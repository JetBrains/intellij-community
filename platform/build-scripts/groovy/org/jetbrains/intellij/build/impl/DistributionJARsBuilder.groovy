// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.Compressor
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlin.Triple
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.resources.FileProvider
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.fus.StatisticsRecorderBundledMetadataProvider
import org.jetbrains.intellij.build.impl.projectStructureMapping.*
import org.jetbrains.jps.model.JpsCompositeElement
import org.jetbrains.jps.model.JpsElementReference
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement
import org.jetbrains.jps.model.artifact.elements.JpsLibraryFilesPackagingElement
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement
import org.jetbrains.jps.model.java.*
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil

import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.concurrent.Callable
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.function.*
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.jetbrains.intellij.build.impl.TracerManager.spanBuilder
/**
 * Assembles output of modules to platform JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAllDir}/lib directory),
 * bundled plugins' JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAllDir distAll}/plugins directory) and zip archives with
 * non-bundled plugins (in {@link org.jetbrains.intellij.build.BuildPaths#artifacts artifacts}/plugins directory).
 */
@CompileStatic
final class DistributionJARsBuilder {
  /**
   * Path to file with third party libraries HTML content,
   * see the same constant at com.intellij.ide.actions.AboutPopup#THIRD_PARTY_LIBRARIES_FILE_PATH
   */
  private static final String THIRD_PARTY_LIBRARIES_FILE_PATH = "license/third-party-libraries.html"
  private static final String PLUGINS_DIRECTORY = "plugins"

  final PlatformLayout platform
  private final Set<PluginLayout> pluginsToPublish
  private final PluginXmlPatcher pluginXmlPatcher

  @CompileStatic(TypeCheckingMode.SKIP)
  DistributionJARsBuilder(BuildContext context, Set<PluginLayout> pluginsToPublish = Collections.emptySet()) {
    this.pluginsToPublish = filterPluginsToPublish(pluginsToPublish, context)

    String releaseDate = context.applicationInfo.majorReleaseDate
    if (releaseDate.startsWith('__')) {
      context.messages.error("Unresolved release-date: $releaseDate")
    }
    String releaseVersion = "${context.applicationInfo.majorVersion}${context.applicationInfo.minorVersionMainPart}00"
    pluginXmlPatcher = new PluginXmlPatcher(releaseDate, releaseVersion)

    platform = createPlatformLayout(pluginsToPublish, context)
  }

  static PlatformLayout createPlatformLayout(Set<PluginLayout> pluginsToPublish, BuildContext context) {
    ProductModulesLayout productLayout = context.productProperties.productLayout
    Set<String> enabledPluginModules = getEnabledPluginModules(pluginsToPublish, context.productProperties)
    Set<JpsLibrary> projectLibrariesUsedByPlugins = computeProjectLibsUsedByPlugins(context, enabledPluginModules)
    return PlatformModules.createPlatformLayout(productLayout,
                                                hasPlatformCoverage(productLayout, enabledPluginModules, context),
                                                projectLibrariesUsedByPlugins,
                                                context)
  }

  private static boolean hasPlatformCoverage(ProductModulesLayout productLayout,
                                             Set<String> enabledPluginModules,
                                             BuildContext context) {
    return Stream.concat(productLayout.getIncludedPluginModules(enabledPluginModules).stream(),
                         productLayout.getIncludedPluginModules(enabledPluginModules).stream())
      .flatMap(new Function<String, Stream<? extends String>>() {
        @Override
        Stream<? extends String> apply(String moduleName) {
          return JpsJavaExtensionService.dependencies(context.findRequiredModule(moduleName))
            .productionOnly()
            .getModules()
            .stream()
            .map(new Function<JpsModule, String>() {
              @Override
              String apply(JpsModule module) {
                return module.name
              }
            })
        }
      })
      .filter(Predicate.isEqual("intellij.platform.coverage"))
      .findAny()
      .isPresent()
  }

  private static Set<JpsLibrary> computeProjectLibsUsedByPlugins(BuildContext context, Set<String> enabledPluginModules) {
    context.messages.debug("Collecting project libraries used by plugins:")

    Collection<JpsLibrary> result = new LinkedHashSet<>()
    MultiMap<JpsLibrary, JpsModule> libraries = MultiMap.createLinked()
    for (PluginLayout plugin : getPluginsByModules(context, enabledPluginModules)) {
      libraries.clear()
      collectProjectLibrariesWhichShouldBeProvidedByPlatform(plugin, libraries, context)
      for (Map.Entry<JpsLibrary, Collection<JpsModule>> entry in libraries.entrySet()) {
        context.messages.debug("plugin '$plugin.mainModule', library '$entry.key.name': " +
                               "used in ${String.join(", ", entry.value.collect { "'$it.name'" })}")
      }
      result.addAll(libraries.keySet())
    }
    return result
  }

  static MultiMap<JpsLibrary, JpsModule> collectProjectLibrariesWhichShouldBeProvidedByPlatform(BaseLayout plugin,
                                                                                                MultiMap<JpsLibrary, JpsModule> result,
                                                                                                BuildContext buildContext) {
    Collection<String> libsToUnpack = plugin.projectLibrariesToUnpack.values()
    for (String moduleName in plugin.includedModuleNames) {
      JpsModule module = buildContext.findRequiredModule(moduleName)
      JpsJavaDependenciesEnumerator dependencies = JpsJavaExtensionService.dependencies(module)
      for (JpsLibrary library : dependencies.includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries) {
        if (!(library.createReference().parentReference instanceof JpsModuleReference) && !plugin.includedProjectLibraries.any {
          it.libraryName == library.name
        } && !libsToUnpack.contains(library.name)) {
          result.putValue(library, module)
        }
      }
    }
    return result
  }

  private static @NotNull Set<PluginLayout> filterPluginsToPublish(@NotNull Set<PluginLayout> plugins, @NotNull BuildContext context) {
    plugins = plugins.stream().filter {
      // Kotlin Multiplatform Mobile plugin is excluded since:
      // * is compatible with Android Studio only;
      // * has release cycle of its
      // * shadows IntelliJ utility modules included via Kotlin Compiler;
      // * breaks searchable options index and jar order generation steps.
      it.mainModule != 'kotlin-ultimate.kmm-plugin'
    }.collect(Collectors.toSet())
    if (plugins.isEmpty()) {
      return plugins
    }

    Set<String> toInclude = new HashSet<>(context.options.nonBundledPluginDirectoriesToInclude)
    if (toInclude.isEmpty()) {
      return plugins
    }
    if (toInclude.size() == 1 && toInclude.contains("none")) {
      return new LinkedHashSet<PluginLayout>()
    }
    return plugins.findAll { toInclude.contains(it.directoryName) }
  }

  private static Set<String> getEnabledPluginModules(Set<PluginLayout> pluginsToPublish, ProductProperties productProperties) {
    Set<String> result = new LinkedHashSet<>()
    result.addAll(productProperties.productLayout.bundledPluginModules)
    pluginsToPublish.collect(result) { it.mainModule }
    return result
  }

  List<String> getPlatformModules() {
    return (platform.includedModuleNames as List<String>) + toolModules
  }

  static List<String> getIncludedPlatformModules(ProductModulesLayout modulesLayout) {
    return PlatformModules.PLATFORM_API_MODULES + PlatformModules.PLATFORM_IMPLEMENTATION_MODULES + modulesLayout.productApiModules +
           modulesLayout.productImplementationModules + modulesLayout.additionalPlatformJars.values()
  }

  /**
   * @return module names which are required to run necessary tools from build scripts
   */
  static List<String> getToolModules() {
    return List.of("intellij.java.rt", "intellij.platform.main", /*required to build searchable options index*/ "intellij.platform.updater")
  }

  Set<String> getIncludedProjectArtifacts(BuildContext context) {
    Set<String> result = new LinkedHashSet<String>()
    result.addAll(platform.includedArtifacts.keySet())

    getPluginsByModules(context, getEnabledPluginModules(pluginsToPublish, context.productProperties))
      .collectMany(result) { it.includedArtifacts.keySet() }
    return result
  }

  @NotNull
  ProjectStructureMapping buildJARs(BuildContext context, boolean isUpdateFromSources = false) {
    validateModuleStructure(context)

    ForkJoinTask<?> svgPrebuildTask = SVGPreBuilder.createPrebuildSvgIconsTask(context)?.fork()
    ForkJoinTask<?> brokenPluginsTask = createBuildBrokenPluginListTask(context)?.fork()

    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.createSkippableTask(spanBuilder("build searchable options index"),
                                    BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
                                    context) {
      buildSearchableOptions(context, getModulesForPluginsToPublish())
    }?.fork()?.join()

    Set<PluginLayout> pluginLayouts = getPluginsByModules(context, context.productProperties.productLayout.bundledPluginModules)

    Path antDir = context.productProperties.isAntRequired ? context.paths.distAllDir.resolve("lib/ant") : null
    Path antTargetFile = antDir == null ? null : antDir.resolve("lib/ant.jar")

    ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher()
    ForkJoinTask<List<DistributionFileEntry>> buildPlatformTask =
      buildHelper.createTask(spanBuilder("build platform lib"), new Supplier<List<DistributionFileEntry>>() {
        @Override
        List<DistributionFileEntry> get() {
          List<ForkJoinTask<?>> tasks = new ArrayList<>()
          ForkJoinTask<?> task = StatisticsRecorderBundledMetadataProvider.createTask(moduleOutputPatcher, context)
          if (task != null) {
            tasks.add(task)
          }

          ForkJoinTask.invokeAll(Arrays.asList(
            StatisticsRecorderBundledMetadataProvider.createTask(moduleOutputPatcher, context),
            buildHelper.createTask(spanBuilder("write patched app info")) {
              Path moduleOutDir = context.getModuleOutputDir(context.findRequiredModule("intellij.platform.core"))
              String relativePath = "com/intellij/openapi/application/ApplicationNamesInfo.class"
              byte[] result = buildHelper.setAppInfo.invokeWithArguments(moduleOutDir.resolve(relativePath),
                                                                         context.applicationInfo?.getAppInfoXml()) as byte[]
              moduleOutputPatcher.patchModuleOutput("intellij.platform.core", relativePath, result)
              return null
            },
            ).findAll { it != null })

          List<DistributionFileEntry> result = buildLib(moduleOutputPatcher, platform, context)
          if (!isUpdateFromSources && context.productProperties.scrambleMainJar) {
            scramble(context)
          }

          context.bootClassPathJarNames = (List<String>)buildHelper.generateClasspath
            .invokeWithArguments(context.paths.distAllDir,
                                 context.productProperties.productLayout.mainJarName,
                                 antTargetFile)
          return result
        }
      })
    List<DistributionFileEntry> entries = ForkJoinTask.invokeAll(Arrays.asList(
      buildPlatformTask,
      createBuildBundledPluginTask(pluginLayouts, buildPlatformTask, context),
      createBuildOsSpecificBundledPluginsTask(pluginLayouts, isUpdateFromSources, buildPlatformTask, context),
      createBuildNonBundledPluginsTask(!isUpdateFromSources, buildPlatformTask, context),
      ForkJoinTask.adapt(new Callable<List<DistributionFileEntry>>() {
        @Override
        List<DistributionFileEntry> call() throws Exception {
          if (antDir == null) {
            return Collections.emptyList()
          }

          List sources = new ArrayList<>()
          BiFunction<Path, IntConsumer, ?> createZipSource = buildHelper.createZipSource
          List<DistributionFileEntry> result = new ArrayList<>()
          buildHelper.copyDir(context.paths.communityHomeDir.resolve("lib/ant"), antDir,
                              new Predicate<Path>() {
                                @Override
                                boolean test(Path path) {
                                  return !path.endsWith("src")
                                }
                              },
                              new Predicate<Path>() {
                                @Override
                                boolean test(Path file) {
                                  if (!file.toString().endsWith(".jar")) {
                                    return true
                                  }

                                  sources.add(createZipSource.apply(file, new IntConsumer() {
                                    @Override
                                    void accept(int size) {
                                      result.add(new ProjectLibraryEntry(antTargetFile, "Ant", file, size))
                                    }
                                  }))
                                  return false
                                }
                              })

          // path in class log - empty, do not reorder, doesn't matter
          sources.sort(null)
          buildHelper.buildJars.accept(List.of(new Triple(antTargetFile, "", sources)), false)
          return result
        }
      })
      ).findAll { it != null })
      .collectMany {
        Object result = it.rawResult
        return (List<DistributionFileEntry>)(result instanceof List<?>
          ? (List<DistributionFileEntry>)result
          : Collections.<List<DistributionFileEntry>> emptyList())
      }

    // must be before reorderJars as these additional plugins maybe required for IDE start-up
    List<Path> additionalPluginPaths = context.productProperties.getAdditionalPluginPaths(context)
    if (!additionalPluginPaths.isEmpty()) {
      Path pluginDir = context.paths.distAllDir.resolve("plugins")
      for (Path sourceDir : additionalPluginPaths) {
        buildHelper.copyDir(sourceDir, pluginDir.resolve(sourceDir.fileName))
      }
    }

    List<ForkJoinTask<?>> tasks = new ArrayList<ForkJoinTask<?>>(3)
    tasks.add(buildHelper.createTask(spanBuilder("generate content report"), new Supplier<Void>() {
      @Override
      Void get() {
        Path artifactOut = Path.of(context.paths.artifacts)
        Files.createDirectories(artifactOut)
        ProjectStructureMapping.writeReport(entries, artifactOut.resolve("content-mapping.json"), context.paths)
        Files.newOutputStream(artifactOut.resolve("content.json")).withCloseable {
          ProjectStructureMapping.buildJarContentReport(entries, it, context.paths)
        }
      }
    }))

    ProjectStructureMapping projectStructureMapping = new ProjectStructureMapping(entries)
    ForkJoinTask<?> task = buildThirdPartyLibrariesList(projectStructureMapping, context)
    if (task != null) {
      tasks.add(task)
    }
    ForkJoinTask.invokeAll(tasks)

    // inversed order of join - better for FJP (https://shipilev.net/talks/jeeconf-May2012-forkjoin.pdf, slide 32)
    brokenPluginsTask?.join()
    svgPrebuildTask?.join()

    return projectStructureMapping
  }

  private static void scramble(BuildContext context) {
    JarPackager.pack(Map.of("internalUtilities.jar", List.of("intellij.tools.internalUtilities")),
                     context.paths.buildOutputDir.resolve("internal"),
                     context)

    ScrambleTool tool = context.proprietaryBuildTools.scrambleTool
    if (tool == null) {
      Span.current().addEvent("skip scrambling because `scrambleTool` isn't defined")
    }
    else {
      tool.scramble(context.productProperties.productLayout.mainJarName, context)
    }

    // e.g. JetBrainsGateway doesn't have a main jar with license code
    if (Files.exists(context.paths.distAllDir.resolve("lib/${context.productProperties.productLayout.mainJarName}"))) {
      packInternalUtilities(context)
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileStatic(TypeCheckingMode.SKIP)
  private static void packInternalUtilities(BuildContext context) {
    context.ant.zip(destfile: "$context.paths.artifacts/internalUtilities.zip") {
      fileset(file: "$context.paths.buildOutputRoot/internal/internalUtilities.jar")
      context.project.libraryCollection.findLibrary("JUnit4").getFiles(JpsOrderRootType.COMPILED).each {
        fileset(file: it.absolutePath)
      }
      zipfileset(src: "$context.paths.buildOutputRoot/internal/internalUtilities.jar") {
        include(name: "*.xml")
      }
    }
  }

  @Nullable
  private static ForkJoinTask<?> createBuildBrokenPluginListTask(@NotNull BuildContext context) {
    String buildString = context.buildNumber
    Path targetFile = context.paths.tempDir.resolve("brokenPlugins.db")
    BuildHelper helper = BuildHelper.getInstance(context)
    return helper.createSkippableTask(
      spanBuilder("build broken plugin list")
        .setAttribute("buildString", buildString)
        .setAttribute("path", targetFile.toString()),
      BuildOptions.BROKEN_PLUGINS_LIST_STEP,
      context,
      new Runnable() {
        @Override
        void run() {
          helper.brokenPluginsTask.invokeWithArguments(targetFile, buildString, context.options.isInDevelopmentMode)
          if (Files.exists(targetFile)) {
            context.addDistFile(Map.entry(targetFile, "bin"))
          }
        }
      }
    )
  }

  /**
   * Validates module structure to be ensure all module dependencies are included
   */
  @CompileStatic
  void validateModuleStructure(BuildContext context) {
    if (context.options.validateModuleStructure) {
      new ModuleStructureValidator(context, platform.moduleJars).validate()
    }
  }

  @CompileStatic
  List<String> getProductModules() {
    List<String> result = new ArrayList<>()
    for (moduleJar in platform.jarToIncludedModuleNames) {
      // Filter out jars with relative paths in name
      if (moduleJar.key.contains("\\") || moduleJar.key.contains("/")) {
        continue
      }

      result.addAll(moduleJar.value)
    }
    return result
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  @Nullable
  static Path buildSearchableOptions(BuildContext buildContext,
                                     @NotNull Collection<String> modulesForPluginsToPublish,
                                     @Nullable UnaryOperator<Set<String>> classpathCustomizer = null,
                                     Map<String, Object> systemProperties = Collections.emptyMap()) {
    Span span = Span.current()
    if (buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      span.addEvent("skip building searchable options index")
      return null
    }

    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    Set<String> modulesToIndex = new LinkedHashSet<>()
    modulesToIndex.addAll(productLayout.mainModules)
    modulesToIndex.addAll(getModulesToCompile(buildContext))
    modulesToIndex.addAll(modulesForPluginsToPublish)
    modulesToIndex.remove("intellij.ruby.lsp")

    Path targetDirectory = JarPackager.getSearchableOptionsDir(buildContext)
    BuildMessages messages = buildContext.messages
    span.setAttribute(AttributeKey.longKey("moduleCount"), (long)modulesToIndex.size())
    span.setAttribute(AttributeKey.stringArrayKey("modules"), List.copyOf(modulesToIndex))
    NioFiles.deleteRecursively(targetDirectory)
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in Settings dialog and build index for them.
    //noinspection SpellCheckingInspection
    BuildHelper.runApplicationStarter(buildContext,
                                      buildContext.paths.tempDir.resolve("searchableOptions"),
                                      modulesToIndex, List.of("traverseUI", targetDirectory.toString(), "true"),
                                      systemProperties,
                                      List.of(),
                                      TimeUnit.MINUTES.toMillis(10L), classpathCustomizer)
    List<Path> modules = Files.newDirectoryStream(targetDirectory).withCloseable { it.asList() }
    if (modules.isEmpty()) {
      messages.error("Failed to build searchable options index: $targetDirectory is empty")
    }
    else {
      span.setAttribute(AttributeKey.longKey("moduleCountWithSearchableOptions"), (long)modules.size())
      span.setAttribute(AttributeKey.stringArrayKey("modulesWithSearchableOptions"),
                        modules.collect { targetDirectory.relativize(it).toString() })
    }
    return targetDirectory
  }

  static Set<String> getModulesToCompile(BuildContext buildContext) {
    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    Set<String> modulesToInclude = new LinkedHashSet<>()
    modulesToInclude.addAll(productLayout.getIncludedPluginModules(Set.copyOf(productLayout.bundledPluginModules)))
    modulesToInclude.addAll(PlatformModules.PLATFORM_API_MODULES)
    modulesToInclude.addAll(PlatformModules.PLATFORM_IMPLEMENTATION_MODULES)
    modulesToInclude.addAll(productLayout.productApiModules)
    modulesToInclude.addAll(productLayout.productImplementationModules)
    modulesToInclude.addAll(productLayout.additionalPlatformJars.values())
    modulesToInclude.addAll(toolModules)
    modulesToInclude.addAll(buildContext.productProperties.additionalModulesToCompile)
    modulesToInclude.add("intellij.idea.community.build.tasks")
    modulesToInclude.add("intellij.platform.images.build")
    modulesToInclude.removeAll(productLayout.excludedModuleNames)
    return modulesToInclude
  }

  Set<String> getModulesForPluginsToPublish() {
    Set<String> result = new LinkedHashSet<String>()
    result.addAll(platformModules)
    pluginsToPublish.collectMany(result) { it.includedModuleNames }
    return result
  }

  static void buildAdditionalArtifacts(BuildContext buildContext, ProjectStructureMapping projectStructureMapping) {
    ProductProperties productProperties = buildContext.productProperties

    if (productProperties.generateLibrariesLicensesTable &&
        !buildContext.options.buildStepsToSkip.contains(BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP)) {
      String artifactNamePrefix = productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      Path artifactDir = Path.of(buildContext.paths.artifacts)
      Files.createDirectories(artifactDir)
      Files.copy(getThirdPartyLibrariesHtmlFilePath(buildContext), artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.html"))
      Files.copy(getThirdPartyLibrariesJsonFilePath(buildContext), artifactDir.resolve(artifactNamePrefix + "-third-party-libraries.json"))
    }

    if (productProperties.buildSourcesArchive) {
      String archiveName = productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + "-sources.zip"
      def modulesFromCommunity = projectStructureMapping.includedModules.findAll { moduleName ->
        productProperties.includeIntoSourcesArchiveFilter.test(buildContext.findRequiredModule(moduleName), buildContext)
      }
      BuildTasks.create(buildContext).zipSourcesOfModules(modulesFromCommunity, Path.of(buildContext.paths.artifacts, archiveName), true)
    }
  }

  void generateProjectStructureMapping(@NotNull Path targetFile, @NotNull BuildContext context) {
    ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher()
    ForkJoinTask<List<DistributionFileEntry>> libDirLayout = processLibDirectoryLayout(moduleOutputPatcher, platform, context, false).fork()
    Set<PluginLayout> allPlugins = getPluginsByModules(context, context.productProperties.productLayout.bundledPluginModules)
    List<DistributionFileEntry> entries = new ArrayList<DistributionFileEntry>()
    allPlugins.stream()
      .filter(new Predicate<PluginLayout>() {
        @Override
        boolean test(PluginLayout plugin) {
          return satisfiesBundlingRequirements(plugin, null, context)
        }
      })
      .forEach(new Consumer<PluginLayout>() {
        @Override
        void accept(PluginLayout plugin) {
          entries.addAll(processLayout(plugin,
                                       context.paths.tempDir,
                                       false,
                                       moduleOutputPatcher,
                                       plugin.moduleJars,
                                       context).fork().join())
        }
      })
    entries.addAll(libDirLayout.join())

    ProjectStructureMapping.writeReport(entries, targetFile, context.paths)
  }

  @Nullable
  private static ForkJoinTask<?> buildThirdPartyLibrariesList(@NotNull ProjectStructureMapping projectStructureMapping,
                                                              @NotNull BuildContext context) {
    return BuildHelper.getInstance(context).createSkippableTask(
      spanBuilder("generate table of licenses for used third-party libraries"),
      BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP,
      context,
      new Runnable() {
        @Override
        void run() {
          LibraryLicensesListGenerator generator = LibraryLicensesListGenerator.create(context.project,
                                                                                       context.productProperties.allLibraryLicenses,
                                                                                       projectStructureMapping.includedModules)
          generator.generateHtml(getThirdPartyLibrariesHtmlFilePath(context))
          generator.generateJson(getThirdPartyLibrariesJsonFilePath(context))
        }
      }
    )
  }

  private static Path getThirdPartyLibrariesHtmlFilePath(@NotNull BuildContext buildContext) {
    return buildContext.paths.distAllDir.resolve(THIRD_PARTY_LIBRARIES_FILE_PATH)
  }

  private static Path getThirdPartyLibrariesJsonFilePath(@NotNull BuildContext buildContext) {
    return buildContext.paths.tempDir.resolve("third-party-libraries.json")
  }

  @NotNull
  static List<DistributionFileEntry> buildLib(ModuleOutputPatcher moduleOutputPatcher, PlatformLayout platform, BuildContext context) {
    patchKeyMapWithAltClickReassignedToMultipleCarets(moduleOutputPatcher, context)

    List<DistributionFileEntry> libDirMappings = processLibDirectoryLayout(moduleOutputPatcher, platform, context, true).fork().join()

    if (context.proprietaryBuildTools.scrambleTool != null) {
      Path libDir = context.paths.distAllDir.resolve("lib")
      for (String forbiddenJarName : context.proprietaryBuildTools.scrambleTool.namesOfJarsRequiredToBeScrambled) {
        if (Files.exists(libDir.resolve(forbiddenJarName))) {
          context.messages.error("The following JAR cannot be included into the product 'lib' directory," +
                                 " it need to be scrambled with the main jar: $forbiddenJarName")
        }
      }

      List<String> modulesToBeScrambled = context.proprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled
      ProductModulesLayout productLayout = context.productProperties.productLayout
      for (jarName in platform.moduleJars.keySet()) {
        if (jarName != productLayout.mainJarName && jarName != PlatformModules.PRODUCT_JAR) {
          Collection<String> notScrambled = platform.moduleJars.get(jarName).intersect(modulesToBeScrambled)
          if (!notScrambled.isEmpty()) {
            context.messages.error("Module '${notScrambled.first()}' is included into $jarName which is not scrambled.")
          }
        }
      }
    }
    return libDirMappings
  }

  static ForkJoinTask<List<DistributionFileEntry>> processLibDirectoryLayout(ModuleOutputPatcher moduleOutputPatcher,
                                                                             PlatformLayout platform,
                                                                             BuildContext context,
                                                                             boolean copyFiles) {
    return processLayout(platform,
                         context.paths.distAllDir,
                         copyFiles,
                         moduleOutputPatcher,
                         platform.moduleJars,
                         context)
  }

  ForkJoinTask<List<DistributionFileEntry>> createBuildBundledPluginTask(@NotNull Collection<PluginLayout> plugins,
                                                                         ForkJoinTask<?> buildPlatformTask,
                                                                         @NotNull BuildContext context) {
    Set<String> pluginDirectoriesToSkip = context.options.bundledPluginDirectoriesToSkip
    return BuildHelper.getInstance(context).createTask(
      spanBuilder("build bundled plugins")
        .setAttribute(AttributeKey.stringArrayKey("pluginDirectoriesToSkip"), List.copyOf(pluginDirectoriesToSkip))
        .setAttribute("count", plugins.size()),
      new Supplier<List<DistributionFileEntry>>() {
        @Override
        List<DistributionFileEntry> get() {
          List<PluginLayout> pluginsToBundle = new ArrayList<PluginLayout>(plugins.size())
          for (PluginLayout plugin : plugins) {
            if (satisfiesBundlingRequirements(plugin, null, context) && !pluginDirectoriesToSkip.contains(plugin.directoryName)) {
              pluginsToBundle.add(plugin)
            }
          }
          Span.current().setAttribute("satisfiableCount", pluginsToBundle.size())
          return buildPlugins(new ModuleOutputPatcher(), pluginsToBundle,
                              context.paths.distAllDir.resolve(PLUGINS_DIRECTORY), context, buildPlatformTask, null)
        }
      }
    )
  }

  private static boolean satisfiesBundlingRequirements(PluginLayout plugin, @Nullable OsFamily osFamily, @NotNull BuildContext context) {
    PluginBundlingRestrictions bundlingRestrictions = plugin.bundlingRestrictions
    if (bundlingRestrictions.includeInEapOnly && !context.applicationInfo.isEAP) {
      return false
    }
    return osFamily == null
      ? bundlingRestrictions.supportedOs == OsFamily.ALL
      : bundlingRestrictions.supportedOs != OsFamily.ALL && bundlingRestrictions.supportedOs.contains(osFamily)
  }

  private ForkJoinTask<List<DistributionFileEntry>> createBuildOsSpecificBundledPluginsTask(@NotNull Set<PluginLayout> pluginLayouts,
                                                                                            boolean isUpdateFromSources,
                                                                                            @Nullable ForkJoinTask<?> buildPlatformTask,
                                                                                            @NotNull BuildContext context) {
    BuildHelper buildHelper = BuildHelper.getInstance(context)
    buildHelper.createTask(spanBuilder("build os-specific bundled plugins")
                             .setAttribute("isUpdateFromSources", isUpdateFromSources), new Supplier<List<DistributionFileEntry>>() {
      @Override
      List<DistributionFileEntry> get() {
        return ForkJoinTask.invokeAll(OsFamily.values().findResults { osFamily ->
          if (!context.shouldBuildDistributionForOS(osFamily.osId)) {
            return null
          }

          List<PluginLayout> osSpecificPlugins = new ArrayList<PluginLayout>()
          for (PluginLayout pluginLayout : pluginLayouts) {
            if (satisfiesBundlingRequirements(pluginLayout, osFamily, context)) {
              osSpecificPlugins.add(pluginLayout)
            }
          }
          if (osSpecificPlugins.isEmpty()) {
            return null
          }

          Path outDir = isUpdateFromSources
            ? context.paths.distAllDir.resolve("plugins")
            : getOsSpecificDistDirectory(osFamily, context).resolve("plugins")

          return buildHelper.createTask(spanBuilder("build bundled plugins")
                                          .setAttribute("os", osFamily.osName)
                                          .setAttribute("count", osSpecificPlugins.size())
                                          .setAttribute("outDir", outDir.toString()), new Supplier<List<DistributionFileEntry>>() {
            @Override
            List<DistributionFileEntry> get() {
              return buildPlugins(new ModuleOutputPatcher(), osSpecificPlugins, outDir, context, buildPlatformTask, null)
            }
          })
        }).collectMany { it.rawResult }
      }
    })
  }

  static Path getOsSpecificDistDirectory(OsFamily osFamily, BuildContext buildContext) {
    return buildContext.paths.buildOutputDir.resolve("dist.${osFamily.distSuffix}")
  }

  /**
   * @return predicate to test if a given plugin should be auto-published
   */
  @NotNull
  private static Predicate<PluginLayout> loadPluginAutoPublishList(@NotNull BuildContext buildContext) {
    //noinspection SpellCheckingInspection
    String productCode = buildContext.applicationInfo.productCode
    Collection<String> config = Files.lines(buildContext.paths.communityHomeDir.resolve("../build/plugins-autoupload.txt"))
      .withCloseable { Stream<String> lines ->
        lines
          .map({ String line -> StringUtil.split(line, "//", true, false)[0] } as Function<String, String>)
          .map({ String line -> StringUtil.split(line, "#", true, false)[0] } as Function<String, String>)
          .map({ String line -> line.trim() } as Function<String, String>)
          .filter({ String line -> !line.isEmpty() } as Predicate<String>)
          .map({ String line -> line.toString() /*make sure there is no GString involved */} as Function<String, String>)
          .collect(Collectors.toCollection({ new TreeSet<String>(String.CASE_INSENSITIVE_ORDER) } as Supplier<Collection<String>>))
      }

    return new Predicate<PluginLayout>() {
      @Override
      boolean test(PluginLayout plugin) {
        if (plugin == null) {
          return false
        }

        //see the specification in the plugins-autoupload.txt. Supported rules:
        //   <plugin main module name> ## include the plugin
        //   +<product code>:<plugin main module name> ## include the plugin
        //   -<product code>:<plugin main module name> ## exclude the plugin

        String module = plugin.mainModule
        String excludeRule = "-${productCode}:${module}"
        String includeRule = "+${productCode}:${module}"
        if (config.contains(excludeRule)) {
          //the exclude rule is the most powerful
          return false
        }

        return config.contains(module) || config.contains(includeRule.toString())
      }
    }
  }

  // compressPluginArchive also means that blockmap for plugin archive will be built
  @Nullable
  ForkJoinTask<?> createBuildNonBundledPluginsTask(boolean compressPluginArchive,
                                                   @Nullable ForkJoinTask<?> buildPlatformLibTask,
                                                   @NotNull BuildContext context) {
    if (pluginsToPublish.isEmpty()) {
      return null
    }

    ProductModulesLayout productLayout = context.productProperties.productLayout
    return BuildHelper.getInstance(context).createSkippableTask(spanBuilder("build non-bundled plugins")
                                                                  .setAttribute("count", pluginsToPublish.size()),
                                                                BuildOptions.NON_BUNDLED_PLUGINS_STEP,
                                                                context, new Runnable() {
      @Override
      void run() {
        Path nonBundledPluginsArtifacts = Path.of(context.paths.artifacts, "${context.applicationInfo.productCode}-plugins")
        Path autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
        boolean prepareCustomPluginRepositoryForPublishedPlugins = productLayout.prepareCustomPluginRepositoryForPublishedPlugins

        ForkJoinTask<List<kotlin.Pair<Path, byte[]>>> buildKeymapPluginsTask = buildKeymapPlugins(autoUploadingDir, context).fork()

        ModuleOutputPatcher moduleOutputPatcher = new ModuleOutputPatcher()
        Path stageDir = context.paths.tempDir.resolve("non-bundled-plugins-" + context.applicationInfo.productCode)

        List<Map.Entry<String, Path>> dirToJar = new ArrayList<>()

        String defaultPluginVersion = context.buildNumber.endsWith(".SNAPSHOT")
          ? context.buildNumber + ".${PluginXmlPatcher.pluginDateFormat.format(ZonedDateTime.now())}"
          : context.buildNumber

        List<PluginRepositorySpec> pluginsToIncludeInCustomRepository = new ArrayList<PluginRepositorySpec>()
        Predicate<PluginLayout> autoPublishPluginChecker = loadPluginAutoPublishList(context)

        buildPlugins(moduleOutputPatcher, pluginsToPublish, stageDir, context, buildPlatformLibTask, new BiConsumer<PluginLayout, Path>() {
          @Override
          void accept(PluginLayout plugin, Path pluginDir) {
            Path targetDirectory = autoPublishPluginChecker.test(plugin) ? autoUploadingDir : nonBundledPluginsArtifacts
            String pluginDirName = pluginDir.getFileName().toString()

            Path moduleOutput = context.getModuleOutputDir(context.findRequiredModule(plugin.mainModule))
            Path pluginXmlPath = moduleOutput.resolve("META-INF/plugin.xml")

            String pluginVersion =
              Files.exists(pluginXmlPath) ? plugin.versionEvaluator.evaluate(pluginXmlPath, defaultPluginVersion, context) :
              defaultPluginVersion

            Path destFile = targetDirectory.resolve("$pluginDirName-${pluginVersion}.zip")
            if (prepareCustomPluginRepositoryForPublishedPlugins) {
              byte[] pluginXml = moduleOutputPatcher.getPatchedPluginXml(plugin.mainModule)
              pluginsToIncludeInCustomRepository.add(new PluginRepositorySpec(destFile, pluginXml))
            }
            dirToJar.add(Map.entry(pluginDirName, destFile))
          }
        })

        BuildHelper buildHelper = BuildHelper.getInstance(context)
        buildHelper.bulkZipWithPrefix(stageDir, dirToJar, compressPluginArchive)
        for (Map.Entry<String, Path> item : dirToJar) {
          context.notifyArtifactWasBuilt(item.value)
        }

        PluginLayout helpPlugin = BuiltInHelpPlugin.helpPlugin(context, defaultPluginVersion)
        if (helpPlugin != null) {
          PluginRepositorySpec spec = buildHelpPlugin(helpPlugin, stageDir, autoUploadingDir, moduleOutputPatcher, context)
          if (prepareCustomPluginRepositoryForPublishedPlugins) {
            pluginsToIncludeInCustomRepository.add(spec)
          }
        }

        if (prepareCustomPluginRepositoryForPublishedPlugins) {
          context.notifyArtifactWasBuilt(PluginRepositoryXmlGenerator.generate(pluginsToIncludeInCustomRepository,
                                                                               nonBundledPluginsArtifacts,
                                                                               context))

          List<PluginRepositorySpec> autoUploadingPlugins = pluginsToIncludeInCustomRepository
            .findAll { it.pluginZip.startsWith(autoUploadingDir) }
          context.notifyArtifactWasBuilt(PluginRepositoryXmlGenerator.generate(autoUploadingPlugins, autoUploadingDir, context))
        }

        for (kotlin.Pair<Path, byte[]> item in buildKeymapPluginsTask.join()) {
          context.notifyArtifactWasBuilt(item.first)
          if (prepareCustomPluginRepositoryForPublishedPlugins) {
            pluginsToIncludeInCustomRepository.add(new PluginRepositorySpec(item.first, item.second))
          }
        }
      }
    })
  }

  private static ForkJoinTask<List<kotlin.Pair<Path, byte[]>>> buildKeymapPlugins(Path targetDir, BuildContext context) {
    Path keymapDir = context.paths.communityHomeDir.resolve("platform/platform-resources/src/keymaps")
    return (ForkJoinTask<List<kotlin.Pair<Path, byte[]>>>)BuildHelper.getInstance(context)
      .buildKeymapPlugins.invokeWithArguments(context.buildNumber, targetDir, keymapDir)
  }

  private PluginRepositorySpec buildHelpPlugin(PluginLayout helpPlugin,
                                               Path pluginsToPublishDir,
                                               Path targetDir,
                                               ModuleOutputPatcher moduleOutputPatcher,
                                               BuildContext context) {
    String directory = getActualPluginDirectoryName(helpPlugin, context)
    Path destFile = targetDir.resolve(directory + ".zip")

    context.messages.block(spanBuilder("build help plugin").setAttribute("dir", directory), new Supplier<Void>() {
      @Override
      Void get() {
        buildPlugins(moduleOutputPatcher, List.of(helpPlugin), pluginsToPublishDir, context, null, null)
        BuildHelper.zipWithPrefix(context, destFile, List.of(pluginsToPublishDir.resolve(directory)), directory, true)
        return null
      }
    })

    context.notifyArtifactBuilt(destFile)
    return new PluginRepositorySpec(destFile, moduleOutputPatcher.getPatchedPluginXml(helpPlugin.mainModule))
  }

  /**
   * Returns name of directory in the product distribution where plugin will be placed. For plugins which use the main module name as the
   * directory name return the old module name to temporary keep layout of plugins unchanged.
   */
  static String getActualPluginDirectoryName(PluginLayout plugin, BuildContext context) {
    if (!plugin.directoryNameSetExplicitly && plugin.directoryName == BaseLayout.convertModuleNameToFileName(plugin.mainModule)
                                           && context.getOldModuleName(plugin.mainModule) != null) {
      return context.getOldModuleName(plugin.mainModule)
    }
    else {
      return plugin.directoryName
    }
  }

  static Set<PluginLayout> getPluginsByModules(BuildContext context, Collection<String> modules) {
    if (modules.isEmpty()) {
      return Collections.emptySet()
    }

    List<PluginLayout> allNonTrivialPlugins = context.productProperties.productLayout.allNonTrivialPlugins
    Map<String, List<PluginLayout>> nonTrivialPlugins = allNonTrivialPlugins.groupBy { it.mainModule }
    Set<PluginLayout> result = new ObjectLinkedOpenCustomHashSet<>(modules.size(), new Hash.Strategy<PluginLayout>() {
      @Override
      int hashCode(@Nullable PluginLayout layout) {
        if (layout == null) {
          return 0
        }

        int result = layout.mainModule.hashCode()
        result = 31 * result + layout.bundlingRestrictions.supportedOs.hashCode()
        return result
      }

      @Override
      boolean equals(@Nullable PluginLayout a, @Nullable PluginLayout b) {
        if (a.is(b)) {
          return true
        }
        if (a == null || b == null) {
          return false
        }
        return a.mainModule == b.mainModule && a.bundlingRestrictions.supportedOs == b.bundlingRestrictions.supportedOs
      }
    })
    for (String moduleName : modules) {
      List<PluginLayout> customLayouts = nonTrivialPlugins.get(moduleName)
      if (customLayouts == null) {
        String alternativeModuleName = context.findModule(moduleName)?.name
        if (alternativeModuleName != moduleName) {
          customLayouts = nonTrivialPlugins.get(alternativeModuleName)
        }
      }

      if (customLayouts == null) {
        if (!result.add(PluginLayout.simplePlugin(moduleName))) {
          throw new IllegalStateException("Plugin layout for module $moduleName is already added (duplicated module name?)")
        }
      }
      else {
        for (PluginLayout layout : customLayouts) {
          if (!result.add(layout)) {
            throw new IllegalStateException("Plugin layout for module $moduleName is already added (duplicated module name?)")
          }
        }
      }
    }
    return result
  }

  // pluginBuilt - for now it doesn't mean that scrambling is completed, so,
  // you must not do use plugin content as a final result in a consumer, only after this method will be finished.
  // It will be changed once will be safe to build plugins in parallel.
  @NotNull
  List<DistributionFileEntry> buildPlugins(ModuleOutputPatcher moduleOutputPatcher,
                                           Collection<PluginLayout> pluginsToInclude,
                                           Path targetDirectory,
                                           BuildContext context,
                                           @Nullable ForkJoinTask<?> buildPlatformTask,
                                           @Nullable BiConsumer<PluginLayout, Path> pluginBuilt) {
    List<DistributionFileEntry> entries = new ArrayList<>()

    ScrambleTool scrambleTool = context.proprietaryBuildTools.scrambleTool
    boolean isScramblingSkipped = context.options.buildStepsToSkip.contains(BuildOptions.SCRAMBLING_STEP)

    List<ForkJoinTask<?>> scrambleTasks = new ArrayList<>()

    for (PluginLayout plugin in pluginsToInclude) {
      boolean isHelpPlugin = "intellij.platform.builtInHelp" == plugin.mainModule
      if (!isHelpPlugin) {
        checkOutputOfPluginModules(plugin.mainModule, plugin.moduleJars, plugin.moduleExcludes, context)
        PluginXmlPatcher.patchPluginXml(moduleOutputPatcher, plugin, pluginsToPublish, pluginXmlPatcher,  context)
      }

      String directoryName = getActualPluginDirectoryName(plugin, context)
      Path pluginDir = targetDirectory.resolve(directoryName)

      List<DistributionFileEntry> result = processLayout(plugin,
                                                         pluginDir,
                                                         true,
                                                         moduleOutputPatcher,
                                                         plugin.moduleJars,
                                                         context).fork().join()
      entries.addAll(result)

      if (!plugin.pathsToScramble.isEmpty()) {
        Attributes attributes = Attributes.of(AttributeKey.stringKey("plugin"), directoryName)
        if (scrambleTool == null) {
          Span.current().addEvent("skip scrambling plugin because scrambleTool isn't defined, but plugin defines paths to be scrambled",
                                  attributes)
        }
        else if (isScramblingSkipped) {
          Span.current().addEvent("skip scrambling plugin because step is disabled", attributes)
        }
        else {
          ForkJoinTask<?> scrambleTask = scrambleTool.scramblePlugin(context, plugin, pluginDir, targetDirectory)
          if (scrambleTask != null) {
            // we can not start executing right now because the plugin can use other plugins in a scramble classpath
            scrambleTasks.add(scrambleTask)
          }
        }
      }

      if (pluginBuilt != null) {
        pluginBuilt.accept(plugin, pluginDir)
      }
    }

    if (!scrambleTasks.isEmpty()) {
      // scrambling can require classes from platform
      BuildHelper.getInstance(context).span(spanBuilder("wait for platform lib for scrambling"), new Runnable() {
        @Override
        void run() {
          buildPlatformTask?.join()
        }
      })
      BuildHelper.invokeAllSettled(scrambleTasks)
    }
    return entries
  }

  static void checkOutputOfPluginModules(@NotNull String mainPluginModule,
                                         MultiMap<String, String> moduleJars,
                                         MultiMap<String, String> moduleExcludes,
                                         @NotNull BuildContext buildContext) {
    // don't check modules which are not direct children of lib/ directory
    List<String> modulesWithPluginXml = new ArrayList<>()
    for (Map.Entry<String, Collection<String>> entry : moduleJars.entrySet()) {
      if (!entry.key.contains("/")) {
        for (String  moduleName : entry.value) {
          if (containsFileInOutput(moduleName, "META-INF/plugin.xml", moduleExcludes.get(moduleName), buildContext)) {
            modulesWithPluginXml.add(moduleName)
          }
        }
      }
    }
    if (modulesWithPluginXml.size() > 1) {
      buildContext.messages.error("Multiple modules (${modulesWithPluginXml.join(", ")}) from '$mainPluginModule' plugin contain plugin.xml files so the plugin won't work properly")
    }
    if (modulesWithPluginXml.isEmpty()) {
      buildContext.messages.error("No module from '$mainPluginModule' plugin contains plugin.xml")
    }

    for (moduleJar in moduleJars.values()) {
      if (moduleJar != "intellij.java.guiForms.rt" &&
          containsFileInOutput(moduleJar, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(moduleJar), buildContext)) {
        buildContext.messages.error(
          "Runtime classes of GUI designer must not be packaged to '$moduleJar' module in '$mainPluginModule' plugin, because they are included into a platform JAR. " +
          "Make sure that 'Automatically copy form runtime classes to the output directory' is disabled in Settings | Editor | GUI Designer.")
      }
    }
  }

  private static boolean containsFileInOutput(@NotNull String moduleName, String filePath, Collection<String> excludes, BuildContext buildContext) {
    Path moduleOutput = buildContext.getModuleOutputDir(buildContext.findRequiredModule(moduleName))
    Path fileInOutput = moduleOutput.resolve(filePath)
    return Files.exists(fileInOutput) && (excludes == null || excludes.every {
      createFileSet(it, moduleOutput, buildContext).iterator().every {
        !(it instanceof FileProvider && FileUtil.pathsEqual(((FileProvider)it).file.toString(), fileInOutput.toString()))
      }
    })
  }

  /**
   * Returns path to a JAR file in the product distribution where platform/plugin classes will be placed. If the JAR name corresponds to
   * a module name and the module was renamed, return the old name to temporary keep the product layout unchanged.
   */
  static String getActualModuleJarPath(String relativeJarPath,
                                       Collection<String> moduleNames,
                                       Set<String> explicitlySetJarPaths,
                                       @NotNull BuildContext buildContext) {
    if (explicitlySetJarPaths.contains(relativeJarPath)) {
      return relativeJarPath
    }

    for (String moduleName : moduleNames) {
      if (relativeJarPath == "${BaseLayout.convertModuleNameToFileName(moduleName)}.jar" &&
          buildContext.getOldModuleName(moduleName) != null) {
        return "${buildContext.getOldModuleName(moduleName)}.jar"
      }
    }
    return relativeJarPath
  }

  /**
   * @param moduleJars mapping from JAR path relative to 'lib' directory to names of modules
   * @param additionalResources pairs of resources files and corresponding relative output paths
   */
  static ForkJoinTask<List<DistributionFileEntry>> processLayout(BaseLayout layout,
                                                                 Path targetDirectory,
                                                                 boolean copyFiles,
                                                                 ModuleOutputPatcher moduleOutputPatcher,
                                                                 MultiMap<String, String> moduleJars,
                                                                 BuildContext context) {
    BuildHelper buildHelper = BuildHelper.getInstance(context)
    return buildHelper.createTask(
      spanBuilder("layout").setAttribute("path", context.paths.buildOutputDir.relativize(targetDirectory).toString()),
      new Supplier<List<DistributionFileEntry>>() {
        @Override
        List<DistributionFileEntry> get() {
          if (copyFiles) {
            checkModuleExcludes(layout.moduleExcludes, context)
          }

          Collection<ForkJoinTask<Collection<DistributionFileEntry>>> tasks =
            new ArrayList<ForkJoinTask<Collection<DistributionFileEntry>>>(3)

          // patchers must be executed _before_ pack because patcher patches module output
          if (copyFiles && layout instanceof PluginLayout && !layout.patchers.isEmpty()) {
            List<BiConsumer<ModuleOutputPatcher, BuildContext>> patchers = layout.patchers
            buildHelper.span(spanBuilder("execute custom patchers").setAttribute("count", patchers.size()), new Runnable() {
              @Override
              void run() {
                for (BiConsumer<ModuleOutputPatcher, BuildContext> patcher : patchers) {
                  patcher.accept(moduleOutputPatcher, context)
                }
              }
            })
          }

          tasks.add(buildHelper.createTask(spanBuilder("pack"), new Supplier<Collection<DistributionFileEntry>>() {
            @Override
            Collection<DistributionFileEntry> get() {
              Map<String, List<String>> actualModuleJars = new LinkedHashMap<>()
              for (Map.Entry<String, Collection<String>> entry in moduleJars.entrySet()) {
                Collection<String> modules = entry.value
                String jarPath = getActualModuleJarPath(entry.key, modules, layout.explicitlySetJarPaths, context)
                actualModuleJars.computeIfAbsent(jarPath, { new ArrayList<>() }).addAll(modules)
              }
              return JarPackager.pack(actualModuleJars,
                                      targetDirectory.resolve("lib"),
                                      layout,
                                      moduleOutputPatcher,
                                      !copyFiles,
                                      context)
            }
          }))

          if (copyFiles && (!layout.resourcePaths.isEmpty() || (layout instanceof PluginLayout && !layout.resourceGenerators.isEmpty()))) {
            tasks.add(buildHelper.createTask(spanBuilder("pack additional resources"), new Supplier<Collection<DistributionFileEntry>>() {
              @Override
              Collection<DistributionFileEntry> get() {
                layoutAdditionalResources(layout, context, targetDirectory, buildHelper)
                return Collections.<DistributionFileEntry> emptyList()
              }
            }))
          }

          if (!layout.includedArtifacts.isEmpty()) {
            tasks.add(buildHelper.createTask(spanBuilder("pack artifacts"), new Supplier<Collection<DistributionFileEntry>>() {
              @Override
              Collection<DistributionFileEntry> get() {
                return layoutArtifacts(layout, context, copyFiles, targetDirectory)
              }
            }))
          }
          return ForkJoinTask.invokeAll(tasks).collectMany { it.rawResult }
        }
      }
    )
  }

  private static void layoutAdditionalResources(BaseLayout layout,
                                                BuildContext context,
                                                Path targetDirectory,
                                                BuildHelper buildHelper) {
    for (ModuleResourceData resourceData in layout.resourcePaths) {
      Path source = basePath(context, resourceData.moduleName).resolve(resourceData.resourcePath).normalize()
      Path target = targetDirectory.resolve(resourceData.relativeOutputPath)
      if (resourceData.packToZip) {
        if (Files.isDirectory(source)) {
          // do not compress - doesn't make sense as it is a part of distribution
          BuildHelper.zip(context, target, source, false)
        }
        else {
          target = target.resolve(source.fileName)
          new Compressor.Zip(target.toFile()).withCloseable {
            it.addFile(target.fileName.toString(), source)
          }
        }
      }
      else {
        if (Files.isRegularFile(source)) {
          BuildHelper.copyFileToDir(source, target)
        }
        else {
          buildHelper.copyDir(source, target)
        }
      }
    }

    if (layout instanceof PluginLayout) {
      List<Pair<BiFunction<Path, BuildContext, Path>, String>> resourceGenerators = layout.resourceGenerators
      buildHelper.span(spanBuilder("generate and pack resources"), new Runnable() {
        @Override
        void run() {
          for (Pair<BiFunction<Path, BuildContext, Path>, String> item : resourceGenerators) {
            Path resourceFile = item.first.apply(targetDirectory, context)
            if (resourceFile == null) {
              continue
            }

            Path target = item.second.isEmpty() ? targetDirectory : targetDirectory.resolve(item.second)
            if (Files.isRegularFile(resourceFile)) {
              BuildHelper.copyFileToDir(resourceFile, target)
            }
            else {
              buildHelper.copyDir(resourceFile, target)
            }
          }
        }
      })
    }
  }

  private static Collection<DistributionFileEntry> layoutArtifacts(BaseLayout layout,
                                                                   BuildContext context,
                                                                   boolean copyFiles,
                                                                   Path targetDirectory) {
    BuildHelper buildHelper = BuildHelper.getInstance(context)
    Span span = Span.current()
    Collection<DistributionFileEntry> entries = new ArrayList<>()
    for (Map.Entry<String, String> entry in layout.includedArtifacts.entrySet()) {
      String artifactName = entry.key
      String relativePath = entry.value

      span.addEvent("include artifact", Attributes.of(AttributeKey.stringKey("artifactName"), artifactName))

      JpsArtifact artifact = JpsArtifactService.instance.getArtifacts(context.project).find { it.name == artifactName }
      if (artifact == null) {
        throw new IllegalArgumentException("Cannot find artifact $artifactName in the project")
      }

      Path artifactFile
      if (artifact.outputFilePath == artifact.outputPath) {
        Path source = Path.of(artifact.outputPath)
        artifactFile = targetDirectory.resolve("lib").resolve(relativePath)
        if (copyFiles) {
          buildHelper.copyDir(source, targetDirectory.resolve("lib").resolve(relativePath))
        }
      }
      else {
        Path source = Path.of(artifact.outputFilePath)
        artifactFile = targetDirectory.resolve("lib").resolve(relativePath).resolve(source.fileName)
        if (copyFiles) {
          BuildHelper.copyFile(source, artifactFile)
        }
      }
      addArtifactMapping(artifact, entries, artifactFile)
    }
    return entries
  }

  private static void addArtifactMapping(@NotNull JpsArtifact artifact,
                                         @NotNull Collection<DistributionFileEntry> entries,
                                         @NotNull Path artifactFile) {
    JpsCompositePackagingElement rootElement = artifact.getRootElement()
    for (JpsPackagingElement element in rootElement.children) {
      if (element instanceof JpsProductionModuleOutputPackagingElement) {
        entries.add(new ModuleOutputEntry(artifactFile, element.moduleReference.moduleName, 0))
      }
      else if (element instanceof JpsTestModuleOutputPackagingElement) {
        entries.add(new ModuleTestOutputEntry(artifactFile, element.moduleReference.moduleName))
      }
      else if (element instanceof JpsLibraryFilesPackagingElement) {
        JpsLibrary library = element.libraryReference.resolve()
        JpsElementReference<? extends JpsCompositeElement> parentReference = library.createReference().parentReference
        if (parentReference instanceof JpsModuleReference) {
          entries.add(new ModuleLibraryFileEntry(artifactFile, ((JpsModuleReference)parentReference).moduleName, null, 0))
        }
        else {
          entries.add(new ProjectLibraryEntry(artifactFile, library.name, null, 0))
        }
      }
    }
  }


  private static void checkModuleExcludes(MultiMap<String, String> moduleExcludes, @NotNull BuildContext context) {
    for (entry in moduleExcludes.entrySet()) {
      String module = entry.key
      for (pattern in entry.value) {
        Path moduleOutput = context.getModuleOutputDir(context.findRequiredModule(module))
        if (Files.notExists(moduleOutput)) {
          context.messages.error("There are excludes defined for module '$module', but the module wasn't compiled; " +
                                      "most probably it means that '$module' isn't include into the product distribution so it makes no sense to define excludes for it.")
        }
      }
    }
  }

  private static FileSet createFileSet(String pattern, Path baseDir, BuildContext context) {
    FileSet fileSet = new FileSet()
    fileSet.setProject(context.ant.antProject)
    fileSet.setDir(baseDir.toFile())
    fileSet.createInclude().setName(pattern)
    return fileSet
  }

  static Path basePath(BuildContext buildContext, String moduleName) {
    return Path.of(JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first()))
  }

  private static void patchKeyMapWithAltClickReassignedToMultipleCarets(ModuleOutputPatcher moduleOutputPatcher, BuildContext context) {
    if (!context.productProperties.reassignAltClickToMultipleCarets) {
      return
    }

    String moduleName = "intellij.platform.resources"
    Path sourceFile = context.getModuleOutputDir(context.findModule(moduleName)).resolve("keymaps/\$default.xml")
    String defaultKeymapContent = Files.readString(sourceFile)
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt button1\"/>",
                                                        "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt button1\"/>")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt shift button1\"/>")
    moduleOutputPatcher.patchModuleOutput(moduleName, "keymaps/\$default.xml", defaultKeymapContent)
  }
}
