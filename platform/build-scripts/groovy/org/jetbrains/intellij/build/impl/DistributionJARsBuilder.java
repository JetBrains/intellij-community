// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.io.Compressor;
import groovy.lang.Closure;
import groovy.lang.Reference;
import groovy.lang.Tuple2;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import kotlin.Pair;
import kotlin.Triple;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.fus.StatisticsRecorderBundledMetadataProvider;
import org.jetbrains.intellij.build.impl.projectStructureMapping.*;
import org.jetbrains.intellij.build.tasks.*;
import org.jetbrains.jps.model.JpsCompositeElement;
import org.jetbrains.jps.model.JpsElementReference;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Assembles output of modules to platform JARs (in {@link BuildPaths#distAllDir}/lib directory),
 * bundled plugins' JARs (in {@link BuildPaths#distAllDir distAll}/plugins directory) and zip archives with
 * non-bundled plugins (in {@link BuildPaths#artifactDir artifacts}/plugins directory).
 */
public final class DistributionJARsBuilder {
  public DistributionJARsBuilder(BuildContext context, Set<PluginLayout> pluginsToPublish) {
    state = new DistributionBuilderState(pluginsToPublish, context);

    String releaseVersion =
      (String)context.getApplicationInfo().getMajorVersion() + context.getApplicationInfo().getMinorVersionMainPart() + "00";
    pluginXmlPatcher = new PluginXmlPatcher(context.getApplicationInfo().getMajorReleaseDate(), releaseVersion);
  }

  public DistributionJARsBuilder(BuildContext context) {
    this(context, (Set<PluginLayout>)Collections.emptySet());
  }

  public DistributionJARsBuilder(DistributionBuilderState state) {
    this.state = state;

    final BuildContext context = state.getContext();
    String releaseVersion =
      (String)context.getApplicationInfo().getMajorVersion() + context.getApplicationInfo().getMinorVersionMainPart() + "00";
    pluginXmlPatcher = new PluginXmlPatcher(context.getApplicationInfo().getMajorReleaseDate(), releaseVersion);
  }

  public static void collectProjectLibrariesWhichShouldBeProvidedByPlatform(BaseLayout plugin,
                                                                            MultiMap<JpsLibrary, JpsModule> result,
                                                                            BuildContext context) {
    Collection<String> libsToUnpack = plugin.getProjectLibrariesToUnpack().values();
    for (String moduleName : plugin.getIncludedModuleNames()) {
      JpsModule module = context.findRequiredModule(moduleName);
      JpsJavaDependenciesEnumerator dependencies = JpsJavaExtensionService.dependencies(module);
      for (JpsLibrary library : dependencies.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).getLibraries()) {
        if (DistributionBuilderStateKt.isProjectLibraryUsedByPlugin(library, plugin, libsToUnpack)) {
          result.putValue(library, module);
        }
      }
    }
  }

  @NotNull
  public ProjectStructureMapping buildJARs(BuildContext context, boolean isUpdateFromSources) {
    validateModuleStructure(context);

    ForkJoinTask<?> svgPrebuildTask = SVGPreBuilder.INSTANCE.createPrebuildSvgIconsTask(context).fork();
    ForkJoinTask<?> brokenPluginsTask = createBuildBrokenPluginListTask(context).fork();

    BuildHelper.createSkippableTask(TracerManager.spanBuilder("build searchable options index"), BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
                                    context, new Closure<Path>(this, this) {
        public Path doCall(Object it) {
          return buildSearchableOptions(context);
        }

        public Path doCall() {
          return doCall(null);
        }
      }).fork().join();

    Set<PluginLayout> pluginLayouts =
      DistributionBuilderStateKt.getPluginsByModules(context.getProductProperties().getProductLayout().getBundledPluginModules(), context);

    Path antDir = context.getProductProperties().isAntRequired() ? context.getPaths().getDistAllDir().resolve("lib/ant") : null;
    Path antTargetFile = antDir == null ? null : antDir.resolve("lib/ant.jar");

    ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher();
    ForkJoinTask<List<DistributionFileEntry>> buildPlatformTask =
      TraceKt.createTask(TracerManager.spanBuilder("build platform lib"), new Supplier<List<DistributionFileEntry>>() {
        @Override
        public List<DistributionFileEntry> get() {
          List<ForkJoinTask<?>> tasks = new ArrayList<ForkJoinTask<?>>();
          ForkJoinTask<?> task = StatisticsRecorderBundledMetadataProvider.createTask(moduleOutputPatcher, context);
          if (task != null) {
            tasks.add(task);
          }


          ForkJoinTask.invokeAll(DefaultGroovyMethods.findAll(
            Arrays.asList(StatisticsRecorderBundledMetadataProvider.createTask(moduleOutputPatcher, context),
                          TraceKt.createTask(TracerManager.spanBuilder("write patched app info"), new Closure<Object>(this, this) {
                            public Object doCall(Object it) {
                              Path moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"));
                              String relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class";
                              Byte[] result = DefaultGroovyMethods.asType(
                                AsmKt.injectAppInfo(moduleOutDir.resolve(relativePath), context.getApplicationInfo().getAppInfoXml()),
                                Byte[].class);
                              moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result);
                              return null;
                            }

                            public void doCall() {
                              doCall(null);
                            }
                          })), new Closure<Boolean>(this, this) {
              public Boolean doCall(ForkJoinTask<? extends Object> it) { return it != null; }

              public Boolean doCall() {
                return doCall(null);
              }
            }));

          List<DistributionFileEntry> result = buildLib(moduleOutputPatcher, state.platform, context);
          if (!isUpdateFromSources && context.getProductProperties().getScrambleMainJar()) {
            scramble(context);
          }


          context.setBootClassPathJarNames(ReorderJarsKt.generateClasspath(context.getPaths().getDistAllDir(),
                                                                           context.getProductProperties().getProductLayout()
                                                                             .getMainJarName(), antTargetFile));
          return result;
        }
      });
    List<DistributionFileEntry> entries = DefaultGroovyMethods.collectMany(ForkJoinTask.invokeAll(DefaultGroovyMethods.findAll(
      Arrays.asList(buildPlatformTask, createBuildBundledPluginTask(pluginLayouts, buildPlatformTask, context),
                    createBuildOsSpecificBundledPluginsTask(pluginLayouts, isUpdateFromSources, buildPlatformTask, context),
                    createBuildNonBundledPluginsTask(state.pluginsToPublish,
                                                     !isUpdateFromSources && context.getOptions().compressNonBundledPluginArchive,
                                                     buildPlatformTask, context),
                    antDir == null ? null : copyAnt(antDir, antTargetFile, context)), new Closure<Boolean>(this, this) {
        public Boolean doCall(ForkJoinTask<List<DistributionFileEntry>> it) { return it != null; }

        public Boolean doCall() {
          return doCall(null);
        }
      })), new Closure<List<DistributionFileEntry>>(this, this) {
      public List<DistributionFileEntry> doCall(ForkJoinTask<List<DistributionFileEntry>> it) {
        List<DistributionFileEntry> result = it.getRawResult();
        return result == null ? Collections.emptyList() : result;
      }

      public List<DistributionFileEntry> doCall() {
        return doCall(null);
      }
    });

    // must be before reorderJars as these additional plugins maybe required for IDE start-up
    List<Path> additionalPluginPaths = context.getProductProperties().getAdditionalPluginPaths(context);
    if (!additionalPluginPaths.isEmpty()) {
      Path pluginDir = context.getPaths().getDistAllDir().resolve("plugins");
      for (Path sourceDir : additionalPluginPaths) {
        BuildHelper.copyDir(sourceDir, pluginDir.resolve(sourceDir.getFileName()));
      }
    }


    List<ForkJoinTask<?>> tasks = new ArrayList<ForkJoinTask<?>>(3);
    tasks.add(TraceKt.createTask(TracerManager.spanBuilder("generate content report"), new Supplier<Void>() {
      @Override
      public Void get() {
        Files.createDirectories(context.getPaths().getArtifactDir());
        ProjectStructureMapping.writeReport(entries, context.getPaths().getArtifactDir().resolve("content-mapping.json"),
                                            context.getPaths());
        return IOGroovyMethods.withCloseable(Files.newOutputStream(context.getPaths().getArtifactDir().resolve("content.json")),
                                             new Closure<Void>(this, this) {
                                               public void doCall(OutputStream it) {
                                                 ProjectStructureMapping.buildJarContentReport(entries, it, context.getPaths());
                                               }

                                               public void doCall() {
                                                 doCall(null);
                                               }
                                             });
      }
    }));

    ProjectStructureMapping projectStructureMapping = new ProjectStructureMapping(entries);
    ForkJoinTask<?> task = buildThirdPartyLibrariesList(projectStructureMapping, context);
    if (task != null) {
      tasks.add(task);
    }

    ForkJoinTask.invokeAll(tasks);

    // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
    brokenPluginsTask.join();
    svgPrebuildTask.join();

    return projectStructureMapping;
  }

  @NotNull
  public ProjectStructureMapping buildJARs(BuildContext context) {
    return buildJARs(context, false);
  }

  private static void scramble(final BuildContext context) {
    JarPackager.pack(Map.of("internalUtilities.jar", List.of("intellij.tools.internalUtilities")),
                     context.getPaths().getBuildOutputDir().resolve("internal"), context);

    ScrambleTool tool = context.getProprietaryBuildTools().getScrambleTool();
    if (tool == null) {
      Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined");
    }
    else {
      tool.scramble(context.getProductProperties().getProductLayout().getMainJarName(), context);
    }


    // e.g. JetBrainsGateway doesn't have a main jar with license code
    if (Files.exists(
      context.getPaths().getDistAllDir().resolve("lib/" + context.getProductProperties().getProductLayout().getMainJarName()))) {
      packInternalUtilities(context);
    }
  }

  @NotNull
  private static ForkJoinTask<List<DistributionFileEntry>> copyAnt(@NotNull final Path antDir,
                                                                   @NotNull final Path antTargetFile,
                                                                   @NotNull final BuildContext context) {
    return TraceKt.createTask(TracerManager.spanBuilder("copy Ant lib").setAttribute("antDir", antDir.toString()),
                              new Supplier<List<DistributionFileEntry>>() {
                                @Override
                                public List<DistributionFileEntry> get() {
                                  final List<Source> sources = new ArrayList<Source>();
                                  final List<DistributionFileEntry> result = new ArrayList<DistributionFileEntry>();
                                  final ProjectLibraryData libraryData = new ProjectLibraryData("Ant", LibraryPackMode.MERGED);
                                  BuildHelper.copyDir(context.getPaths().getCommunityHomeDir().resolve("lib/ant"), antDir,
                                                      new Predicate<Path>() {
                                                        @Override
                                                        public boolean test(Path path) {
                                                          return !path.endsWith("src");
                                                        }
                                                      }, new Predicate<Path>() {
                                      @Override
                                      public boolean test(final Path file) {
                                        if (!file.toString().endsWith(".jar")) {
                                          return true;
                                        }


                                        sources.add(JarBuilder.createZipSource(file, new IntConsumer() {
                                          @Override
                                          public void accept(int size) {
                                            result.add(new ProjectLibraryEntry(antTargetFile, libraryData, file, size));
                                          }
                                        }));
                                        return false;
                                      }
                                    });

                                  sources.sort(null);
                                  // path in class log - empty, do not reorder, doesn't matter
                                  List<Triple<Path, String, List<Source>>> list = List.of(new Triple(antTargetFile, "", sources));
                                  JarBuilder.buildJars(list, false);
                                  return result;
                                }
                              });
  }

  private static void packInternalUtilities(BuildContext context) {
    List<Path> sources = new ArrayList<Path>();
    for (File file : context.getProject().getLibraryCollection().findLibrary("JUnit4").getFiles(JpsOrderRootType.COMPILED)) {
      sources.add(file.toPath());
    }


    sources.add(context.getPaths().getBuildOutputDir().resolve("internal/internalUtilities.jar"));

    ArchiveKt.packInternalUtilities(context.getPaths().getArtifactDir().resolve("internalUtilities.zip"), sources);
  }

  @Nullable
  private static ForkJoinTask<?> createBuildBrokenPluginListTask(@NotNull final BuildContext context) {
    final String buildString = context.getFullBuildNumber();
    final Path targetFile = context.getPaths().getTempDir().resolve("brokenPlugins.db");
    return BuildHelper.createSkippableTask(TracerManager.spanBuilder("build broken plugin list").setAttribute("buildNumber", buildString)
                                             .setAttribute("path", targetFile.toString()), BuildOptions.BROKEN_PLUGINS_LIST_STEP, context,
                                           new Runnable() {
                                             @Override
                                             public void run() {
                                               BrokenPluginsKt.buildBrokenPlugins(targetFile, buildString,
                                                                                  context.getOptions().isInDevelopmentMode);
                                               if (Files.exists(targetFile)) {
                                                 context.addDistFile(Map.entry(targetFile, "bin"));
                                               }
                                             }
                                           });
  }

  /**
   * Validates module structure to be ensure all module dependencies are included
   */
  public void validateModuleStructure(BuildContext context) {
    if (context.getOptions().validateModuleStructure) {
      new ModuleStructureValidator(context, state.platform.getModuleJars()).validate();
    }
  }

  public List<String> getProductModules() {
    List<String> result = new ArrayList<String>();
    for (Map.Entry<String, Collection<String>> moduleJar : state.platform.getJarToIncludedModuleNames()) {
      // Filter out jars with relative paths in name
      if (moduleJar.getKey().contains("\\") || moduleJar.getKey().contains("/")) {
        continue;
      }


      result.addAll(moduleJar.getValue());
    }

    return result;
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  @Nullable
  public Path buildSearchableOptions(BuildContext buildContext,
                                     @Nullable UnaryOperator<Set<String>> classpathCustomizer,
                                     Map<String, Object> systemProperties) {
    Span span = Span.current();
    if (buildContext.getOptions().buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      span.addEvent("skip building searchable options index");
      return null;
    }

    LinkedHashSet<String> ideClasspath = createIdeClassPath(buildContext);
    Path targetDirectory = JarPackager.getSearchableOptionsDir(buildContext);
    BuildMessages messages = buildContext.getMessages();
    NioFiles.deleteRecursively(targetDirectory);
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in Settings dialog and build index for them.
    //noinspection SpellCheckingInspection
    BuildHelper.runApplicationStarter(buildContext, buildContext.getPaths().getTempDir().resolve("searchableOptions"), ideClasspath,
                                      List.of("traverseUI", targetDirectory.toString(), "true"), systemProperties, List.of(),
                                      TimeUnit.MINUTES.toMillis(10L), classpathCustomizer);

    if (!Files.isDirectory(targetDirectory)) {
      messages.error("Failed to build searchable options index: " +
                     String.valueOf(targetDirectory) +
                     " does not exist. See log above for error output from traverseUI run.");
    }


    List<Path> modules = IOGroovyMethods.withCloseable(Files.newDirectoryStream(targetDirectory), new Closure<List<Path>>(this, this) {
      public List<Path> doCall(DirectoryStream<Path> it) { return DefaultGroovyMethods.asList(it); }

      public List<Path> doCall() {
        return doCall(null);
      }
    });
    if (modules.isEmpty()) {
      messages.error("Failed to build searchable options index: " +
                     String.valueOf(targetDirectory) +
                     " is empty. See log above for error output from traverseUI run.");
    }
    else {
      span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), (long)modules.size());
      span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"),
                        DefaultGroovyMethods.collect(modules, new Closure<String>(this, this) {
                          public String doCall(Path it) { return targetDirectory.relativize(it).toString(); }

                          public String doCall() {
                            return doCall(null);
                          }
                        }));
    }

    return targetDirectory;
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  @Nullable
  public Path buildSearchableOptions(BuildContext buildContext, @Nullable UnaryOperator<Set<String>> classpathCustomizer) {
    return buildSearchableOptions(buildContext, classpathCustomizer, Collections.emptyMap());
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  @Nullable
  public Path buildSearchableOptions(BuildContext buildContext) {
    return buildSearchableOptions(buildContext, null, Collections.emptyMap());
  }

  public static Set<String> getModulesToCompile(BuildContext buildContext) {
    ProductModulesLayout productLayout = buildContext.getProductProperties().getProductLayout();
    Set<String> modulesToInclude = new LinkedHashSet<String>();
    modulesToInclude.addAll(productLayout.getIncludedPluginModules(Set.copyOf(productLayout.getBundledPluginModules())));
    PlatformModules.INSTANCE.collectPlatformModules(modulesToInclude);
    modulesToInclude.addAll(productLayout.getProductApiModules());
    modulesToInclude.addAll(productLayout.getProductImplementationModules());
    modulesToInclude.addAll(productLayout.getAdditionalPlatformJars().values());
    modulesToInclude.addAll(DistributionBuilderStateKt.getToolModules());
    modulesToInclude.addAll(buildContext.getProductProperties().getAdditionalModulesToCompile());
    modulesToInclude.add("intellij.idea.community.build.tasks");
    modulesToInclude.add("intellij.platform.images.build");
    modulesToInclude.removeAll(productLayout.getExcludedModuleNames());
    return modulesToInclude;
  }

  public LinkedHashSet<String> createIdeClassPath(@NotNull BuildContext context) {
    // for some reasons maybe duplicated paths - use set
    LinkedHashSet<String> classPath = new LinkedHashSet<String>();
    Files.createDirectories(context.getPaths().getTempDir());
    Path pluginLayoutRoot = Files.createTempDirectory(context.getPaths().getTempDir(), "pluginLayoutRoot");
    List<DistributionFileEntry> nonPluginsEntries = new ArrayList<DistributionFileEntry>();
    List<DistributionFileEntry> pluginsEntries = new ArrayList<DistributionFileEntry>();
    for (DistributionFileEntry e : (generateProjectStructureMapping(context, pluginLayoutRoot))) {
      if (e.getPath().startsWith(pluginLayoutRoot)) {
        Path relPath = pluginLayoutRoot.relativize(e.getPath());
        // For plugins our classloader load jars only from lib folder
        final Path parent = relPath.getParent();
        if ((parent == null ? null : parent.getParent()) == null && relPath.getParent().toString().equals("lib")) {
          pluginsEntries.add(e);
        }
      }
      else {
        nonPluginsEntries.add(e);
      }
    }


    for (DistributionFileEntry entry : DefaultGroovyMethods.plus(nonPluginsEntries, pluginsEntries)) {
      if (entry instanceof ModuleOutputEntry) {
        classPath.add(context.getModuleOutputDir(context.findRequiredModule(((ModuleOutputEntry)entry).getModuleName())).toString());
      }
      else if (entry instanceof LibraryFileEntry) {
        classPath.add(((LibraryFileEntry)entry).getLibraryFile().toString());
      }
      else {
        throw new UnsupportedOperationException("Entry " + String.valueOf(entry) + " is not supported");
      }
    }

    return classPath;
  }

  public List<DistributionFileEntry> generateProjectStructureMapping(@NotNull final BuildContext context,
                                                                     @NotNull final Path pluginLayoutRoot) {
    final ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher();
    ForkJoinTask<List<DistributionFileEntry>> libDirLayout =
      processLibDirectoryLayout(moduleOutputPatcher, state.platform, context, false).fork();
    Set<PluginLayout> allPlugins =
      DistributionBuilderStateKt.getPluginsByModules(context.getProductProperties().getProductLayout().getBundledPluginModules(), context);
    final List<DistributionFileEntry> entries = new ArrayList<DistributionFileEntry>();
    allPlugins.stream().filter(new Predicate<PluginLayout>() {
      @Override
      public boolean test(PluginLayout plugin) {
        return satisfiesBundlingRequirements(plugin, null, null, context);
      }
    }).forEach(new Consumer<PluginLayout>() {
      @Override
      public void accept(PluginLayout plugin) {
        entries.addAll(layout(plugin, pluginLayoutRoot, false, moduleOutputPatcher, plugin.getModuleJars(), context));
      }
    });
    entries.addAll(libDirLayout.join());
    return entries;
  }

  public void generateProjectStructureMapping(@NotNull Path targetFile, @NotNull BuildContext context, @NotNull Path pluginLayoutRoot) {
    ProjectStructureMapping.writeReport(generateProjectStructureMapping(context, pluginLayoutRoot), targetFile, context.getPaths());
  }

  @Nullable
  private static ForkJoinTask<?> buildThirdPartyLibrariesList(@NotNull final ProjectStructureMapping projectStructureMapping,
                                                              @NotNull final BuildContext context) {
    return BuildHelper.createSkippableTask(TracerManager.spanBuilder("generate table of licenses for used third-party libraries"),
                                           BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP, context, new Runnable() {
        @Override
        public void run() {
          LibraryLicensesListGenerator generator =
            LibraryLicensesListGenerator.create(context.getProject(), context.getProductProperties().getAllLibraryLicenses(),
                                                projectStructureMapping.getIncludedModules());
          generator.generateHtml(BuildTasksImplKt.getThirdPartyLibrariesHtmlFilePath(context));
          generator.generateJson(BuildTasksImplKt.getThirdPartyLibrariesJsonFilePath(context));
        }
      });
  }

  @NotNull
  public static List<DistributionFileEntry> buildLib(ModuleOutputPatcher moduleOutputPatcher,
                                                     PlatformLayout platform,
                                                     BuildContext context) {
    patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher, context);

    List<DistributionFileEntry> libDirMappings = processLibDirectoryLayout(moduleOutputPatcher, platform, context, true).fork().join();

    if (context.getProprietaryBuildTools().getScrambleTool() != null) {
      Path libDir = context.getPaths().getDistAllDir().resolve("lib");
      for (String forbiddenJarName : context.getProprietaryBuildTools().getScrambleTool().getNamesOfJarsRequiredToBeScrambled()) {
        if (Files.exists(libDir.resolve(forbiddenJarName))) {
          context.getMessages().error("The following JAR cannot be included into the product 'lib' directory," +
                                      " it need to be scrambled with the main jar: " +
                                      forbiddenJarName);
        }
      }


      List<String> modulesToBeScrambled = context.getProprietaryBuildTools().getScrambleTool().getNamesOfModulesRequiredToBeScrambled();
      ProductModulesLayout productLayout = context.getProductProperties().getProductLayout();
      for (String jarName : platform.getModuleJars().keySet()) {
        if (!jarName.equals(productLayout.getMainJarName()) && !jarName.equals(PlatformModules.PRODUCT_JAR)) {
          final Collection<String> notScrambled =
            DefaultGroovyMethods.intersect(platform.getModuleJars().get(jarName), modulesToBeScrambled);
          if (!notScrambled.isEmpty()) {
            context.getMessages().error(
              "Module \'" + DefaultGroovyMethods.first(notScrambled) + "\' is included into " + jarName + " which is not scrambled.");
          }
        }
      }
    }

    return libDirMappings;
  }

  public static ForkJoinTask<List<DistributionFileEntry>> processLibDirectoryLayout(final ModuleOutputPatcher moduleOutputPatcher,
                                                                                    final PlatformLayout platform,
                                                                                    final BuildContext context,
                                                                                    final boolean copyFiles) {
    return TraceKt.createTask(TracerManager.spanBuilder("layout").setAttribute("path", context.getPaths().getBuildOutputDir()
      .relativize(context.getPaths().getDistAllDir()).toString()), new Supplier<List<DistributionFileEntry>>() {
      @Override
      public List<DistributionFileEntry> get() {
        return layout(platform, context.getPaths().getDistAllDir(), copyFiles, moduleOutputPatcher, platform.getModuleJars(), context);
      }
    });
  }

  public ForkJoinTask<List<DistributionFileEntry>> createBuildBundledPluginTask(@NotNull final Collection<PluginLayout> plugins,
                                                                                final ForkJoinTask<?> buildPlatformTask,
                                                                                @NotNull final BuildContext context) {
    final Set<String> pluginDirectoriesToSkip = context.getOptions().bundledPluginDirectoriesToSkip;
    return TraceKt.createTask(TracerManager.spanBuilder("build bundled plugins")
                                .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), List.copyOf(pluginDirectoriesToSkip))
                                .setAttribute("count", plugins.size()), new Supplier<List<DistributionFileEntry>>() {
      @Override
      public List<DistributionFileEntry> get() {
        List<PluginLayout> pluginsToBundle = new ArrayList<PluginLayout>(plugins.size());
        for (PluginLayout plugin : plugins) {
          if (satisfiesBundlingRequirements(plugin, null, null, context) && !pluginDirectoriesToSkip.contains(plugin.getDirectoryName())) {
            pluginsToBundle.add(plugin);
          }
        }


        // Doesn't make sense to require passing here a list with a stable order - unnecessary complication. Just sort by main module.
        pluginsToBundle.sort(PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE);

        Span.current().setAttribute("satisfiableCount", pluginsToBundle.size());
        return buildPlugins(new ModuleOutputPatcher(), pluginsToBundle, context.getPaths().getDistAllDir().resolve(PLUGINS_DIRECTORY),
                            context, buildPlatformTask, null);
      }
    });
  }

  private static boolean satisfiesBundlingRequirements(PluginLayout plugin,
                                                       @Nullable OsFamily osFamily,
                                                       @Nullable JvmArchitecture arch,
                                                       @NotNull BuildContext context) {
    PluginBundlingRestrictions bundlingRestrictions = plugin.getBundlingRestrictions();

    if (bundlingRestrictions.getIncludeInEapOnly() && !context.getApplicationInfo().isEAP()) {
      return false;
    }


    if (osFamily == null && !DefaultGroovyMethods.equals(bundlingRestrictions.getSupportedOs(), OsFamily.ALL)) {
      return false;
    }


    if (osFamily != null &&
        (DefaultGroovyMethods.equals(bundlingRestrictions.getSupportedOs(), OsFamily.ALL) ||
         !bundlingRestrictions.getSupportedOs().contains(osFamily))) {
      return false;
    }


    if (arch == null && !DefaultGroovyMethods.equals(bundlingRestrictions.getSupportedArch(), JvmArchitecture.ALL)) {
      return false;
    }


    if (arch != null && !bundlingRestrictions.getSupportedArch().contains(arch)) {
      return false;
    }


    return true;
  }

  private ForkJoinTask<List<DistributionFileEntry>> createBuildOsSpecificBundledPluginsTask(@NotNull final Set<PluginLayout> pluginLayouts,
                                                                                            final boolean isUpdateFromSources,
                                                                                            @Nullable final ForkJoinTask<?> buildPlatformTask,
                                                                                            @NotNull final BuildContext context) {
    return TraceKt.createTask(
      TracerManager.spanBuilder("build os-specific bundled plugins").setAttribute("isUpdateFromSources", isUpdateFromSources),
      new Supplier<List<DistributionFileEntry>>() {
        @Override
        public List<DistributionFileEntry> get() {
          List<Tuple2> platforms = isUpdateFromSources
                                   ? List.of(new Tuple2(OsFamily.currentOs, JvmArchitecture.currentJvmArch))
                                   : List.of(new Tuple2(OsFamily.MACOS, JvmArchitecture.x64),
                                             new Tuple2(OsFamily.MACOS, JvmArchitecture.aarch64),
                                             new Tuple2(OsFamily.WINDOWS, JvmArchitecture.x64),
                                             new Tuple2(OsFamily.LINUX, JvmArchitecture.x64));
          return DefaultGroovyMethods.collectMany(ForkJoinTask.invokeAll(
            DefaultGroovyMethods.findResults(platforms, new Closure<ForkJoinTask<List<DistributionFileEntry>>>(this, this) {
              public ForkJoinTask<List<DistributionFileEntry>> doCall(OsFamily osFamily, JvmArchitecture arch) {
                if (!context.shouldBuildDistributionForOS(osFamily.osId)) {
                  return null;
                }


                final List<PluginLayout> osSpecificPlugins = new ArrayList<PluginLayout>();
                for (PluginLayout pluginLayout : pluginLayouts) {
                  if (satisfiesBundlingRequirements(pluginLayout, osFamily, arch, context)) {
                    osSpecificPlugins.add(pluginLayout);
                  }
                }

                if (osSpecificPlugins.isEmpty()) {
                  return null;
                }


                final Path outDir = isUpdateFromSources
                                    ? context.getPaths().getDistAllDir().resolve("plugins")
                                    : getOsAndArchSpecificDistDirectory(osFamily, arch, context).resolve("plugins");

                return TraceKt.createTask(
                  TracerManager.spanBuilder("build bundled plugins").setAttribute("os", osFamily.osName).setAttribute("arch", arch.name())
                    .setAttribute("count", osSpecificPlugins.size()).setAttribute("outDir", outDir.toString()),
                  new Supplier<List<DistributionFileEntry>>() {
                    @Override
                    public List<DistributionFileEntry> get() {
                      return buildPlugins(new ModuleOutputPatcher(), osSpecificPlugins, outDir, context, buildPlatformTask, null);
                    }
                  });
              }
            })), new Closure<List<DistributionFileEntry>>(this, this) {
            public List<DistributionFileEntry> doCall(ForkJoinTask<List<DistributionFileEntry>> it) { return it.getRawResult(); }

            public List<DistributionFileEntry> doCall() {
              return doCall(null);
            }
          });
        }
      });
  }

  public static Path getOsAndArchSpecificDistDirectory(final OsFamily osFamily, final JvmArchitecture arch, BuildContext buildContext) {
    return buildContext.getPaths().getBuildOutputDir().resolve("dist." + osFamily.distSuffix + "." + arch.name());
  }

  /**
   * @return predicate to test if a given plugin should be auto-published
   */
  @NotNull
  private static Predicate<PluginLayout> loadPluginAutoPublishList(@NotNull BuildContext buildContext) {
    //noinspection SpellCheckingInspection
    final String productCode = buildContext.getApplicationInfo().getProductCode();
    final Collection<String> config =
      IOGroovyMethods.withCloseable(Files.lines(buildContext.getPaths().getCommunityHomeDir().resolve("../build/plugins-autoupload.txt")),
                                    new Closure<Collection<String>>(null, null) {
                                      public Collection<String> doCall(Stream<String> lines) {
                                        return lines.map(DefaultGroovyMethods.asType(new Closure<String>(null, null) {
                                          public String doCall(String line) { return StringUtil.split(line, "//", true, false).get(0); }
                                        }, (Class<T>)Function.class)).map(DefaultGroovyMethods.asType(new Closure<String>(null, null) {
                                          public String doCall(String line) { return StringUtil.split(line, "#", true, false).get(0); }
                                        }, (Class<T>)Function.class)).map(DefaultGroovyMethods.asType(new Closure<String>(null, null) {
                                          public String doCall(String line) { return line.trim(); }
                                        }, (Class<T>)Function.class)).filter(DefaultGroovyMethods.asType(new Closure<Object>(null, null) {
                                          public Object doCall(String line) { return !line.isEmpty(); }
                                        }, (Class<T>)Predicate.class)).map(DefaultGroovyMethods.asType(new Closure<String>(null, null) {
                                          public String doCall(String line) { return line.toString();/*make sure there is no GString involved */ }
                                        }, (Class<T>)Function.class)).collect(
                                          (Collector<? super String, ?, Collection<String>>)Collectors.toCollection(
                                            DefaultGroovyMethods.asType(new Closure<TreeSet<String>>(null, null) {
                                              public TreeSet<String> doCall(Object it) {
                                                return new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
                                              }

                                              public TreeSet<String> doCall() {
                                                return doCall(null);
                                              }
                                            }, (Class<T>)Supplier.class)));
                                      }
                                    });

    return new Predicate<PluginLayout>() {
      @Override
      public boolean test(PluginLayout plugin) {
        if (plugin == null) {
          return false;
        }


        //see the specification in the plugins-autoupload.txt. Supported rules:
        //   <plugin main module name> ## include the plugin
        //   +<product code>:<plugin main module name> ## include the plugin
        //   -<product code>:<plugin main module name> ## exclude the plugin

        final String module = plugin.getMainModule();
        String excludeRule = (String)"-" + productCode + ":" + module;
        String includeRule = (String)"+" + productCode + ":" + module;
        if (config.contains(excludeRule)) {
          //the exclude rule is the most powerful
          return false;
        }


        return config.contains(module) || config.contains(includeRule.toString());
      }
    };
  }

  @Nullable
  public ForkJoinTask<List<DistributionFileEntry>> createBuildNonBundledPluginsTask(@NotNull final Set<PluginLayout> pluginsToPublish,
                                                                                    final boolean compressPluginArchive,
                                                                                    @Nullable final ForkJoinTask<?> buildPlatformLibTask,
                                                                                    @NotNull final BuildContext context) {
    if (pluginsToPublish.isEmpty()) {
      return null;
    }


    return TraceKt.createTask(TracerManager.spanBuilder("build non-bundled plugins").setAttribute("count", pluginsToPublish.size()),
                              new Supplier<List<DistributionFileEntry>>() {
                                @Override
                                public List<DistributionFileEntry> get() {
                                  if (context.getOptions().buildStepsToSkip.contains(BuildOptions.NON_BUNDLED_PLUGINS_STEP)) {
                                    Span.current().addEvent("skip");
                                    return Collections.emptyList();
                                  }


                                  final Path nonBundledPluginsArtifacts =
                                    context.getPaths().getArtifactDir().resolve(context.getApplicationInfo().getProductCode() + "-plugins");
                                  final Path autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading");

                                  ForkJoinTask<List<Pair<Path, Byte[]>>> buildKeymapPluginsTask =
                                    buildKeymapPlugins(autoUploadingDir, context).fork();

                                  final ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher();
                                  Path stageDir = context.getPaths().getTempDir()
                                    .resolve("non-bundled-plugins-" + context.getApplicationInfo().getProductCode());

                                  final List<Map.Entry<String, Path>> dirToJar =
                                    Collections.synchronizedList(new ArrayList<Map.Entry<String, Path>>());

                                  final String defaultPluginVersion = context.getBuildNumber().endsWith(".SNAPSHOT")
                                                                      ? context.getBuildNumber() +
                                                                        "." +
                                                                        PluginXmlPatcher.getPluginDateFormat().format(ZonedDateTime.now())
                                                                      : context.getBuildNumber();

                                  final List<PluginRepositorySpec> pluginsToIncludeInCustomRepository =
                                    Collections.synchronizedList(new ArrayList<PluginRepositorySpec>());
                                  final Predicate<PluginLayout> autoPublishPluginChecker = loadPluginAutoPublishList(context);

                                  final boolean prepareCustomPluginRepositoryForPublishedPlugins =
                                    context.getProductProperties().getProductLayout().getPrepareCustomPluginRepositoryForPublishedPlugins();
                                  List<DistributionFileEntry> mappings = buildPlugins(moduleOutputPatcher,
                                                                                      DefaultGroovyMethods.sort(pluginsToPublish, false,
                                                                                                                PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE),
                                                                                      stageDir, context, buildPlatformLibTask,
                                                                                      new BiConsumer<PluginLayout, Path>() {
                                                                                        @Override
                                                                                        public void accept(PluginLayout plugin,
                                                                                                           Path pluginDir) {
                                                                                          Path targetDirectory =
                                                                                            autoPublishPluginChecker.test(plugin)
                                                                                            ? autoUploadingDir
                                                                                            : nonBundledPluginsArtifacts;
                                                                                          String pluginDirName =
                                                                                            pluginDir.getFileName().toString();

                                                                                          Path moduleOutput = context.getModuleOutputDir(
                                                                                            context.findRequiredModule(
                                                                                              plugin.getMainModule()));
                                                                                          Path pluginXmlPath =
                                                                                            moduleOutput.resolve("META-INF/plugin.xml");

                                                                                          final String pluginVersion =
                                                                                            Files.exists(pluginXmlPath)
                                                                                            ? plugin.getVersionEvaluator()
                                                                                              .evaluate(pluginXmlPath, defaultPluginVersion,
                                                                                                        context)
                                                                                            : defaultPluginVersion;

                                                                                          Path destFile = targetDirectory.resolve(
                                                                                            pluginDirName + "-" + pluginVersion + ".zip");
                                                                                          if (prepareCustomPluginRepositoryForPublishedPlugins) {
                                                                                            Byte[] pluginXml =
                                                                                              moduleOutputPatcher.getPatchedPluginXml(
                                                                                                plugin.getMainModule());
                                                                                            pluginsToIncludeInCustomRepository.add(
                                                                                              new PluginRepositorySpec(destFile,
                                                                                                                       pluginXml));
                                                                                          }

                                                                                          dirToJar.add(Map.entry(pluginDirName, destFile));
                                                                                        }
                                                                                      });
                                  BlockmapKt.bulkZipWithPrefix(stageDir, dirToJar, compressPluginArchive);

                                  PluginLayout helpPlugin = BuiltInHelpPlugin.helpPlugin(context, defaultPluginVersion);
                                  if (helpPlugin != null) {
                                    PluginRepositorySpec spec =
                                      buildHelpPlugin(helpPlugin, stageDir, autoUploadingDir, moduleOutputPatcher, context);
                                    if (prepareCustomPluginRepositoryForPublishedPlugins) {
                                      pluginsToIncludeInCustomRepository.add(spec);
                                    }
                                  }


                                  if (prepareCustomPluginRepositoryForPublishedPlugins) {
                                    PluginRepositoryXmlGenerator.generate(pluginsToIncludeInCustomRepository, nonBundledPluginsArtifacts,
                                                                          context);

                                    List<PluginRepositorySpec> autoUploadingPlugins =
                                      DefaultGroovyMethods.findAll(pluginsToIncludeInCustomRepository, new Closure<Boolean>(this, this) {
                                        public Boolean doCall(PluginRepositorySpec it) {
                                          return it.getPluginZip().startsWith(autoUploadingDir);
                                        }

                                        public Boolean doCall() {
                                          return doCall(null);
                                        }
                                      });
                                    PluginRepositoryXmlGenerator.generate(autoUploadingPlugins, autoUploadingDir, context);
                                  }


                                  for (Pair<Path, Byte[]> item : buildKeymapPluginsTask.join()) {
                                    if (prepareCustomPluginRepositoryForPublishedPlugins) {
                                      pluginsToIncludeInCustomRepository.add(new PluginRepositorySpec(item.getFirst(), item.getSecond()));
                                    }
                                  }

                                  return mappings;
                                }
                              });
  }

  private static ForkJoinTask<List<Pair<Path, Byte[]>>> buildKeymapPlugins(Path targetDir, BuildContext context) {
    Path keymapDir = context.getPaths().getCommunityHomeDir().resolve("platform/platform-resources/src/keymaps");
    return KeymapPluginKt.buildKeymapPlugins(context.getBuildNumber(), targetDir, keymapDir);
  }

  private PluginRepositorySpec buildHelpPlugin(final PluginLayout helpPlugin,
                                               final Path pluginsToPublishDir,
                                               Path targetDir,
                                               final ModuleOutputPatcher moduleOutputPatcher,
                                               final BuildContext context) {
    final String directory = getActualPluginDirectoryName(helpPlugin, context);
    final Path destFile = targetDir.resolve(directory + ".zip");

    context.getMessages().block(TracerManager.spanBuilder("build help plugin").setAttribute("dir", directory), new Supplier<Void>() {
      @Override
      public Void get() {
        buildPlugins(moduleOutputPatcher, List.of(helpPlugin), pluginsToPublishDir, context, null, null);
        BuildHelper.zipWithPrefix(context, destFile, List.of(pluginsToPublishDir.resolve(directory)), directory, true);
        return null;
      }
    });
    return new PluginRepositorySpec(destFile, moduleOutputPatcher.getPatchedPluginXml(helpPlugin.getMainModule()));
  }

  /**
   * Returns name of directory in the product distribution where plugin will be placed. For plugins which use the main module name as the
   * directory name return the old module name to temporary keep layout of plugins unchanged.
   */
  public static String getActualPluginDirectoryName(PluginLayout plugin, BuildContext context) {
    if (!plugin.getDirectoryNameSetExplicitly() &&
        plugin.getDirectoryName().equals(BaseLayout.convertModuleNameToFileName(plugin.getMainModule())) &&
        context.getOldModuleName(plugin.getMainModule()) != null) {
      return context.getOldModuleName(plugin.getMainModule());
    }
    else {
      return plugin.getDirectoryName();
    }
  }

  @NotNull
  private List<DistributionFileEntry> buildPlugins(final ModuleOutputPatcher moduleOutputPatcher,
                                                   Collection<PluginLayout> plugins,
                                                   final Path targetDirectory,
                                                   final BuildContext context,
                                                   @Nullable final ForkJoinTask<?> buildPlatformTask,
                                                   @Nullable final BiConsumer<PluginLayout, Path> pluginBuilt) {
    final ScrambleTool scrambleTool = context.getProprietaryBuildTools().getScrambleTool();
    final boolean isScramblingSkipped = context.getOptions().buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP);

    final List<ForkJoinTask<?>> scrambleTasks = new ArrayList<ForkJoinTask<?>>();
    final List<ForkJoinTask<List<DistributionFileEntry>>> tasks = new ArrayList<ForkJoinTask<List<DistributionFileEntry>>>();

    // must be as a closure, dont' use "for in" here - to capture supplier variables.
    DefaultGroovyMethods.each(plugins, new Closure<Boolean>(this, this) {
      public Boolean doCall(final PluginLayout plugin) {
        boolean isHelpPlugin = "intellij.platform.builtInHelp".equals(plugin.getMainModule());
        if (!isHelpPlugin) {
          checkOutputOfPluginModules(plugin.getMainModule(), plugin.getModuleJars(), plugin.getModuleExcludes(), context);
          PluginXmlPatcher.patchPluginXml(moduleOutputPatcher, plugin, state.pluginsToPublish, pluginXmlPatcher, context);
        }


        final String directoryName = getActualPluginDirectoryName(plugin, context);
        final Path pluginDir = targetDirectory.resolve(directoryName);

        return tasks.add(TraceKt.createTask(
          TracerManager.spanBuilder("plugin").setAttribute("path", context.getPaths().getBuildOutputDir().relativize(pluginDir).toString()),
          new Supplier<List<DistributionFileEntry>>() {
            @Override
            public List<DistributionFileEntry> get() throws Exception {
              List<DistributionFileEntry> result = layout(plugin, pluginDir, true, moduleOutputPatcher, plugin.getModuleJars(), context);
              if (!plugin.getPathsToScramble().isEmpty()) {
                Attributes attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName);
                if (scrambleTool == null) {
                  Span.current()
                    .addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled",
                              attributes);
                }
                else if (isScramblingSkipped) {
                  Span.current().addEvent("skip scrambling plugin because step is disabled", attributes);
                }
                else {
                  ForkJoinTask<?> scrambleTask = scrambleTool.scramblePlugin(context, plugin, pluginDir, targetDirectory);
                  if (scrambleTask != null) {
                    // we can not start executing right now because the plugin can use other plugins in a scramble classpath
                    scrambleTasks.add(scrambleTask);
                  }
                }
              }


              if (pluginBuilt != null) {
                pluginBuilt.accept(plugin, pluginDir);
              }

              return result;
            }
          }));
      }
    });

    List<DistributionFileEntry> entries = new ArrayList<DistributionFileEntry>(tasks.size() * 2);
    for (ForkJoinTask<List<DistributionFileEntry>> task : ForkJoinTask.invokeAll(tasks)) {
      entries.addAll(task.getRawResult());
    }


    if (!scrambleTasks.isEmpty()) {
      // scrambling can require classes from platform
      if (buildPlatformTask != null) {
        BuildHelper.span(TracerManager.spanBuilder("wait for platform lib for scrambling"), new Runnable() {
          @Override
          public void run() {
            buildPlatformTask.join();
          }
        });
      }

      BuildHelper.invokeAllSettled(scrambleTasks);
    }

    return entries;
  }

  public static void checkOutputOfPluginModules(@NotNull String mainPluginModule,
                                                MultiMap<String, String> moduleJars,
                                                MultiMap<String, String> moduleExcludes,
                                                @NotNull BuildContext buildContext) {
    // don't check modules which are not direct children of lib/ directory
    final List<String> modulesWithPluginXml = new ArrayList<String>();
    for (Map.Entry<String, Collection<String>> entry : moduleJars.entrySet()) {
      if (!entry.getKey().contains("/")) {
        for (String moduleName : entry.getValue()) {
          if (containsFileInOutput(moduleName, "META-INF/plugin.xml", moduleExcludes.get(moduleName), buildContext)) {
            modulesWithPluginXml.add(moduleName);
          }
        }
      }
    }

    if (modulesWithPluginXml.size() > 1) {
      buildContext.getMessages().error("Multiple modules (" +
                                       DefaultGroovyMethods.join(modulesWithPluginXml, ", ") +
                                       ") from \'" +
                                       mainPluginModule +
                                       "\' plugin contain plugin.xml files so the plugin won\'t work properly");
    }

    if (modulesWithPluginXml.isEmpty()) {
      buildContext.getMessages().error("No module from \'" + mainPluginModule + "\' plugin contains plugin.xml");
    }


    for (String moduleJar : moduleJars.values()) {
      if (!moduleJar.equals("intellij.java.guiForms.rt") &&
          containsFileInOutput(moduleJar, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(moduleJar),
                               buildContext)) {
        buildContext.getMessages().error("Runtime classes of GUI designer must not be packaged to \'" +
                                         moduleJar +
                                         "\' module in \'" +
                                         mainPluginModule +
                                         "\' plugin, because they are included into a platform JAR. ".plus(
                                           "Make sure that 'Automatically copy form runtime classes to the output directory' is disabled in Settings | Editor | GUI Designer."));
      }
    }
  }

  private static boolean containsFileInOutput(@NotNull String moduleName,
                                              String filePath,
                                              Collection<String> excludes,
                                              BuildContext buildContext) {
    Path moduleOutput = buildContext.getModuleOutputDir(buildContext.findRequiredModule(moduleName));
    Path fileInOutput = moduleOutput.resolve(filePath);

    if (!Files.exists(fileInOutput)) {
      return false;
    }


    final FileSet set = new FileSet(moduleOutput).include(filePath);
    DefaultGroovyMethods.each(excludes, new Closure<FileSet>(null, null) {
      public FileSet doCall(String it) { return set.exclude(it); }

      public FileSet doCall() {
        return doCall(null);
      }
    });
    return !set.isEmpty();
  }

  /**
   * Returns path to a JAR file in the product distribution where platform/plugin classes will be placed. If the JAR name corresponds to
   * a module name and the module was renamed, return the old name to temporary keep the product layout unchanged.
   */
  public static String getActualModuleJarPath(String relativeJarPath,
                                              Collection<String> moduleNames,
                                              Set<String> explicitlySetJarPaths,
                                              @NotNull final BuildContext buildContext) {
    if (explicitlySetJarPaths.contains(relativeJarPath)) {
      return relativeJarPath;
    }


    for (String moduleName : moduleNames) {
      if (relativeJarPath.equals(BaseLayout.convertModuleNameToFileName(moduleName) + ".jar") &&
          buildContext.getOldModuleName(moduleName) != null) {
        return buildContext.getOldModuleName(moduleName) + ".jar";
      }
    }

    return relativeJarPath;
  }

  /**
   * @param moduleJars          mapping from JAR path relative to 'lib' directory to names of modules
   * @param additionalResources pairs of resources files and corresponding relative output paths
   */
  public static List<DistributionFileEntry> layout(final BaseLayout layout,
                                                   final Path targetDirectory,
                                                   final boolean copyFiles,
                                                   final ModuleOutputPatcher moduleOutputPatcher,
                                                   final MultiMap<String, String> moduleJars,
                                                   final BuildContext context) {
    if (copyFiles) {
      checkModuleExcludes(layout.getModuleExcludes(), context);
    }


    Collection<ForkJoinTask<Collection<DistributionFileEntry>>> tasks = new ArrayList<ForkJoinTask<Collection<DistributionFileEntry>>>(3);

    // patchers must be executed _before_ pack because patcher patches module output
    if (copyFiles && layout instanceof PluginLayout && !((PluginLayout)layout).getPatchers().isEmpty()) {
      final List<BiConsumer<ModuleOutputPatcher, BuildContext>> patchers = ((PluginLayout)layout).getPatchers();
      BuildHelper.span(TracerManager.spanBuilder("execute custom patchers").setAttribute("count", patchers.size()), new Runnable() {
        @Override
        public void run() {
          for (BiConsumer<ModuleOutputPatcher, BuildContext> patcher : patchers) {
            patcher.accept(moduleOutputPatcher, context);
          }
        }
      });
    }


    ((ArrayList<ForkJoinTask<Collection<DistributionFileEntry>>>)tasks).add(
      TraceKt.createTask(TracerManager.spanBuilder("pack"), new Supplier<Collection<DistributionFileEntry>>() {
        @Override
        public Collection<DistributionFileEntry> get() {
          Map<String, List<String>> actualModuleJars = new TreeMap<String, List<String>>();
          for (Map.Entry<String, Collection<String>> entry : moduleJars.entrySet()) {
            Collection<String> modules = entry.getValue();
            String jarPath = getActualModuleJarPath(entry.getKey(), modules, layout.getExplicitlySetJarPaths(), context);
            actualModuleJars.computeIfAbsent(jarPath, new Closure<ArrayList<Object>>(this, this) {
              public ArrayList<Object> doCall(String it) { return new ArrayList<Object>(); }

              public ArrayList<Object> doCall() {
                return doCall(null);
              }
            }).addAll(modules);
          }

          return JarPackager.pack(actualModuleJars, targetDirectory.resolve("lib"), layout, moduleOutputPatcher, !copyFiles, context);
        }
      }));

    if (copyFiles &&
        (!layout.getResourcePaths().isEmpty() ||
         (layout instanceof PluginLayout && !((PluginLayout)layout).getResourceGenerators().isEmpty()))) {
      ((ArrayList<ForkJoinTask<Collection<DistributionFileEntry>>>)tasks).add(
        TraceKt.createTask(TracerManager.spanBuilder("pack additional resources"), new Supplier<Collection<DistributionFileEntry>>() {
          @Override
          public Collection<DistributionFileEntry> get() {
            layoutAdditionalResources(layout, context, targetDirectory);
            return Collections.emptyList();
          }
        }));
    }


    if (!layout.getIncludedArtifacts().isEmpty()) {
      ((ArrayList<ForkJoinTask<Collection<DistributionFileEntry>>>)tasks).add(
        TraceKt.createTask(TracerManager.spanBuilder("pack artifacts"), new Supplier<Collection<DistributionFileEntry>>() {
          @Override
          public Collection<DistributionFileEntry> get() {
            return layoutArtifacts(layout, context, copyFiles, targetDirectory);
          }
        }));
    }

    return DefaultGroovyMethods.collectMany(ForkJoinTask.invokeAll(tasks), new Closure<Collection<DistributionFileEntry>>(null, null) {
      public Collection<DistributionFileEntry> doCall(ForkJoinTask<Collection<DistributionFileEntry>> it) { return it.getRawResult(); }

      public Collection<DistributionFileEntry> doCall() {
        return doCall(null);
      }
    });
  }

  private static void layoutAdditionalResources(BaseLayout layout, final BuildContext context, final Path targetDirectory) {
    for (ModuleResourceData resourceData : layout.getResourcePaths()) {
      final Path source = basePath(context, resourceData.getModuleName()).resolve(resourceData.getResourcePath()).normalize();
      final Reference<Path> target = new Reference<Path>(targetDirectory.resolve(resourceData.getRelativeOutputPath()));
      if (resourceData.getPackToZip()) {
        if (Files.isDirectory(source)) {
          // do not compress - doesn't make sense as it is a part of distribution
          BuildHelper.zip(context, target.get(), source, false);
        }
        else {
          target.set(target.get().resolve(source.getFileName()));
          IOGroovyMethods.withCloseable(new Compressor.Zip(target.get().toFile()), new Closure<Void>(null, null) {
            public void doCall(Compressor.Zip it) {
              it.addFile(target.get().getFileName().toString(), source);
            }

            public void doCall() {
              doCall(null);
            }
          });
        }
      }
      else {
        if (Files.isRegularFile(source)) {
          BuildHelper.copyFileToDir(source, target.get());
        }
        else {
          BuildHelper.copyDir(source, target.get());
        }
      }
    }


    if (!(layout instanceof PluginLayout)) {
      return;
    }


    final List<com.intellij.openapi.util.Pair<BiFunction<Path, BuildContext, Path>, String>> resourceGenerators =
      ((PluginLayout)layout).getResourceGenerators();
    if (!resourceGenerators.isEmpty()) {
      BuildHelper.span(TracerManager.spanBuilder("generate and pack resources"), new Runnable() {
        @Override
        public void run() {
          for (com.intellij.openapi.util.Pair<BiFunction<Path, BuildContext, Path>, String> item : resourceGenerators) {
            Path resourceFile = item.getFirst().apply(targetDirectory, context);
            if (resourceFile == null) {
              continue;
            }


            Path target = item.getSecond().isEmpty() ? targetDirectory : targetDirectory.resolve(item.getSecond());
            if (Files.isRegularFile(resourceFile)) {
              BuildHelper.copyFileToDir(resourceFile, target);
            }
            else {
              BuildHelper.copyDir(resourceFile, target);
            }
          }
        }
      });
    }
  }

  private static Collection<DistributionFileEntry> layoutArtifacts(BaseLayout layout,
                                                                   BuildContext context,
                                                                   boolean copyFiles,
                                                                   Path targetDirectory) {
    Span span = Span.current();
    Collection<DistributionFileEntry> entries = new ArrayList<DistributionFileEntry>();
    for (Map.Entry<String, String> entry : layout.getIncludedArtifacts().entrySet()) {
      final String artifactName = entry.getKey();
      String relativePath = entry.getValue();

      span.addEvent("include artifact", Attributes.of(AttributeKey.stringKey("artifactName"), artifactName));

      JpsArtifact artifact =
        DefaultGroovyMethods.find(JpsArtifactService.getInstance().getArtifacts(context.getProject()), new Closure<Boolean>(null, null) {
          public Boolean doCall(JpsArtifact it) { return it.getName().equals(artifactName); }

          public Boolean doCall() {
            return doCall(null);
          }
        });
      if (artifact == null) {
        throw new IllegalArgumentException("Cannot find artifact " + artifactName + " in the project");
      }


      Path artifactFile;
      if (artifact.getOutputFilePath().equals(artifact.getOutputPath())) {
        Path source = Path.of(artifact.getOutputPath());
        artifactFile = targetDirectory.resolve("lib").resolve(relativePath);
        if (copyFiles) {
          BuildHelper.copyDir(source, targetDirectory.resolve("lib").resolve(relativePath));
        }
      }
      else {
        Path source = Path.of(artifact.getOutputFilePath());
        artifactFile = targetDirectory.resolve("lib").resolve(relativePath).resolve(source.getFileName());
        if (copyFiles) {
          BuildHelper.copyFile(source, artifactFile);
        }
      }

      addArtifactMapping(artifact, entries, artifactFile);
    }

    return entries;
  }

  private static void addArtifactMapping(@NotNull JpsArtifact artifact,
                                         @NotNull Collection<DistributionFileEntry> entries,
                                         @NotNull Path artifactFile) {
    JpsCompositePackagingElement rootElement = artifact.getRootElement();
    for (JpsPackagingElement element : rootElement.getChildren()) {
      if (element instanceof JpsProductionModuleOutputPackagingElement) {
        entries.add(
          new ModuleOutputEntry(artifactFile, ((JpsProductionModuleOutputPackagingElement)element).getModuleReference().getModuleName(), 0,
                                "artifact: " + artifact.getName()));
      }
      else if (element instanceof JpsTestModuleOutputPackagingElement) {
        entries.add(
          new ModuleTestOutputEntry(artifactFile, ((JpsTestModuleOutputPackagingElement)element).getModuleReference().getModuleName()));
      }
      else if (element instanceof JpsLibraryFilesPackagingElement) {
        JpsLibrary library = ((JpsLibraryFilesPackagingElement)element).getLibraryReference().resolve();
        JpsElementReference<? extends JpsCompositeElement> parentReference = library.createReference().getParentReference();
        if (parentReference instanceof JpsModuleReference) {
          entries.add(new ModuleLibraryFileEntry(artifactFile, ((JpsModuleReference)parentReference).getModuleName(), null, 0));
        }
        else {
          ProjectLibraryData libraryData = new ProjectLibraryData(library.getName(), LibraryPackMode.MERGED);
          entries.add(new ProjectLibraryEntry(artifactFile, libraryData, null, 0));
        }
      }
    }
  }

  private static void checkModuleExcludes(MultiMap<String, String> moduleExcludes, @NotNull BuildContext context) {
    for (Map.Entry<String, Collection<String>> entry : moduleExcludes.entrySet()) {
      String module = entry.getKey();
      for (String pattern : entry.getValue()) {
        Path moduleOutput = context.getModuleOutputDir(context.findRequiredModule(module));
        if (Files.notExists(moduleOutput)) {
          context.getMessages().error("There are excludes defined for module \'" +
                                      module +
                                      "\', but the module wasn\'t compiled; ".plus("most probably it means that \'" +
                                                                                   module +
                                                                                   "\' isn\'t include into the product distribution so it makes no sense to define excludes for it."));
        }
      }
    }
  }

  public static Path basePath(BuildContext buildContext, String moduleName) {
    return Path.of(
      JpsPathUtil.urlToPath(DefaultGroovyMethods.first(buildContext.findRequiredModule(moduleName).getContentRootsList().getUrls())));
  }

  private static void patchKeyMapWithAltClickReassignedToMultipleCarets(ModuleOutputPatcher moduleOutputPatcher, BuildContext context) {
    if (!context.getProductProperties().getReassignAltClickToMultipleCarets()) {
      return;
    }


    String moduleName = "intellij.platform.resources";
    Path sourceFile = context.getModuleOutputDir(context.findModule(moduleName)).resolve("keymaps/\$default.xml");
    String defaultKeymapContent = Files.readString(sourceFile);
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt button1\"/>",
                                                        "<mouse-shortcut keystroke=\"to be alt shift button1\"/>");
    defaultKeymapContent =
      defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>", "<mouse-shortcut keystroke=\"alt button1\"/>");
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt shift button1\"/>");
    moduleOutputPatcher.patchModuleOutput(moduleName, "keymaps/\$default.xml", defaultKeymapContent);
  }

  private static final String PLUGINS_DIRECTORY = "plugins";
  private static final Comparator<PluginLayout> PLUGIN_LAYOUT_COMPARATOR_BY_MAIN_MODULE = new Comparator<PluginLayout>() {
    @Override
    public int compare(PluginLayout o1, PluginLayout o2) {
      //noinspection ChangeToOperator
      return o1.getMainModule().compareTo(o2.getMainModule());
    }
  };
  public final DistributionBuilderState state;
  private final PluginXmlPatcher pluginXmlPatcher;
}
