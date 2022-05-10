// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.Formats;
import com.intellij.util.io.Decompressor;
import com.intellij.util.lang.CompoundRuntimeException;
import com.intellij.util.system.CpuArch;
import groovy.io.FileType;
import groovy.lang.Closure;
import groovy.lang.GString;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import kotlin.text.Regex;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.IOGroovyMethods;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.aether.ArtifactKind;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.intellij.build.*;
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping;
import org.jetbrains.intellij.build.tasks.DirSource;
import org.jetbrains.intellij.build.tasks.JarBuilder;
import org.jetbrains.intellij.build.tasks.Source;
import org.jetbrains.intellij.build.tasks.ZipSource;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoriesConfiguration;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.*;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsTypedModuleSourceRoot;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.file.*;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class BuildTasksImpl extends BuildTasks {
  public BuildTasksImpl(BuildContext buildContext) {
    this.buildContext = buildContext;
  }

  @Override
  public void zipSourcesOfModules(final Collection<String> modules, final Path targetFile, final boolean includeLibraries) {
    buildContext.executeStep(TracerManager.spanBuilder("build module sources archives")
                               .setAttribute("path", buildContext.getPaths().getBuildOutputDir().relativize(targetFile).toString())
                               .setAttribute(AttributeKey.stringArrayKey("modules"), List.copyOf(modules)),
                             BuildOptions.SOURCES_ARCHIVE_STEP, new Closure<Void>(this, this) {
        public void doCall(Object it) {
          Files.createDirectories(targetFile.getParent());
          Files.deleteIfExists(targetFile);

          final LinkedHashSet<JpsLibrary> includedLibraries = new LinkedHashSet<JpsLibrary>();
          if (includeLibraries) {
            List<String> debugMapping = new ArrayList<String>();
            for (String moduleName : modules) {
              JpsModule module = getBuildContext().findRequiredModule(moduleName);
              if (moduleName.startsWith("intellij.platform.") && getBuildContext().findModule(moduleName + ".impl") != null) {
                Set<JpsLibrary> libraries =
                  JpsJavaExtensionService.dependencies(module).productionOnly().compileOnly().recursivelyExportedOnly().getLibraries();
                includedLibraries.addAll(libraries);
                DefaultGroovyMethods.collect(libraries, debugMapping, new Closure<String>(BuildTasksImpl.this, BuildTasksImpl.this) {
                  public String doCall(JpsLibrary it) {
                    return it.getName() + " for " + moduleName;
                  }

                  public String doCall() {
                    return doCall(null);
                  }
                });
              }
            }

            Span.current()
              .addEvent("collect libraries to include into archive", Attributes.of(AttributeKey.stringArrayKey("mapping"), debugMapping));
            final List<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>> librariesWithMissingSources =
              DefaultGroovyMethods.findAll(DefaultGroovyMethods.collect(includedLibraries,
                                                                        new Closure<JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>>(
                                                                          BuildTasksImpl.this, BuildTasksImpl.this) {
                                                                          public JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> doCall(
                                                                            JpsLibrary it) {
                                                                            return it.asTyped(JpsRepositoryLibraryType.INSTANCE);
                                                                          }

                                                                          public JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>> doCall() {
                                                                            return doCall(null);
                                                                          }
                                                                        }), new Closure<Boolean>(BuildTasksImpl.this, BuildTasksImpl.this) {
                public Boolean doCall(Object library) {
                  return library != null &&
                         DefaultGroovyMethods.any(
                           ((JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>)library).getFiles(
                             JpsOrderRootType.SOURCES), new Closure<Object>(BuildTasksImpl.this, BuildTasksImpl.this) {
                             public Object doCall(File it) { return !it.exists(); }

                             public Object doCall() {
                               return doCall(null);
                             }
                           });
                }
              });
            if (!librariesWithMissingSources.isEmpty()) {
              getBuildContext().getMessages()
                .debug("Download missing sources for " + String.valueOf(librariesWithMissingSources.size()) + " libraries");
              final JpsRemoteRepositoriesConfiguration configuration =
                JpsRemoteRepositoryService.getInstance().getRemoteRepositoriesConfiguration(getBuildContext().getProject());
              final List<RemoteRepository> collect =
                DefaultGroovyMethods.collect((configuration == null ? null : configuration.getRepositories()),
                                             new Closure<RemoteRepository>(BuildTasksImpl.this, BuildTasksImpl.this) {
                                               public RemoteRepository doCall(JpsRemoteRepositoryDescription it) {
                                                 return ArtifactRepositoryManager.createRemoteRepository(it.getId(), it.getUrl());
                                               }

                                               public RemoteRepository doCall() {
                                                 return doCall(null);
                                               }
                                             });
              List<RemoteRepository> repositories = DefaultGroovyMethods.asBoolean(collect) ? collect : new ArrayList();
              final ArtifactRepositoryManager repositoryManager =
                new ArtifactRepositoryManager(getLocalArtifactRepositoryRoot(getBuildContext().getProjectModel().getGlobal()), repositories,
                                              ProgressConsumer.DEAF);
              DefaultGroovyMethods.each(librariesWithMissingSources, new Closure<Void>(BuildTasksImpl.this, BuildTasksImpl.this) {
                public void doCall(final Object library) {
                  JpsMavenRepositoryLibraryDescriptor descriptor =
                    ((JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>)library).getProperties().getData();
                  getBuildContext().getMessages().progress("Downloading sources for library \'" +
                                                           ((JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>)library).getName() +
                                                           "\' (" +
                                                           descriptor.getMavenId() +
                                                           ")");
                  final Collection<Artifact> downloaded =
                    repositoryManager.resolveDependencyAsArtifact(descriptor.getGroupId(), descriptor.getArtifactId(),
                                                                  descriptor.getVersion(), EnumSet.of(ArtifactKind.SOURCES),
                                                                  descriptor.isIncludeTransitiveDependencies(),
                                                                  descriptor.getExcludedDependencies());
                  getBuildContext().getMessages().debug(" " +
                                                        ((JpsTypedLibrary<JpsSimpleElement<JpsMavenRepositoryLibraryDescriptor>>)library).getName() +
                                                        ": downloaded " +
                                                        DefaultGroovyMethods.join(downloaded, ", "));
                }
              });
            }
          }


          Map<Path, String> zipFileMap = new LinkedHashMap<Path, String>();
          for (String moduleName : modules) {
            getBuildContext().getMessages().debug(" include module " + moduleName);
            JpsModule module = getBuildContext().findRequiredModule(moduleName);
            for (JpsTypedModuleSourceRoot<JavaSourceRootProperties> root : module.getSourceRoots(JavaSourceRootType.SOURCE)) {
              if (root.getFile().getAbsoluteFile().exists()) {
                Path sourceFiles =
                  filterSourceFilesOnly(root.getFile().getName(), new Closure<Void>(BuildTasksImpl.this, BuildTasksImpl.this) {
                    public void doCall(Path it) {
                      FileUtil.copyDirContent(root.getFile().getAbsoluteFile(), it.toFile());
                    }

                    public void doCall() {
                      doCall(null);
                    }
                  });
                zipFileMap.put(sourceFiles, root.getProperties().getPackagePrefix().replace(".", "/"));
              }
            }

            for (JpsTypedModuleSourceRoot<JavaResourceRootProperties> root : module.getSourceRoots(JavaResourceRootType.RESOURCE)) {
              if (root.getFile().getAbsoluteFile().exists()) {
                Path sourceFiles =
                  filterSourceFilesOnly(root.getFile().getName(), new Closure<Void>(BuildTasksImpl.this, BuildTasksImpl.this) {
                    public void doCall(Path it) {
                      FileUtil.copyDirContent(root.getFile().getAbsoluteFile(), it.toFile());
                    }

                    public void doCall() {
                      doCall(null);
                    }
                  });
                zipFileMap.put(sourceFiles, root.getProperties().getRelativeOutputPath());
              }
            }
          }

          final List<String> libraryRootUrls =
            DefaultGroovyMethods.collectMany(includedLibraries, new Closure<Collection<String>>(BuildTasksImpl.this, BuildTasksImpl.this) {
              public Collection<String> doCall(JpsLibrary it) {
                return DefaultGroovyMethods.asType(it.getRootUrls(JpsOrderRootType.SOURCES), Collection.class);
              }

              public Collection<String> doCall() {
                return doCall(null);
              }
            });
          getBuildContext().getMessages().debug(" include " +
                                                String.valueOf(libraryRootUrls.size()) +
                                                " roots from " +
                                                String.valueOf(includedLibraries.size()) +
                                                " libraries:");
          for (String url : libraryRootUrls) {
            if (url.startsWith(JpsPathUtil.JAR_URL_PREFIX) && url.endsWith(JpsPathUtil.JAR_SEPARATOR)) {
              final File file = JpsPathUtil.urlToFile(url).getAbsoluteFile();
              if (file.isFile()) {
                getBuildContext().getMessages().debug("  " +
                                                      String.valueOf(file) +
                                                      ", " +
                                                      Formats.formatFileSize(file.length()) +
                                                      ", " +
                                                      StringGroovyMethods.padLeft(file.length().toString(), 9, "0") +
                                                      " bytes");
                Path sourceFiles = filterSourceFilesOnly(file.getName(), new Closure<Void>(BuildTasksImpl.this, BuildTasksImpl.this) {
                  public void doCall(Path it) {
                    new Decompressor.Zip(file).filter(new Closure<Boolean>(BuildTasksImpl.this, BuildTasksImpl.this) {
                      public Boolean doCall(String it) { return isSourceFile(it); }

                      public Boolean doCall() {
                        return doCall(null);
                      }
                    }).extract(it);
                  }

                  public void doCall() {
                    doCall(null);
                  }
                });
                zipFileMap.put(sourceFiles, "");
              }
              else {
                getBuildContext().getMessages().debug("  skipped root " + String.valueOf(file) + ": file doesn\'t exist");
              }
            }
            else {
              getBuildContext().getMessages().debug("  skipped root " + url + ": not a jar file");
            }
          }

          BuildHelper.zipWithPrefixes(getBuildContext(), targetFile, zipFileMap, true);
          getBuildContext().notifyArtifactWasBuilt(targetFile);
        }

        public void doCall() {
          doCall(null);
        }
      });
  }

  private static Boolean isSourceFile(String path) {
    return path.endsWith(".java") || path.endsWith(".groovy") || path.endsWith(".kt");
  }

  private Path filterSourceFilesOnly(final String name, Consumer<Path> configure) {
    Path sourceFiles = buildContext.getPaths().getTempDir().resolve(name + "-" + String.valueOf(UUID.randomUUID()));
    FileUtil.delete(sourceFiles);
    Files.createDirectories(sourceFiles);
    configure.accept(sourceFiles);
    IOGroovyMethods.withCloseable(Files.walk(sourceFiles), new Closure<Void>(this, this) {
      public void doCall(Object stream) {
        ((Stream<Path>)stream).forEach(new Closure<Void>(BuildTasksImpl.this, BuildTasksImpl.this) {
          public void doCall(Path it) {
            if (!Files.isDirectory(it) && !isSourceFile(it.toString())) {
              Files.delete(it);
            }
          }

          public void doCall() {
            doCall(null);
          }
        });
      }
    });
    return sourceFiles;
  }

  private static File getLocalArtifactRepositoryRoot(@NotNull JpsGlobal global) {
    String localRepoPath = JpsModelSerializationDataService.getPathVariablesConfiguration(global).getUserVariableValue("MAVEN_REPOSITORY");
    if (localRepoPath != null) {
      return new File(localRepoPath);
    }

    String root = System.getProperty("user.home", null);
    return root != null ? new File(root, ".m2/repository") : new File(".m2/repository");
  }

  /**
   * Build a list with modules that the IDE will provide for plugins.
   */
  private static void buildProvidedModuleList(final BuildContext context,
                                              final Path targetFile,
                                              @NotNull final DistributionJARsBuilder builder) {
    context.executeStep(TracerManager.spanBuilder("build provided module list"), BuildOptions.PROVIDED_MODULES_LIST_STEP, new Runnable() {
      @Override
      public void run() {
        Files.deleteIfExists(targetFile);
        Set<String> ideClasspath = builder.createIdeClassPath(context);
        // start the product in headless mode using com.intellij.ide.plugins.BundledPluginsLister
        BuildHelper.runApplicationStarter(context, context.getPaths().getTempDir().resolve("builtinModules"), ideClasspath,
                                          List.of("listBundledPlugins", targetFile.toString()), Collections.emptyMap(), null,
                                          TimeUnit.MINUTES.toMillis(10L), context.getClasspathCustomizer());
        if (Files.notExists(targetFile)) {
          context.getMessages().error("Failed to build provided modules list: " + String.valueOf(targetFile) + " doesn\'t exist");
        }

        context.getProductProperties().customizeBuiltinModules(context, targetFile);
        ((BuildContextImpl)context).setBuiltinModules(BuiltinModulesFileUtils.readBuiltinModulesFile(targetFile));
        context.notifyArtifactWasBuilt(targetFile);
      }
    });
  }

  public static Path patchIdeaPropertiesFile(BuildContext buildContext) {
    StringBuilder builder =
      new StringBuilder(Files.readString(buildContext.getPaths().getCommunityHomeDir().resolve("bin/idea.properties")));

    for (Path it : buildContext.getProductProperties().getAdditionalIDEPropertiesFilePaths()) {
      builder.append("\n").append(Files.readString(it));
    }


    //todo[nik] introduce special systemSelectorWithoutVersion instead?
    String settingsDir = buildContext.getSystemSelector().replaceFirst("\\d+(\\.\\d+)?", "");
    String temp = builder.toString();
    builder.setLength(0);
    LinkedHashMap<String, String> map = new LinkedHashMap<String, String>(1);
    map.put("settings_dir", settingsDir);
    builder.append(BuildUtils.replaceAll(temp, map, "@@"));

    if (buildContext.getApplicationInfo().isEAP()) {
      builder.append(
        "\n#-----------------------------------------------------------------------\n# Change to 'disabled' if you don't want to receive instant visual notifications\n# about fatal errors that happen to an IDE or plugins installed.\n#-----------------------------------------------------------------------\nidea.fatal.error.notification=enabled\n");
    }
    else {
      builder.append(
        "\n#-----------------------------------------------------------------------\n# Change to 'enabled' if you want to receive instant visual notifications\n# about fatal errors that happen to an IDE or plugins installed.\n#-----------------------------------------------------------------------\nidea.fatal.error.notification=disabled\n");
    }


    Path propertiesFile = buildContext.getPaths().getTempDir().resolve("idea.properties");
    Files.writeString(propertiesFile, builder);
    return propertiesFile;
  }

  private static void layoutShared(final BuildContext buildContext) {
    buildContext.getMessages().block("copy files shared among all distributions", new Supplier<Void>() {
      @Override
      public Void get() {
        Path licenseOutDir = buildContext.getPaths().getDistAllDir().resolve("license");
        BuildHelper.copyDir(buildContext.getPaths().getCommunityHomeDir().resolve("license"), licenseOutDir);
        for (String additionalDirWithLicenses : buildContext.getProductProperties().getAdditionalDirectoriesWithLicenses()) {
          BuildHelper.copyDir(Path.of(additionalDirWithLicenses), licenseOutDir);
        }


        if (buildContext.getApplicationInfo().getSvgRelativePath() != null) {
          Path from = findBrandingResource(buildContext.getApplicationInfo().getSvgRelativePath(), buildContext);
          Path to =
            buildContext.getPaths().getDistAllDir().resolve("bin/" + buildContext.getProductProperties().getBaseFileName() + ".svg");
          Files.createDirectories(to.getParent());
          Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }


        buildContext.getProductProperties().copyAdditionalFiles(buildContext, buildContext.getPaths().getDistAll());
        return null;
      }
    });
  }

  public static void generateBuildTxt(@NotNull BuildContext buildContext, @NotNull Path targetDirectory) {
    Files.writeString(targetDirectory.resolve("build.txt"), buildContext.getFullBuildNumber());
  }

  @NotNull
  private static Path findBrandingResource(@NotNull String relativePath, BuildContext buildContext) {
    String normalizedRelativePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
    Path inModule =
      buildContext.findFileInModuleSources(buildContext.getProductProperties().getApplicationInfoModule(), normalizedRelativePath);
    if (inModule != null) {
      return inModule;
    }


    for (String brandingResourceDir : buildContext.getProductProperties().getBrandingResourcePaths()) {
      Path file = Paths.get(brandingResourceDir, normalizedRelativePath);
      if (Files.exists(file)) {
        return file;
      }
    }


    buildContext.getMessages().error("Cannot find \'" +
                                     normalizedRelativePath +
                                     "\' neither in sources of \'" +
                                     buildContext.getProductProperties().getApplicationInfoModule() +
                                     "\'".plus(
                                       " nor in " + String.valueOf(buildContext.getProductProperties().getBrandingResourcePaths())));
    return null;
  }

  @NotNull
  private static BuildTaskRunnable createDistributionForOsTask(@NotNull final OsFamily os,
                                                               @NotNull final JvmArchitecture arch,
                                                               @NotNull final Map<Pair<OsFamily, JvmArchitecture>, Path> result,
                                                               @NotNull final Path ideaProperties) {
    return createDistributionForOsTask(os, arch, new Closure<Path>(null, null) {
      public Path doCall(Object context) {
        OsSpecificDistributionBuilder builder = ((BuildContext)context).getOsDistributionBuilder(os, ideaProperties);
        if (builder != null) {
          Path osAndArchSpecificDistDirectory = DistributionJARsBuilder.getOsAndArchSpecificDistDirectory(os, arch, (BuildContext)context);
          builder.buildArtifacts(osAndArchSpecificDistDirectory, arch);
          return result.put(new Pair(os, arch), osAndArchSpecificDistDirectory);
        }
      }
    });
  }

  @NotNull
  private static BuildTaskRunnable createDistributionForOsTask(@NotNull final OsFamily os,
                                                               @NotNull final JvmArchitecture arch,
                                                               @NotNull final Consumer<BuildContext> buildAction) {
    return BuildTaskRunnable.task(os.osId + " " + arch.name(), new Consumer<BuildContext>() {
      @Override
      public void accept(final BuildContext context) {
        if (!context.shouldBuildDistributionForOS(os.osId)) {
          return;
        }


        context.getMessages().block("build " + os.osName + " " + arch.name() + " distribution", new Supplier<Void>() {
          @Override
          public Void get() {
            buildAction.accept(context);
            return null;
          }
        });
      }
    });
  }

  @Override
  public void compileModulesFromProduct() {
    checkProductProperties();
    compileModulesForDistribution(buildContext);
  }

  private DistributionJARsBuilder compileModulesForDistribution(BuildContext context) {
    Set<PluginLayout> pluginsToPublish =
      DistributionJARsBuilder.getPluginsByModules(context, context.getProductProperties().getProductLayout().getPluginModulesToPublish());
    return compileModulesForDistribution(pluginsToPublish, context);
  }

  private DistributionJARsBuilder compileModulesForDistribution(Set<PluginLayout> pluginsToPublish, final BuildContext context) {
    ProductProperties productProperties = context.getProductProperties();
    ProductModulesLayout productLayout = productProperties.getProductLayout();
    Collection<String> moduleNames = DistributionJARsBuilder.getModulesToCompile(context);
    MavenArtifactsProperties mavenArtifacts = productProperties.getMavenArtifacts();

    Set<String> toCompile = new LinkedHashSet<String>();
    toCompile.addAll(moduleNames);
    final ScrambleTool tool = context.getProprietaryBuildTools().getScrambleTool();
    final List<String> compile = (tool == null ? null : tool.getAdditionalModulesToCompile());
    toCompile.addAll(DefaultGroovyMethods.asBoolean(compile) ? compile : Collections.emptyList());
    toCompile.addAll(productLayout.getMainModules());
    toCompile.addAll(mavenArtifacts.getAdditionalModules());
    toCompile.addAll(mavenArtifacts.getSquashedModules());
    toCompile.addAll(mavenArtifacts.getProprietaryModules());
    toCompile.addAll(productProperties.getModulesToCompileTests());
    compileModules(toCompile);

    if (context.shouldBuildDistributions()) {
      DistributionJARsBuilder builder = compilePlatformAndPluginModules(pluginsToPublish);
      Path providedModulesFile =
        context.getPaths().getArtifactDir().resolve(context.getApplicationInfo().getProductCode() + "-builtinModules.json");
      buildProvidedModuleList(context, providedModulesFile, builder);
      if (productProperties.getProductLayout().getBuildAllCompatiblePlugins()) {
        if (context.getOptions().buildStepsToSkip.contains(BuildOptions.PROVIDED_MODULES_LIST_STEP)) {
          context.getMessages().info("Skipping collecting compatible plugins because PROVIDED_MODULES_LIST_STEP was skipped");
        }
        else {
          pluginsToPublish = new LinkedHashSet<PluginLayout>(pluginsToPublish);
          pluginsToPublish.addAll(PluginsCollector.collectCompatiblePluginsToPublish(providedModulesFile, context));
        }
      }
    }

    return compilePlatformAndPluginModules(pluginsToPublish);
  }

  private DistributionJARsBuilder compilePlatformAndPluginModules(@NotNull Set<PluginLayout> pluginsToPublish) {
    DistributionJARsBuilder distBuilder = new DistributionJARsBuilder(buildContext, pluginsToPublish);
    compileModules(DefaultGroovyMethods.plus(distBuilder.getModulesForPluginsToPublish(), new ArrayList<String>(
      Arrays.asList("intellij.idea.community.build.tasks", "intellij.platform.images.build"))));

    // we need this to ensure that all libraries which may be used in the distribution are resolved,
    // even if product modules don't depend on them (e.g. JUnit5)
    CompilationTasks compilationTasks = CompilationTasks.create(buildContext);
    compilationTasks.resolveProjectDependencies();
    compilationTasks.buildProjectArtifacts(distBuilder.getIncludedProjectArtifacts(buildContext));
    return distBuilder;
  }

  @Override
  public void buildDistributions() {
    Span span = TracerManager.spanBuilder("build distributions").startSpan();
    Scope spanScope = span.makeCurrent();
    try {
      doBuildDistributions(buildContext);
    }
    catch (Throwable e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR, e.getMessage());

      try {
        TracerManager.INSTANCE.finish();
      }
      catch (Throwable ignore) {
      }

      throw e;
    }
    finally {
      span.end();
      spanScope.close();
    }
  }

  private void doBuildDistributions(final BuildContext context) {
    checkProductProperties();
    copyDependenciesFile(context);

    logFreeDiskSpace("before compilation");

    final Set<PluginLayout> pluginsToPublish =
      DistributionJARsBuilder.getPluginsByModules(context, context.getProductProperties().getProductLayout().getPluginModulesToPublish());

    final DistributionJARsBuilder distributionJARsBuilder = compileModulesForDistribution(buildContext);
    logFreeDiskSpace("after compilation");

    final MavenArtifactsProperties mavenArtifacts = context.getProductProperties().getMavenArtifacts();
    if (mavenArtifacts.getForIdeModules() ||
        !mavenArtifacts.getAdditionalModules().isEmpty() ||
        !mavenArtifacts.getSquashedModules().isEmpty() ||
        !mavenArtifacts.getProprietaryModules().isEmpty()) {
      context.executeStep("generate maven artifacts", BuildOptions.MAVEN_ARTIFACTS_STEP, new Runnable() {
        @Override
        public void run() {
          MavenArtifactsBuilder mavenArtifactsBuilder = new MavenArtifactsBuilder(context);
          List<String> moduleNames = new ArrayList<String>();
          if (mavenArtifacts.getForIdeModules()) {
            Set<String> bundledPlugins = Set.copyOf(context.getProductProperties().getProductLayout().getBundledPluginModules());
            moduleNames.addAll(distributionJARsBuilder.getPlatformModules());
            moduleNames.addAll(context.getProductProperties().getProductLayout().getIncludedPluginModules(bundledPlugins));
          }

          moduleNames.addAll(mavenArtifacts.getAdditionalModules());
          if (!moduleNames.isEmpty()) {
            mavenArtifactsBuilder.generateMavenArtifacts(moduleNames, mavenArtifacts.getSquashedModules(), "maven-artifacts");
          }

          if (!mavenArtifacts.getProprietaryModules().isEmpty()) {
            mavenArtifactsBuilder.generateMavenArtifacts(mavenArtifacts.getProprietaryModules(), Collections.emptyList(),
                                                         "proprietary-maven-artifacts");
          }
        }
      });
    }


    context.getMessages().block("build platform and plugin JARs", new Supplier<Void>() {
      @Override
      public Void get() {
        if (context.shouldBuildDistributions()) {
          ProjectStructureMapping projectStructureMapping = distributionJARsBuilder.buildJARs(context);
          DistributionJARsBuilder.buildAdditionalArtifacts(context, projectStructureMapping);
        }
        else {
          Span.current().addEvent("skip building product distributions because " +
                                  "\"intellij.build.target.os\" property is set to \"" +
                                  BuildOptions.OS_NONE +
                                  "\"");
          distributionJARsBuilder.buildSearchableOptions(context, context.getClasspathCustomizer());
          distributionJARsBuilder.createBuildNonBundledPluginsTask(pluginsToPublish, true, null, context).fork().join();
        }

        return null;
      }
    });

    if (context.shouldBuildDistributions()) {
      layoutShared(context);
      final Map<Pair<OsFamily, JvmArchitecture>, Path> distDirs = buildOsSpecificDistributions(context);
      if (Boolean.getBoolean("intellij.build.toolbox.litegen")) {
        if (context.getBuildNumber() == null) {
          context.getMessages().warning("Toolbox LiteGen is not executed - it does not support SNAPSHOT build numbers");
        }
        else if (!context.getOptions().targetOS.equals(BuildOptions.OS_ALL)) {
          context.getMessages()
            .warning("Toolbox LiteGen is not executed - it doesn't support installers are being built only for specific OS");
        }
        else {
          context.executeStep("build toolbox lite-gen links", BuildOptions.TOOLBOX_LITE_GEN_STEP, new Runnable() {
            @Override
            public void run() {
              String toolboxLiteGenVersion = System.getProperty("intellij.build.toolbox.litegen.version");
              if (toolboxLiteGenVersion == null) {
                context.getMessages().error("Toolbox Lite-Gen version is not specified!");
              }
              else {
                ToolboxLiteGen.runToolboxLiteGen(context.getPaths().getBuildDependenciesCommunityRoot(), context.getMessages(),
                                                 toolboxLiteGenVersion, "/artifacts-dir=" + context.getPaths().getArtifacts(),
                                                 "/product-code=" + context.getApplicationInfo().getProductCode(),
                                                 "/isEAP=" + String.valueOf(context.getApplicationInfo().isEAP()),
                                                 "/output-dir=" + context.getPaths().getBuildOutputRoot() + "/toolbox-lite-gen");
              }
            }
          });
        }
      }


      if (context.getProductProperties().getBuildCrossPlatformDistribution()) {
        if (distDirs.size() == SUPPORTED_DISTRIBUTIONS.size()) {
          context.executeStep("build cross-platform distribution", BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP, new Runnable() {
            @Override
            public void run() {
              CrossPlatformDistributionBuilder.buildCrossPlatformZip(distDirs, context);
            }
          });
        }
        else {
          Span.current().addEvent("skip building cross-platform distribution because some OS/arch-specific distributions were skipped");
        }
      }
    }

    logFreeDiskSpace("after building distributions");
  }

  private Map<Pair<OsFamily, JvmArchitecture>, Path> buildOsSpecificDistributions(final BuildContext context) {
    final Path propertiesFile = patchIdeaPropertiesFile(buildContext);
    final Map<Pair<OsFamily, JvmArchitecture>, Path> distDirs =
      Collections.synchronizedMap(new HashMap<Pair<OsFamily, JvmArchitecture>, Path>(SUPPORTED_DISTRIBUTIONS.size()));
    buildContext.executeStep("Building OS-specific distributions", BuildOptions.OS_SPECIFIC_DISTRIBUTIONS_STEP,
                             new Closure<Void>(this, this) {
                               public void doCall(Object it) {
                                 List<BuildTaskRunnable> createDistTasks =
                                   List.of(createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.x64, distDirs, propertiesFile),
                                           createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.aarch64, distDirs, propertiesFile),
                                           createDistributionForOsTask(OsFamily.WINDOWS, JvmArchitecture.x64, distDirs, propertiesFile),
                                           createDistributionForOsTask(OsFamily.LINUX, JvmArchitecture.x64, distDirs, propertiesFile));
                                 runInParallel(DefaultGroovyMethods.findAll(createDistTasks,
                                                                            new Closure<Boolean>(BuildTasksImpl.this, BuildTasksImpl.this) {
                                                                              public Boolean doCall(BuildTaskRunnable it) {
                                                                                return it !=
                                                                                       null;
                                                                              }

                                                                              public Boolean doCall() {
                                                                                return doCall(null);
                                                                              }
                                                                            }), context);
                               }

                               public void doCall() {
                                 doCall(null);
                               }
                             });
    return distDirs;
  }

  private static Path find(final Path directory, final String suffix, final BuildContext context) {
    return IOGroovyMethods.withCloseable(Files.walk(directory), new Closure<Path>(null, null) {
      public Path doCall(Object stream) {
        List<Path> found = ((Stream<Path>)stream).filter(new Closure<Boolean>(null, null) {
          public Boolean doCall(Path it) { return ((GString)String.valueOf(it.getFileName())).endsWith(suffix); }

          public Boolean doCall() {
            return doCall(null);
          }
        }).collect((Collector<? super Path, ?, List<Path>>)Collectors.toList());
        if (found.isEmpty()) {
          context.getMessages().error("No file with suffix " + suffix + " is found in " + String.valueOf(directory));
        }

        if (found.size() > 1) {
          context.getMessages().error("Multiple files with suffix " +
                                      suffix +
                                      " are found in " +
                                      String.valueOf(directory) +
                                      ":\n".plus(DefaultGroovyMethods.join(found, "\n")));
        }

        return DefaultGroovyMethods.first(found);
      }
    });
  }

  @Override
  public void buildDmg(final Path macZipDir) {
    List<BuildTaskRunnable> createDistTasks =
      List.of(createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.x64, new Consumer<BuildContext>() {
        @Override
        public void accept(final BuildContext context) {
          BuiltinModulesFileData readBuiltinModules =
            BuiltinModulesFileUtils.readBuiltinModulesFile(find(macZipDir, "builtinModules.json", context));
          ((BuildContextImpl)context).setBuiltinModules(readBuiltinModules);
          DefaultGroovyMethods.with(context.getMacDistributionCustomizer(), new Closure<Void>(this, this) {
            public void doCall(MacDistributionCustomizer it) {
              Path macZip = find(macZipDir, String.valueOf(JvmArchitecture.x64) + ".zip", context);
              (DefaultGroovyMethods.asType(context.getOsDistributionBuilder(OsFamily.MACOS, null),
                                           MacDistributionBuilder.class)).buildAndSignDmgFromZip(macZip, JvmArchitecture.x64);
            }

            public void doCall() {
              doCall(null);
            }
          });
        }
      }), createDistributionForOsTask(OsFamily.MACOS, JvmArchitecture.aarch64, new Consumer<BuildContext>() {
        @Override
        public void accept(BuildContext context) {
          BuiltinModulesFileData readBuiltinModules =
            BuiltinModulesFileUtils.readBuiltinModulesFile(find(macZipDir, "builtinModules.json", context));
          ((BuildContextImpl)context).setBuiltinModules(readBuiltinModules);
          Path macZip = find(macZipDir, String.valueOf(JvmArchitecture.aarch64) + ".zip", context);
          (DefaultGroovyMethods.asType(context.getOsDistributionBuilder(OsFamily.MACOS, null),
                                       MacDistributionBuilder.class)).buildAndSignDmgFromZip(macZip, JvmArchitecture.aarch64);
        }
      }));
    runInParallel(DefaultGroovyMethods.findAll(createDistTasks, new Closure<Boolean>(this, this) {
      public Boolean doCall(BuildTaskRunnable it) { return it != null; }

      public Boolean doCall() {
        return doCall(null);
      }
    }), buildContext);
  }

  @Override
  public void buildNonBundledPlugins(List<String> mainPluginModules) {
    checkProductProperties();
    checkPluginModules(mainPluginModules, "mainPluginModules",
                       buildContext.getProductProperties().getProductLayout().getAllNonTrivialPlugins());
    copyDependenciesFile(buildContext);
    Set<PluginLayout> pluginsToPublish = DistributionJARsBuilder.getPluginsByModules(buildContext, mainPluginModules);
    DistributionJARsBuilder distributionJARsBuilder = compilePlatformAndPluginModules(pluginsToPublish);
    distributionJARsBuilder.buildSearchableOptions(buildContext);
    distributionJARsBuilder.createBuildNonBundledPluginsTask(pluginsToPublish, true, null, buildContext).fork().join();
  }

  @Override
  public void generateProjectStructureMapping(Path targetFile) {
    Files.createDirectories(buildContext.getPaths().getTempDir());
    Path pluginLayoutRoot = Files.createTempDirectory(buildContext.getPaths().getTempDir(), "pluginLayoutRoot");
    new DistributionJARsBuilder(buildContext).generateProjectStructureMapping(targetFile, buildContext, pluginLayoutRoot);
  }

  @NotNull
  public static Path unpackPty4jNative(final BuildContext buildContext, @NotNull Path distDir, final String pty4jOsSubpackageName) {
    final Path pty4jNativeDir = distDir.resolve("lib/pty4j-native");
    final String nativePkg = "resources/com/pty4j/native";
    DefaultGroovyMethods.each(buildContext.getProject().getLibraryCollection().findLibrary("pty4j").getFiles(JpsOrderRootType.COMPILED),
                              new Closure<File[]>(null, null) {
                                public File[] doCall(File it) {
                                  Path tempDir = Files.createTempDirectory(buildContext.getPaths().getTempDir(), it.getName());
                                  try {
                                    new Decompressor.Zip(it).withZipExtensions().extract(tempDir);
                                    Path nativeDir = tempDir.resolve(nativePkg);
                                    if (Files.isDirectory(nativeDir)) {
                                      return DefaultGroovyMethods.each(nativeDir.toFile().listFiles(), new Closure<Void>(null, null) {
                                        public void doCall(Object child) {
                                          String childName = ((File)child).getName();
                                          if (pty4jOsSubpackageName == null || pty4jOsSubpackageName.equals(childName)) {
                                            File dest = new File(pty4jNativeDir.toFile(), childName);
                                            FileUtil.createDirectory(dest);
                                            FileUtil.copyDir((File)child, dest);
                                          }
                                        }
                                      });
                                    }
                                  }
                                  finally {
                                    FileUtil.delete(tempDir);
                                  }
                                }

                                public File[] doCall() {
                                  return doCall(null);
                                }
                              });
    final List<File> files = new ArrayList<File>();
    ResourceGroovyMethods.eachFileRecurse(pty4jNativeDir.toFile(), FileType.FILES, new Closure<Boolean>(null, null) {
      public Boolean doCall(File it) {
        return files.add(it);
      }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (files.isEmpty()) {
      buildContext.getMessages().error("Cannot layout pty4j native: no files extracted");
    }

    return pty4jNativeDir;
  }

  public static List<String> addDbusJava(CompilationContext context, @NotNull Path libDir) {
    JpsLibrary library = context.findModule("intellij.platform.credentialStore").getLibraryCollection().findLibrary("dbus-java");
    List<String> extraJars = new ArrayList<String>();
    Files.createDirectories(libDir);
    for (File file : library.getFiles(JpsOrderRootType.COMPILED)) {
      BuildHelper.copyFileToDir(file.toPath(), libDir);
      extraJars.add(file.getName());
    }

    return extraJars;
  }

  private void logFreeDiskSpace(String phase) {
    CompilationContextImpl.logFreeDiskSpace(buildContext.getMessages(), buildContext.getPaths().getBuildOutputDir(), phase);
  }

  private static void copyDependenciesFile(BuildContext context) {
    Path outputFile = context.getPaths().getArtifactDir().resolve("dependencies.txt");
    Files.createDirectories(outputFile.getParent());
    Files.copy(context.getDependenciesProperties().getFile(), outputFile, StandardCopyOption.REPLACE_EXISTING);
    context.notifyArtifactWasBuilt(outputFile);
  }

  private void checkProductProperties() {
    checkProductLayout();
    ProductProperties properties = buildContext.getProductProperties();
    checkPaths2(properties.getBrandingResourcePaths(), "productProperties.brandingResourcePaths");
    checkPaths2(properties.getAdditionalIDEPropertiesFilePaths(), "productProperties.additionalIDEPropertiesFilePaths");
    checkPaths2(properties.getAdditionalDirectoriesWithLicenses(), "productProperties.additionalDirectoriesWithLicenses");

    checkModules(properties.getAdditionalModulesToCompile(), "productProperties.additionalModulesToCompile");
    checkModules(properties.getModulesToCompileTests(), "productProperties.modulesToCompileTests");

    WindowsDistributionCustomizer winCustomizer = buildContext.getWindowsDistributionCustomizer();
    checkPaths(new ArrayList<String>(Arrays.asList((winCustomizer == null ? null : winCustomizer.getIcoPath()))),
               "productProperties.windowsCustomizer.icoPath");
    checkPaths(new ArrayList<String>(Arrays.asList((winCustomizer == null ? null : winCustomizer.getIcoPathForEAP()))),
               "productProperties.windowsCustomizer.icoPathForEAP");
    checkPaths(new ArrayList<String>(Arrays.asList((winCustomizer == null ? null : winCustomizer.getInstallerImagesPath()))),
               "productProperties.windowsCustomizer.installerImagesPath");

    final LinuxDistributionCustomizer customizer = buildContext.getLinuxDistributionCustomizer();
    checkPaths(new ArrayList<String>(Arrays.asList((customizer == null ? null : customizer.getIconPngPath()))),
               "productProperties.linuxCustomizer.iconPngPath");
    final LinuxDistributionCustomizer customizer1 = buildContext.getLinuxDistributionCustomizer();
    checkPaths(new ArrayList<String>(Arrays.asList((customizer1 == null ? null : customizer1.getIconPngPathForEAP()))),
               "productProperties.linuxCustomizer.iconPngPathForEAP");

    MacDistributionCustomizer macCustomizer = buildContext.getMacDistributionCustomizer();
    if (macCustomizer != null) {
      checkMandatoryField(macCustomizer.getBundleIdentifier(), "productProperties.macCustomizer.bundleIdentifier");
      checkMandatoryPath(macCustomizer.getIcnsPath(), "productProperties.macCustomizer.icnsPath");
      checkPaths(new ArrayList<String>(Arrays.asList(macCustomizer.getIcnsPathForEAP())), "productProperties.macCustomizer.icnsPathForEAP");
      checkMandatoryPath(macCustomizer.getDmgImagePath(), "productProperties.macCustomizer.dmgImagePath");
      checkPaths(new ArrayList<String>(Arrays.asList(macCustomizer.getDmgImagePathForEAP())),
                 "productProperties.macCustomizer.dmgImagePathForEAP");
    }


    checkModules(properties.getMavenArtifacts().getAdditionalModules(), "productProperties.mavenArtifacts.additionalModules");
    checkModules(properties.getMavenArtifacts().getSquashedModules(), "productProperties.mavenArtifacts.squashedModules");
    if (buildContext.getProductProperties().getScrambleMainJar()) {
      final ScrambleTool tool = buildContext.getProprietaryBuildTools().getScrambleTool();
      checkModules((tool == null ? null : tool.getNamesOfModulesRequiredToBeScrambled()),
                   "ProprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled");
    }
  }

  private void checkProductLayout() {
    ProductModulesLayout layout = buildContext.getProductProperties().getProductLayout();
    if (layout.getMainJarName() == null) {
      buildContext.getMessages().error("productProperties.productLayout.mainJarName is not specified");
    }


    List<PluginLayout> nonTrivialPlugins = layout.getAllNonTrivialPlugins();
    checkPluginDuplicates(nonTrivialPlugins);

    checkPluginModules(layout.getBundledPluginModules(), "productProperties.productLayout.bundledPluginModules", nonTrivialPlugins);
    checkPluginModules(layout.getPluginModulesToPublish(), "productProperties.productLayout.pluginModulesToPublish", nonTrivialPlugins);
    checkPluginModules(layout.getCompatiblePluginsToIgnore(), "productProperties.productLayout.compatiblePluginsToIgnore",
                       nonTrivialPlugins);

    if (!layout.getBuildAllCompatiblePlugins() && !layout.getCompatiblePluginsToIgnore().isEmpty()) {
      buildContext.getMessages().warning("layout.buildAllCompatiblePlugins option isn't enabled. Value of " +
                                         "layout.compatiblePluginsToIgnore property will be ignored (" +
                                         String.valueOf(layout.getCompatiblePluginsToIgnore()) +
                                         ")");
    }

    if (layout.getBuildAllCompatiblePlugins() && !layout.getCompatiblePluginsToIgnore().isEmpty()) {
      checkPluginModules(layout.getCompatiblePluginsToIgnore(), "productProperties.productLayout.compatiblePluginsToIgnore",
                         nonTrivialPlugins);
    }


    if (!buildContext.shouldBuildDistributions() && layout.getBuildAllCompatiblePlugins()) {
      buildContext.getMessages().warning("Distribution is not going to build. Hence all compatible plugins won't be built despite " +
                                         "layout.buildAllCompatiblePlugins option is enabled. layout.pluginModulesToPublish will be used (" +
                                         String.valueOf(layout.getPluginModulesToPublish()) +
                                         ")");
    }

    if (layout.getPrepareCustomPluginRepositoryForPublishedPlugins() &&
        layout.getPluginModulesToPublish().isEmpty() &&
        !layout.getBuildAllCompatiblePlugins()) {
      buildContext.getMessages().error(
        "productProperties.productLayout.prepareCustomPluginRepositoryForPublishedPlugins option is enabled" +
        " but no pluginModulesToPublish are specified");
    }


    checkModules(layout.getProductApiModules(), "productProperties.productLayout.productApiModules");
    checkModules(layout.getProductImplementationModules(), "productProperties.productLayout.productImplementationModules");
    checkModules(layout.getAdditionalPlatformJars().values(), "productProperties.productLayout.additionalPlatformJars");
    checkModules(layout.getModuleExcludes().keySet(), "productProperties.productLayout.moduleExcludes");
    checkModules(layout.getMainModules(), "productProperties.productLayout.mainModules");
    checkProjectLibraries(layout.getProjectLibrariesToUnpackIntoMainJar(),
                          "productProperties.productLayout.projectLibrariesToUnpackIntoMainJar", buildContext);
    DefaultGroovyMethods.each(nonTrivialPlugins, new Closure<Void>(this, this) {
      public void doCall(Object plugin) {
        checkBaseLayout((BaseLayout)plugin, "\'" + ((PluginLayout)plugin).getMainModule() + "\' plugin");
      }
    });
  }

  private void checkBaseLayout(BaseLayout layout, String description) {
    checkModules(layout.getIncludedModuleNames(), "moduleJars in " + description);
    checkArtifacts(layout.getIncludedArtifacts().keySet(), "includedArtifacts in " + description);
    checkModules(DefaultGroovyMethods.collect(layout.getResourcePaths(), new Closure<String>(this, this) {
      public String doCall(ModuleResourceData it) { return it.getModuleName(); }

      public String doCall() {
        return doCall(null);
      }
    }), "resourcePaths in " + description);
    checkModules(layout.getModuleExcludes().keySet(), "moduleExcludes in " + description);
    checkProjectLibraries(DefaultGroovyMethods.collect(layout.getIncludedProjectLibraries(), new Closure<String>(this, this) {
      public String doCall(ProjectLibraryData it) { return it.getLibraryName(); }

      public String doCall() {
        return doCall(null);
      }
    }), "includedProjectLibraries in " + description, buildContext);
    for (ModuleLibraryData data : layout.getIncludedModuleLibraries()) {
      checkModules(new ArrayList<String>(Arrays.asList(data.getModuleName())), "includedModuleLibraries in " + description);
      if (DefaultGroovyMethods.find(buildContext.findRequiredModule(data.getModuleName()).getLibraryCollection().getLibraries(),
                                    new Closure<Boolean>(this, this) {
                                      public Boolean doCall(JpsLibrary it) {
                                        return JarPackager.getLibraryName(it).equals(data.getLibraryName());
                                      }

                                      public Boolean doCall() {
                                        return doCall(null);
                                      }
                                    }) == null) {
        buildContext.getMessages()
          .error("Cannot find library \'" + data.getLibraryName() + "\' in \'" + data.getModuleName() + "\' (used in " + description + ")");
      }
    }

    checkModules(layout.getExcludedModuleLibraries().keySet(), "excludedModuleLibraries in " + description);
    for (Map.Entry<String, Collection<String>> entry : layout.getExcludedModuleLibraries().entrySet()) {
      List<JpsLibrary> libraries = buildContext.findRequiredModule(entry.getKey()).getLibraryCollection().getLibraries();
      for (String libraryName : entry.getValue()) {
        if (DefaultGroovyMethods.find(libraries, new Closure<Boolean>(this, this) {
          public Boolean doCall(JpsLibrary it) { return JarPackager.getLibraryName(it).equals(libraryName); }

          public Boolean doCall() {
            return doCall(null);
          }
        }) == null) {
          buildContext.getMessages().error("Cannot find library \'" +
                                           libraryName +
                                           "\' in \'" +
                                           entry.getKey() +
                                           "\' (used in \'excludedModuleLibraries\' in " +
                                           description +
                                           ")");
        }
      }
    }

    checkProjectLibraries(layout.getProjectLibrariesToUnpack().values(), "projectLibrariesToUnpack in " + description, buildContext);
    checkModules(layout.getModulesWithExcludedModuleLibraries(), "modulesWithExcludedModuleLibraries in " + description);
  }

  private void checkPluginDuplicates(List<PluginLayout> nonTrivialPlugins) {
    Collection<List<PluginLayout>> pluginsGroupedByMainModule =
      DefaultGroovyMethods.groupBy(nonTrivialPlugins, new Closure<String>(this, this) {
        public String doCall(PluginLayout it) { return it.getMainModule(); }

        public String doCall() {
          return doCall(null);
        }
      }).values();
    for (List<PluginLayout> duplicatedPlugins : pluginsGroupedByMainModule) {
      if (duplicatedPlugins.size() > 1) {
        buildContext.getMessages()
          .warning("Duplicated plugin description in productLayout.allNonTrivialPlugins: " + duplicatedPlugins.get(0).getMainModule());
      }
    }
  }

  private void checkModules(Collection<String> modules, String fieldName) {
    if (modules != null) {
      Collection<String> unknownModules = DefaultGroovyMethods.findAll(modules, new Closure<Boolean>(this, this) {
        public Boolean doCall(String it) { return getBuildContext().findModule(it) == null; }

        public Boolean doCall() {
          return doCall(null);
        }
      });
      if (!unknownModules.isEmpty()) {
        buildContext.getMessages()
          .error("The following modules from " + fieldName + " aren\'t found in the project: " + String.valueOf(unknownModules));
      }
    }
  }

  private static void checkProjectLibraries(Collection<String> names, String fieldName, final BuildContext context) {
    Collection<String> unknownLibraries = DefaultGroovyMethods.findAll(names, new Closure<Boolean>(null, null) {
      public Boolean doCall(String it) { return context.getProject().getLibraryCollection().findLibrary(it) == null; }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (!unknownLibraries.isEmpty()) {
      context.getMessages()
        .error("The following libraries from " + fieldName + " aren\'t found in the project: " + String.valueOf(unknownLibraries));
    }
  }

  private void checkArtifacts(Collection<String> names, String fieldName) {
    Collection<String> unknownArtifacts = DefaultGroovyMethods.minus(names, DefaultGroovyMethods.collect(
      JpsArtifactService.getInstance().getArtifacts(buildContext.getProject()), new Closure<String>(this, this) {
        public String doCall(JpsArtifact it) { return it.getName(); }

        public String doCall() {
          return doCall(null);
        }
      }));
    if (!unknownArtifacts.isEmpty()) {
      buildContext.getMessages()
        .error("The following artifacts from " + fieldName + " aren\'t found in the project: " + String.valueOf(unknownArtifacts));
    }
  }

  private void checkPluginModules(List<String> pluginModules, String fieldName, final List<PluginLayout> pluginLayoutList) {
    if (pluginModules == null) {
      return;
    }

    checkModules(pluginModules, fieldName);

    List<String> unspecifiedLayoutPluginModules = DefaultGroovyMethods.findAll(pluginModules, new Closure<Boolean>(this, this) {
      public Boolean doCall(final Object mainModuleName) {
        return DefaultGroovyMethods.find(pluginLayoutList, new Closure<Boolean>(BuildTasksImpl.this, BuildTasksImpl.this) {
          public Boolean doCall(PluginLayout it) { return it.getMainModule().equals(mainModuleName); }

          public Boolean doCall() {
            return doCall(null);
          }
        }) == null;
      }
    });
    if (!unspecifiedLayoutPluginModules.isEmpty()) {
      buildContext.getMessages().info(
        "No plugin layout specified in productProperties.productLayout.allNonTrivialPlugins for following plugin main modules. " +
        "Assuming simple layout. Modules list: " +
        String.valueOf(unspecifiedLayoutPluginModules));
    }


    List<String> unknownBundledPluginModules = DefaultGroovyMethods.findAll(pluginModules, new Closure<Boolean>(this, this) {
      public Boolean doCall(String it) { return getBuildContext().findFileInModuleSources(it, "META-INF/plugin.xml") == null; }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (!unknownBundledPluginModules.isEmpty()) {
      buildContext.getMessages().error("The following modules from " +
                                       fieldName +
                                       " don\'t contain META-INF/plugin.xml file and aren\'t specified as optional plugin modules ".plus(
                                         "in productProperties.productLayout.allNonTrivialPlugins: " +
                                         String.valueOf(unknownBundledPluginModules) +
                                         ". "));
    }
  }

  private void checkPaths(@NotNull Collection<String> paths, String fieldName) {
    final Collection<String> nonExistingFiles = DefaultGroovyMethods.findAll(paths, new Closure<Boolean>(this, this) {
      public Boolean doCall(String it) { return it != null && !Files.exists(Paths.get(it)); }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (!nonExistingFiles.isEmpty()) {
      buildContext.getMessages().error(fieldName + " contains non-existing path" + nonExistingFiles.size().compareTo(1) > 0
                                       ? "s"
                                       : "" + ": " + String.join(",", nonExistingFiles));
    }
  }

  private void checkPaths2(@NotNull Collection<Path> paths, String fieldName) {
    final Collection<Path> nonExistingFiles = DefaultGroovyMethods.findAll(paths, new Closure<Boolean>(this, this) {
      public Boolean doCall(Path it) { return it != null && !Files.exists(it); }

      public Boolean doCall() {
        return doCall(null);
      }
    });
    if (!nonExistingFiles.isEmpty()) {
      buildContext.getMessages().error(fieldName + " contains non-existing path" + nonExistingFiles.size().compareTo(1) > 0
                                       ? "s"
                                       : "" +
                                         ": " +
                                         String.join(",", DefaultGroovyMethods.collect(nonExistingFiles, new Closure<String>(this, this) {
                                           public String doCall(Path it) { return it.toString(); }

                                           public String doCall() {
                                             return doCall(null);
                                           }
                                         })));
    }
  }

  private void checkMandatoryField(String value, String fieldName) {
    if (value == null) {
      buildContext.getMessages().error("Mandatory property \'" + fieldName + "\' is not specified");
    }
  }

  private void checkMandatoryPath(String path, String fieldName) {
    checkMandatoryField(path, fieldName);
    checkPaths(new ArrayList<String>(Arrays.asList(path)), fieldName);
  }

  @Override
  public void compileProjectAndTests(List<String> includingTestsInModules) {
    compileModules(null, includingTestsInModules);
  }

  @Override
  public void compileProjectAndTests() {
    compileProjectAndTests(new ArrayList<String>());
  }

  @Override
  public void compileModules(Collection<String> moduleNames, List<String> includingTestsInModules) {
    CompilationTasks.create(buildContext).compileModules(moduleNames, includingTestsInModules);
  }

  @Override
  public void compileModules(Collection<String> moduleNames) {
    compileModules(moduleNames, List.of());
  }

  public static void runInParallel(List<BuildTaskRunnable> tasks, final BuildContext buildContext) {
    if (tasks.isEmpty()) {
      return;
    }


    if (!buildContext.getOptions().runBuildStepsInParallel) {
      DefaultGroovyMethods.collect(tasks, new Closure<Void>(null, null) {
        public void doCall(BuildTaskRunnable it) {
          it.getTask().accept(buildContext);
        }

        public void doCall() {
          doCall(null);
        }
      });
      return;
    }


    Span span = TracerManager.spanBuilder("run tasks in parallel")
      .setAttribute(AttributeKey.stringArrayKey("tasks"), DefaultGroovyMethods.collect(tasks, new Closure<String>(null, null) {
        public String doCall(BuildTaskRunnable it) { return it.getStepId(); }

        public String doCall() {
          return doCall(null);
        }
      })).setAttribute("taskCount", tasks.size()).startSpan();
    Context traceContext = Context.current().with(span);
    try {
      List<ForkJoinTask<?>> futures = new ArrayList<ForkJoinTask<?>>(tasks.size());
      for (BuildTaskRunnable task : tasks) {
        ForkJoinTask<?> forkJoinTask = createTaskWrapper(task, buildContext.forkForParallelTask(task.getStepId()), traceContext);
        if (forkJoinTask != null) {
          futures.add(forkJoinTask.fork());
        }
      }


      List<Throwable> errors = new ArrayList<Throwable>();

      // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
      for (int i = futures.size() - 1; ; i >= 0 ;){
        ForkJoinTask<?> task = futures.get(i);
        try {
          task.join();
        }
        catch (Throwable e) {
          errors.add(e instanceof UndeclaredThrowableException ? e.getCause() : e);
        }
      }


      if (!errors.isEmpty()) {
        Span.current().setStatus(StatusCode.ERROR);
        if (errors.size() == 1) {
          buildContext.getMessages().error(errors.get(0).getMessage(), errors.get(0));
        }
        else {
          buildContext.getMessages().error("Some tasks failed", new CompoundRuntimeException(errors));
        }
      }
    }
    catch (Throwable e) {
      span.recordException(e);
      span.setStatus(StatusCode.ERROR);
      throw e;
    }
    finally {
      span.end();
      buildContext.getMessages().onAllForksFinished();
    }
  }

  @Nullable
  private static ForkJoinTask<?> createTaskWrapper(final BuildTaskRunnable task,
                                                   final BuildContext buildContext,
                                                   final Context traceContext) {
    if (buildContext.getOptions().buildStepsToSkip.contains(task.getStepId())) {
      Span span = TracerManager.spanBuilder(task.getStepId()).setParent(traceContext).startSpan();
      span.addEvent("skip");
      span.end();
      return null;
    }


    return ForkJoinTask.adapt(new Runnable() {
      @Override
      public void run() throws Exception {
        Span span = TracerManager.spanBuilder(task.getStepId()).setParent(traceContext).startSpan();
        Scope scope = span.makeCurrent();
        buildContext.getMessages().onForkStarted();
        try {
          if (buildContext.getOptions().buildStepsToSkip.contains(task.getStepId())) {
            span.addEvent("skip");
            null;
          }
          else {
            task.getTask().accept(buildContext);
          }
        }
        catch (Throwable e) {
          span.recordException(e);
          span.setStatus(StatusCode.ERROR);
          throw e;
        }
        finally {
          span.end();
          scope.close();
          buildContext.getMessages().onForkFinished();
        }
      }
    });
  }

  @Override
  public void buildUpdaterJar() {
    doBuildUpdaterJar("updater.jar");
  }

  @Override
  public void buildFullUpdaterJar() {
    doBuildUpdaterJar("updater-full.jar");
  }

  private void doBuildUpdaterJar(String artifactName) {
    String updaterModuleName = "intellij.platform.updater";
    JpsModule updaterModule = buildContext.findRequiredModule(updaterModuleName);
    Source updaterModuleSource = new DirSource(buildContext.getModuleOutputDir(updaterModule), new ArrayList<PathMatcher>(), null);

    List<Source> librarySources = DefaultGroovyMethods.collect(
      DefaultGroovyMethods.collectMany(JpsJavaExtensionService.dependencies(updaterModule).productionOnly().runtimeOnly().getLibraries(),
                                       new Closure<List<File>>(this, this) {
                                         public List<File> doCall(JpsLibrary it) { return it.getFiles(JpsOrderRootType.COMPILED); }

                                         public List<File> doCall() {
                                           return doCall(null);
                                         }
                                       }), new Closure<Source>(this, this) {
        public Source doCall(File zipFile) { return (Source)new ZipSource(zipFile.toPath(), List.of(new Regex("^META-INF/.*")), null); }
      });

    Path updaterJar = buildContext.getPaths().getArtifactDir().resolve(artifactName);
    JarBuilder.buildJar(updaterJar, DefaultGroovyMethods.plus((List<Source>)List.of((DirSource)updaterModuleSource), librarySources), true);

    buildContext.notifyArtifactBuilt(updaterJar);
  }

  @Override
  public void runTestBuild() {
    checkProductProperties();

    BuildContext context = buildContext;

    DistributionJARsBuilder distributionJARsBuilder = compileModulesForDistribution(context);
    ProjectStructureMapping projectStructureMapping = distributionJARsBuilder.buildJARs(context);
    layoutShared(context);
    Map<String, String> checkerConfig = context.getProductProperties().getVersionCheckerConfig();
    if (checkerConfig != null) {
      ClassVersionChecker.checkVersions(checkerConfig, context, context.getPaths().getDistAllDir());
    }


    if (context.getProductProperties().getBuildSourcesArchive()) {
      DistributionJARsBuilder.buildSourcesArchive(projectStructureMapping, context);
    }

    buildOsSpecificDistributions(buildContext);
  }

  @Override
  public void buildUnpackedDistribution(@NotNull Path targetDirectory, boolean includeBinAndRuntime) {
    BuildContext buildContext = buildContext;
    OsFamily currentOs = OsFamily.currentOs;

    buildContext.getPaths().setDistAllDir(targetDirectory.toAbsolutePath().normalize());
    buildContext.getOptions().targetOS = currentOs.osId;
    buildContext.getOptions().buildStepsToSkip.add(BuildOptions.GENERATE_JAR_ORDER_STEP);

    BundledMavenDownloader.downloadMavenCommonLibs(buildContext.getPaths().getBuildDependenciesCommunityRoot());
    BundledMavenDownloader.downloadMavenDistribution(buildContext.getPaths().getBuildDependenciesCommunityRoot());

    compileModulesForDistribution(buildContext).buildJARs(buildContext, true);
    JvmArchitecture arch = CpuArch.isArm64() ? JvmArchitecture.aarch64 : JvmArchitecture.x64;
    layoutShared(buildContext);

    if (includeBinAndRuntime) {
      Path propertiesFile = patchIdeaPropertiesFile(buildContext);
      OsSpecificDistributionBuilder builder = buildContext.getOsDistributionBuilder(currentOs, propertiesFile);
      builder.copyFilesForOsDistribution(targetDirectory, arch);
      buildContext.getBundledRuntime()
        .extractTo(BundledRuntimeImpl.getProductPrefix(buildContext), currentOs, targetDirectory.resolve("jbr"), arch);

      List<String> executableFilesPatterns = builder.generateExecutableFilesPatterns(true);
      updateExecutablePermissions(targetDirectory, executableFilesPatterns);
      buildContext.getBundledRuntime().checkExecutablePermissions(targetDirectory, "", currentOs);
    }
    else {
      copyDistFiles(buildContext, targetDirectory);
      unpackPty4jNative(buildContext, targetDirectory, null);
    }
  }

  public static void updateExecutablePermissions(final Path destinationDir, List<String> executableFilesPatterns) {
    final Set<PosixFilePermission> executable =
      EnumSet.of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE, GROUP_READ, GROUP_EXECUTE, OTHERS_READ, OTHERS_EXECUTE);
    final Set<PosixFilePermission> regular = EnumSet.of(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ);
    final List<PathMatcher> executableFilesMatchers =
      DefaultGroovyMethods.collect(executableFilesPatterns, new Closure<PathMatcher>(null, null) {
        public PathMatcher doCall(String it) {
          return FileSystems.getDefault().getPathMatcher("glob:" + it);
        }

        public PathMatcher doCall() {
          return doCall(null);
        }
      });
    IOGroovyMethods.withCloseable(Files.walk(destinationDir), new Closure<Void>(null, null) {
      public void doCall(Object stream) {
        ((Stream<Path>)stream).filter(new Closure<Object>(null, null) {
          public Object doCall(Path it) { return !Files.isDirectory(it); }

          public Object doCall() {
            return doCall(null);
          }
        }).forEach(new Closure<Object>(null, null) {
          public Object doCall(Object file) {
            if (SystemInfoRt.isUnix) {
              final Path relativeFile = destinationDir.relativize((Path)file);
              boolean isExecutable = OWNER_EXECUTE in java.nio.file.Files.getPosixFilePermissions((Path)file) ||
                                                           DefaultGroovyMethods.any(executableFilesMatchers,
                                                                                    new Closure<Boolean>(null, null) {
                                                                                      public Boolean doCall(PathMatcher it) {
                                                                                        return it.matches(relativeFile);
                                                                                      }

                                                                                      public Boolean doCall() {
                                                                                        return doCall(null);
                                                                                      }
                                                                                    });
              return Files.setPosixFilePermissions((Path)file, isExecutable ? executable : regular);
            }
            else {
              ((DosFileAttributeView)Files.getFileAttributeView((Path)file, DosFileAttributeView.class)).setReadOnly(false);
            }
          }
        });
      }
    });
  }

  public static void copyDistFiles(@NotNull BuildContext buildContext, @NotNull Path newDir) {
    Files.createDirectories(newDir);
    for (Map.Entry<Path, String> item : buildContext.getDistFiles()) {
      Path file = item.getKey();
      Path dir = newDir.resolve(item.getValue());
      Files.createDirectories(dir);
      Files.copy(file, dir.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public static void copyInspectScript(@NotNull BuildContext buildContext, @NotNull Path distBinDir) {
    final String inspectScript = buildContext.getProductProperties().getInspectCommandName();
    if (!inspectScript.equals("inspect")) {
      Path targetPath = distBinDir.resolve(inspectScript + ".sh");
      Files.move(distBinDir.resolve("inspect.sh"), targetPath, StandardCopyOption.REPLACE_EXISTING);
      buildContext.patchInspectScript(targetPath);
    }
  }

  public final BuildContext getBuildContext() {
    return buildContext;
  }

  private final BuildContext buildContext;

  {
    LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>(2);
    map.put("os", getProperty("OsFamily").MACOS);
    map.put("arch", getProperty("JvmArchitecture").x64);
    LinkedHashMap<String, Object> map1 = new LinkedHashMap<String, Object>(2);
    map1.put("os", getProperty("OsFamily").MACOS);
    map1.put("arch", getProperty("JvmArchitecture").aarch64);
    LinkedHashMap<String, Object> map2 = new LinkedHashMap<String, Object>(2);
    map2.put("os", getProperty("OsFamily").WINDOWS);
    map2.put("arch", getProperty("JvmArchitecture").x64);
    LinkedHashMap<String, Object> map3 = new LinkedHashMap<String, Object>(2);
    map3.put("os", getProperty("OsFamily").LINUX);
    map3.put("arch", getProperty("JvmArchitecture").x64);
    SUPPORTED_DISTRIBUTIONS = new ArrayList<SupportedDistribution>(
      Arrays.asList(new SupportedDistribution(map), new SupportedDistribution(map1), new SupportedDistribution(map2),
                    new SupportedDistribution(map3)));
  }

  private static List<SupportedDistribution> SUPPORTED_DISTRIBUTIONS;

  final private static class SupportedDistribution {
    public final OsFamily getOs() {
      return os;
    }

    public final JvmArchitecture getArch() {
      return arch;
    }

    public SupportedDistribution(OsFamily os, JvmArchitecture arch) { }

    public SupportedDistribution(Map args) { }

    public SupportedDistribution() { }

    private final OsFamily os;
    private final JvmArchitecture arch;
  }
}
