// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.resources.FileProvider
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.fus.StatisticsRecorderBundledMetadataProvider
import org.jetbrains.intellij.build.impl.projectStructureMapping.ProjectStructureMapping
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Assembles output of modules to platform JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAll distAll}/lib directory),
 * bundled plugins' JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAll distAll}/plugins directory) and zip archives with
 * non-bundled plugins (in {@link org.jetbrains.intellij.build.BuildPaths#artifacts artifacts}/plugins directory).
 */
@CompileStatic
final class DistributionJARsBuilder {
  private static final boolean COMPRESS_JARS = false
  /**
   * Path to file with third party libraries HTML content,
   * see the same constant at com.intellij.ide.actions.AboutPopup#THIRD_PARTY_LIBRARIES_FILE_PATH
   */
  private static final String THIRD_PARTY_LIBRARIES_FILE_PATH = "license/third-party-libraries.html"
  private static final String PLUGINS_DIRECTORY = "plugins"

  private final BuildContext buildContext
  final ProjectStructureMapping projectStructureMapping = new ProjectStructureMapping()
  final PlatformLayout platform
  private final String patchedApplicationInfo
  private final Set<PluginLayout> pluginsToPublish
  private final PluginXmlPatcher pluginXmlPatcher

  @CompileStatic(TypeCheckingMode.SKIP)
  DistributionJARsBuilder(BuildContext buildContext, Set<PluginLayout> pluginsToPublish = Collections.emptySet()) {
    this.patchedApplicationInfo = buildContext.applicationInfo?.getAppInfoXml()
    this.buildContext = buildContext
    this.pluginsToPublish = filterPluginsToPublish(pluginsToPublish)

    def releaseDate = buildContext.applicationInfo.majorReleaseDate
    if (releaseDate.startsWith('__')) {
      buildContext.messages.error("Unresolved release-date: $releaseDate")
    }
    def releaseVersion = "${buildContext.applicationInfo.majorVersion}${buildContext.applicationInfo.minorVersionMainPart}00"
    this.pluginXmlPatcher = new PluginXmlPatcher(buildContext.messages, releaseDate, releaseVersion, buildContext.applicationInfo.productName, buildContext.applicationInfo.isEAP)

    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    Set<String> enabledPluginModules = getEnabledPluginModules()
    buildContext.messages.debug("Collecting project libraries used by plugins: ")
    List<JpsLibrary> projectLibrariesUsedByPlugins = getPluginsByModules(buildContext, enabledPluginModules).collectMany { plugin ->
      def libraries = computeProjectLibrariesWhichShouldBeProvidedByPlatform(plugin, buildContext)
      libraries.entrySet().each { entry ->
        buildContext.messages.debug(" plugin '$plugin.mainModule', library '$entry.key.name': used in ${entry.value.collect { "'$it.name'" }.join(",")}")
      }
      libraries.keySet()
    }

    Set<String> allProductDependencies = (productLayout.getIncludedPluginModules(enabledPluginModules) +
                                          getIncludedPlatformModules(productLayout))
      .collectMany(new LinkedHashSet<String>()) {
        JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(it))
          .productionOnly()
          .getModules()
          .collect { it.name }
      }

    platform = PlatformModules.createPlatformLayout(productLayout, allProductDependencies, projectLibrariesUsedByPlugins, buildContext)
  }

  static MultiMap<JpsLibrary, JpsModule> computeProjectLibrariesWhichShouldBeProvidedByPlatform(BaseLayout plugin,
                                                                                                BuildContext buildContext) {
    MultiMap<JpsLibrary, JpsModule> result = MultiMap.createLinked()
    final Collection<String> libsToUnpack = plugin.projectLibrariesToUnpack.values()
    plugin.moduleJars.values().each {
      JpsModule module = buildContext.findRequiredModule(it)
      JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll { library ->
        !(library.createReference().parentReference instanceof JpsModuleReference) && !plugin.includedProjectLibraries.any {
          it.libraryName == library.name && it.relativeOutputPath == ""
        } && !libsToUnpack.contains(library.name)
      }.each {
        result.putValue(it, module)
      }
    }
    return result
  }

  @NotNull Set<PluginLayout> filterPluginsToPublish(@NotNull Set<PluginLayout> plugins) {
    plugins = plugins.findAll {
      // Kotlin Multiplatform Mobile plugin is excluded since:
      // * is compatible with Android Studio only;
      // * has release cycle of its
      // * shadows IntelliJ utility modules included via Kotlin Compiler;
      // * breaks searchable options index and jar order generation steps.
      it.mainModule != 'kotlin-ultimate.kmm-plugin'
    }
    if (plugins.isEmpty()) {
      return plugins
    }

    Set<String> toInclude = new HashSet<>(buildContext.options.nonBundledPluginDirectoriesToInclude)
    if (toInclude.isEmpty()) {
      return plugins
    }
    if (toInclude.size() == 1 && toInclude.contains("none")) {
      return new LinkedHashSet<PluginLayout>()
    }
    return plugins.findAll { toInclude.contains(it.directoryName) }
  }

  private Set<String> getEnabledPluginModules() {
    buildContext.productProperties.productLayout.bundledPluginModules + pluginsToPublish.collect { it.mainModule } as Set<String>
  }

  List<String> getPlatformModules() {
    (platform.moduleJars.values() as List<String>) + toolModules
  }

  static List<String> getIncludedPlatformModules(ProductModulesLayout modulesLayout) {
    PlatformModules.PLATFORM_API_MODULES + PlatformModules.PLATFORM_IMPLEMENTATION_MODULES + modulesLayout.productApiModules +
    modulesLayout.productImplementationModules + modulesLayout.additionalPlatformJars.values()
  }

  /**
   * @return module names which are required to run necessary tools from build scripts
   */
  static List<String> getToolModules() {
    return List.of("intellij.java.rt", "intellij.platform.main", /*required to build searchable options index*/ "intellij.platform.updater")
  }

  Collection<String> getIncludedProjectArtifacts() {
    platform.includedArtifacts.keySet() + getPluginsByModules(buildContext, getEnabledPluginModules()).collectMany {it.includedArtifacts.keySet()}
  }

  void buildJARs(boolean isUpdateFromSources = false) {
    validateModuleStructure()

    BuildTasksImpl.runInParallel(List.<BuildTaskRunnable<Void>>of(
      SVGPreBuilder.createPrebuildSvgIconsTask(),
      createBuildSearchableOptionsTask(getModulesForPluginsToPublish()),
      createBuildBrokenPluginListTask(),
    ), buildContext)

    buildLib()
    buildBundledPlugins()
    buildOsSpecificBundledPlugins()

    // must be before reorderJars as these additional plugins maybe required for IDE start-up
    List<Path> additionalPluginPaths = buildContext.productProperties.getAdditionalPluginPaths(buildContext)
    if (!additionalPluginPaths.isEmpty()) {
      Path pluginDir = buildContext.paths.distAllDir.resolve("plugins")
      for (Path sourceDir : additionalPluginPaths) {
        BuildHelper.copyDir(sourceDir, pluginDir.resolve(sourceDir.fileName), buildContext)
      }
    }

    buildNonBundledPlugins(!isUpdateFromSources)
    if (!isUpdateFromSources) {
      buildNonBundledPluginsBlockMaps()
    }
    buildThirdPartyLibrariesList(projectStructureMapping)

    Path artifactOut = Path.of(buildContext.paths.artifacts)
    Files.createDirectories(artifactOut)
    projectStructureMapping.generateJsonFile(artifactOut.resolve("content-mapping.json"), buildContext.paths)
    Files.newBufferedWriter(artifactOut.resolve("content.json")).withCloseable {
      ProjectStructureMapping.buildJarContentReport(projectStructureMapping, it, buildContext.paths)
    }
  }

  private static BuildTaskRunnable<Void> createBuildBrokenPluginListTask() {
    return BuildTaskRunnable.task(BuildOptions.BROKEN_PLUGINS_LIST_STEP, "Build broken plugin list") { BuildContext buildContext ->
      Path targetFile = buildContext.paths.tempDir.resolve("brokenPlugins.db")
      String currentBuildString = buildContext.buildNumber
      BuildHelper.getInstance(buildContext).brokenPluginsTask.invokeWithArguments(targetFile,
                                                                                  currentBuildString,
                                                                                  buildContext.options.isInDevelopmentMode,
                                                                                  buildContext.messages)
      if (Files.exists(targetFile)) {
        buildContext.addDistFile(new Pair<Path, String>(targetFile, "bin"))
      }
    }
  }

  /**
   * Validates module structure to be ensure all module dependencies are included
   */
  @CompileStatic
  void validateModuleStructure() {
    if (!buildContext.options.validateModuleStructure) {
      return
    }

    def validator = new ModuleStructureValidator(buildContext, platform.moduleJars)
    validator.validate()
  }

  @CompileStatic
  List<String> getProductModules() {
    List<String> result = new ArrayList<>()
    for (moduleJar in platform.moduleJars.entrySet()) {
      // Filter out jars with relative paths in name
      if (moduleJar.key.contains("\\") || moduleJar.key.contains("/")) {
        continue
      }

      result.addAll(moduleJar.value)
    }
    return result
  }

  /**
   * @see {@link org.jetbrains.intellij.build.impl.DistributionJARsBuilder#buildSearchableOptions}
   */
  static BuildTaskRunnable<Void> createBuildSearchableOptionsTask(@NotNull List<String> modulesForPluginsToPublish) {
    BuildTaskRunnable.task(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, "Build searchable options index", new Consumer<BuildContext>() {
      @Override
      void accept(BuildContext buildContext) {
        buildSearchableOptions(buildContext, modulesForPluginsToPublish)
      }
    })
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  @Nullable
  static Path buildSearchableOptions(BuildContext buildContext,
                                     @NotNull List<String> modulesForPluginsToPublish,
                                     BuildTasksImpl.ApplicationStarterClasspathCustomizer classpathCustomizer = new BuildTasksImpl.ApplicationStarterClasspathCustomizer(buildContext),
                                     Map<String, Object> systemProperties = Collections.emptyMap()) {
    if (buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      buildContext.messages.info("Skipping 'Build searchable options index'")
      return null
    }
    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    List<String> modulesToIndex = productLayout.mainModules + getModulesToCompile(buildContext) + modulesForPluginsToPublish
    modulesToIndex -= "intellij.ruby.lsp"
    Path targetDirectory = JarPackager.getSearchableOptionsDir(buildContext)
    buildContext.messages.progress("Building searchable options for ${modulesToIndex.size()} modules")
    buildContext.messages.debug("Searchable options are going to be built for the following modules: $modulesToIndex")
    NioFiles.deleteRecursively(targetDirectory)
    // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
    // It'll process all UI elements in Settings dialog and build index for them.
    BuildTasksImpl.runApplicationStarter(buildContext,
                                         buildContext.paths.tempDir.resolve("searchableOptions"),
                                         modulesToIndex, List.of("traverseUI", targetDirectory.toString(), "true"),
                                         systemProperties,
                                         List.of("-ea", "-Xmx1024m", "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader"),
                                         TimeUnit.MINUTES.toMillis(10L), classpathCustomizer)
    List<Path> modules = Files.newDirectoryStream(targetDirectory).withCloseable { it.asList() }
    if (modules.isEmpty()) {
      buildContext.messages.error("Failed to build searchable options index: $targetDirectory is empty")
    }
    else {
      buildContext.messages.info("Searchable options are built successfully for ${modules.size()} modules")
      buildContext.messages.debug("The following modules contain searchable options: $modules")
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

  List<String> getModulesForPluginsToPublish() {
    return platformModules + pluginsToPublish.collectMany(new LinkedHashSet()) { it.moduleJars.values() }
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

    buildInternalUtilities(buildContext)

    if (productProperties.buildSourcesArchive) {
      String archiveName = productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber) + "-sources.zip"
      def modulesFromCommunity = projectStructureMapping.includedModules.findAll { moduleName ->
        productProperties.includeIntoSourcesArchiveFilter.test(buildContext.findRequiredModule(moduleName), buildContext)
      }
      BuildTasks.create(buildContext).zipSourcesOfModules(modulesFromCommunity, Path.of(buildContext.paths.artifacts, archiveName), true)
    }
  }

  void generateProjectStructureMapping(Path targetFile) {
    LayoutBuilder layoutBuilder = createLayoutBuilder()
    processLibDirectoryLayout(layoutBuilder, projectStructureMapping, false)
    def allPlugins = getPluginsByModules(buildContext, buildContext.productProperties.productLayout.bundledPluginModules)
    def pluginsToBundle = allPlugins.findAll { satisfiesBundlingRequirements(it, null) }
    pluginsToBundle.each {
      processPluginLayout(it, layoutBuilder, buildContext.paths.tempDir, [], projectStructureMapping, false)
    }
    projectStructureMapping.generateJsonFile(targetFile, buildContext.paths)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  static void buildInternalUtilities(BuildContext buildContext) {
    if (buildContext.productProperties.scrambleMainJar) {
      new LayoutBuilder(buildContext, COMPRESS_JARS).layout("$buildContext.paths.buildOutputRoot/internal") {
        jar("internalUtilities.jar") {
          module("intellij.tools.internalUtilities")
        }
      }
    }
  }

  private void buildThirdPartyLibrariesList(@NotNull ProjectStructureMapping projectStructureMapping) {
    buildContext.executeStep("Generate table of licenses for used third-party libraries", BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP) {
      LibraryLicensesListGenerator generator = LibraryLicensesListGenerator.create(buildContext.messages,
                                                                                   buildContext.project,
                                                                                   buildContext.productProperties.allLibraryLicenses,
                                                                                   projectStructureMapping.includedModules)
      generator.generateHtml(getThirdPartyLibrariesHtmlFilePath(buildContext))
      generator.generateJson(getThirdPartyLibrariesJsonFilePath(buildContext))
    }
  }

  private static Path getThirdPartyLibrariesHtmlFilePath(@NotNull BuildContext buildContext) {
    return buildContext.paths.distAllDir.resolve(THIRD_PARTY_LIBRARIES_FILE_PATH)
  }

  private static Path getThirdPartyLibrariesJsonFilePath(@NotNull BuildContext buildContext) {
    return buildContext.paths.tempDir.resolve("third-party-libraries.json")
  }

  static Map<String, String> getPluginModulesToJar(@NotNull BuildContext buildContext) {
    Map<String, String> pluginsToJar = new HashMap<String, String>()
    def productLayout = buildContext.productProperties.productLayout
    def allPlugins = getPluginsByModules(buildContext, productLayout.bundledPluginModules + productLayout.pluginModulesToPublish)
    for (PluginLayout plugin : allPlugins) {
      String directory = getActualPluginDirectoryName(plugin, buildContext)
      getModuleToJarMap(plugin, buildContext, pluginsToJar, "/$PLUGINS_DIRECTORY/$directory/lib/")
    }
    return pluginsToJar
  }

  static Map<String, String> getModuleToJarMap(BaseLayout layout,
                                               @NotNull BuildContext buildContext,
                                               Map<String, String> moduleToJar = new HashMap<>(),
                                               String jarPrefix = "") {
    for (Map.Entry<String, Collection<String>> entry : layout.moduleJars.entrySet()) {
      String jarName = entry.key
      String fixedJarName = getActualModuleJarPath(jarName, entry.value, layout.explicitlySetJarPaths, buildContext)
      for (String el : entry.value) {
        moduleToJar.put(el, jarPrefix + fixedJarName)
      }
    }
    return moduleToJar
  }

  void buildLib() {
    LayoutBuilder layoutBuilder = createLayoutBuilder()
    ProductModulesLayout productLayout = buildContext.productProperties.productLayout

    if (buildContext.productProperties.reassignAltClickToMultipleCarets) {
      layoutBuilder.patchModuleOutput("intellij.platform.resources", createKeyMapWithAltClickReassignedToMultipleCarets())
    }
    if (buildContext.proprietaryBuildTools.featureUsageStatisticsProperties != null) {
      buildContext.executeStep("Bundling a default version of feature usage statistics", BuildOptions.FUS_METADATA_BUNDLE_STEP) {
        try {
          Path metadata = StatisticsRecorderBundledMetadataProvider.downloadMetadata(buildContext)
          layoutBuilder.patchModuleOutput('intellij.platform.ide.impl', metadata)
        }
        catch (Exception e) {
          buildContext.messages.warning('Failed to bundle default version of feature usage statistics metadata')
          e.printStackTrace()
        }
      }
    }

    ProjectStructureMapping libDirectoryMapping = new ProjectStructureMapping()
    buildContext.messages.block("Build platform JARs in lib directory") {
      processLibDirectoryLayout(layoutBuilder, projectStructureMapping, true)
    }
    projectStructureMapping.mergeFrom(libDirectoryMapping, "")

    if (buildContext.proprietaryBuildTools.scrambleTool != null) {
      List<String> forbiddenJarNames = buildContext.proprietaryBuildTools.scrambleTool.namesOfJarsRequiredToBeScrambled
      File[] packagedFiles = buildContext.paths.distAllDir.resolve("lib").toFile().listFiles()
      Collection<File> forbiddenJars = packagedFiles.findAll { forbiddenJarNames.contains(it.name) }
      if (!forbiddenJars.empty) {
        buildContext.messages.error( "The following JARs cannot be included into the product 'lib' directory, they need to be scrambled with the main jar: ${forbiddenJars}")
      }
      List<String> modulesToBeScrambled = buildContext.proprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled
      for (jarName in platform.moduleJars.keySet()) {
        if (jarName != productLayout.mainJarName) {
          Collection<String> notScrambled = platform.moduleJars.get(jarName).intersect(modulesToBeScrambled)
          if (!notScrambled.isEmpty()) {
            buildContext.messages.error("Module '${notScrambled.first()}' is included into $jarName which is not scrambled.")
          }
        }
      }
    }
  }

  void processLibDirectoryLayout(LayoutBuilder layoutBuilder, ProjectStructureMapping projectStructureMapping, boolean copyFiles) {
    if (copyFiles) {
      Path moduleOutDir = Path.of(buildContext.getModuleOutputPath(buildContext.findRequiredModule("intellij.platform.core")))
      Path patchedClassFileRoot = buildContext.paths.tempDir.resolve("appInfoData")
      Path classRelativeFile = Path.of("com/intellij/openapi/application/ApplicationNamesInfo.class")
      BuildHelper.getInstance(buildContext).setAppInfo.invokeWithArguments(
        moduleOutDir.resolve(classRelativeFile),
        patchedClassFileRoot.resolve(classRelativeFile),
        patchedApplicationInfo
      )
      layoutBuilder.patchModuleOutput("intellij.platform.core", patchedClassFileRoot)
    }
    processLayout(layoutBuilder, platform, buildContext.paths.distAllDir, layoutBuilder.createLayoutSpec(projectStructureMapping, copyFiles),
                  platform.moduleJars,
                  Collections.<Pair<File, String>>emptyList())
  }

  void buildBundledPlugins() {
    buildBundledPlugins(getPluginsByModules(buildContext, buildContext.productProperties.productLayout.bundledPluginModules))
  }

  void buildBundledPlugins(Collection<PluginLayout> plugins) {
    LayoutBuilder layoutBuilder = createLayoutBuilder()
    Set<String> pluginDirectoriesToSkip = new HashSet<>(buildContext.options.bundledPluginDirectoriesToSkip)
    buildContext.messages.debug("Plugin directories to skip: " + pluginDirectoriesToSkip)
    buildContext.messages.block("Build bundled plugins") {
      Collection<PluginLayout> pluginsToBundle = plugins.findAll {
        satisfiesBundlingRequirements(it, null) && !pluginDirectoriesToSkip.contains(it.directoryName)
      }

      buildPlugins(layoutBuilder, pluginsToBundle, buildContext.paths.distAllDir.resolve(PLUGINS_DIRECTORY), projectStructureMapping)
    }
  }

  private boolean satisfiesBundlingRequirements(PluginLayout plugin, @Nullable OsFamily osFamily) {
    def bundlingRestrictions = plugin.bundlingRestrictions
    if (!buildContext.applicationInfo.isEAP && bundlingRestrictions.includeInEapOnly) {
      return false
    }
    osFamily == null ? bundlingRestrictions.supportedOs == OsFamily.ALL
                     : bundlingRestrictions.supportedOs != OsFamily.ALL && bundlingRestrictions.supportedOs.contains(osFamily)
  }

  private void buildOsSpecificBundledPlugins() {
    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    for (OsFamily osFamily in OsFamily.values()) {
      Collection<PluginLayout> osSpecificPlugins = getPluginsByModules(buildContext, productLayout.bundledPluginModules).findAll {
        satisfiesBundlingRequirements(it, osFamily)
      }

      if (!osSpecificPlugins.isEmpty() && buildContext.shouldBuildDistributionForOS(osFamily.osId)) {
        LayoutBuilder layoutBuilder = createLayoutBuilder()
        buildContext.messages.block("Build bundled plugins for $osFamily.osName") {
          buildPlugins(layoutBuilder, osSpecificPlugins,
                       getOsSpecificDistDirectory(osFamily, buildContext).resolve("plugins"), projectStructureMapping)
        }
      }
    }
  }

  static Path getOsSpecificDistDirectory(OsFamily osFamily, BuildContext buildContext) {
    Path.of(buildContext.paths.buildOutputRoot, "dist.$osFamily.distSuffix")
  }

  /**
   * @return predicate to test if a given plugin should be auto-published
   */
  @NotNull
  private Predicate<PluginLayout> loadPluginsAutoPublishList() {
    Path configFile = buildContext.paths.communityHomeDir.resolve("../build/plugins-autoupload.txt")
    String productCode = buildContext.applicationInfo.productCode
    Collection<String> config = Files.lines(configFile)
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
        if (plugin == null) return false

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

  void buildNonBundledPlugins(boolean compressPluginArchive) {
    if (pluginsToPublish.isEmpty()) {
      return
    }

    ProductModulesLayout productLayout = buildContext.productProperties.productLayout
    LayoutBuilder layoutBuilder = createLayoutBuilder()
    buildContext.executeStep("Build non-bundled plugins", BuildOptions.NON_BUNDLED_PLUGINS_STEP, new Runnable() {
      @Override
      void run() {
        Path pluginsToPublishDir = buildContext.paths.tempDir.resolve("${buildContext.applicationInfo.productCode}-plugins-to-publish")
        buildPlugins(layoutBuilder, List.<PluginLayout>copyOf(pluginsToPublish), pluginsToPublishDir, null)

        String pluginVersion = buildContext.buildNumber.endsWith(".SNAPSHOT")
          ? buildContext.buildNumber + ".${new SimpleDateFormat('yyyyMMdd').format(new Date())}"
          : buildContext.buildNumber
        String pluginsDirectoryName = "${buildContext.applicationInfo.productCode}-plugins"
        Path nonBundledPluginsArtifacts = Paths.get(buildContext.paths.artifacts, pluginsDirectoryName)
        List<PluginRepositorySpec> pluginsToIncludeInCustomRepository = new ArrayList<PluginRepositorySpec>()
        Predicate<PluginLayout> autoPublishPluginChecker = loadPluginsAutoPublishList()

        Path autoUploadingDir = nonBundledPluginsArtifacts.resolve("auto-uploading")
        Path patchedPluginXmlDir = buildContext.paths.tempDir.resolve("patched-plugin-xml")
        List<Map.Entry<String, Path>> toArchive = new ArrayList<>()
        for (plugin in pluginsToPublish) {
          String directory = getActualPluginDirectoryName(plugin, buildContext)
          Path targetDirectory = autoPublishPluginChecker.test(plugin) ? autoUploadingDir : nonBundledPluginsArtifacts
          Path destFile = targetDirectory.resolve("$directory-${pluginVersion}.zip")

          if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
            Path pluginXml = patchedPluginXmlDir.resolve("${plugin.mainModule}/META-INF/plugin.xml")
            if (!Files.exists(pluginXml)) {
              buildContext.messages.error("patched plugin.xml not found for ${plugin.mainModule} module: $pluginXml")
            }
            pluginsToIncludeInCustomRepository
              .add(new PluginRepositorySpec(pluginZip: destFile.toString(), pluginXml: pluginXml.toString()))
          }
          toArchive.add(new AbstractMap.SimpleImmutableEntry(directory, destFile))
        }

        BuildHelper.bulkZipWithPrefix(buildContext, pluginsToPublishDir, toArchive, compressPluginArchive)
        for (Map.Entry<String, Path> item : toArchive) {
          buildContext.notifyArtifactWasBuilt(item.value)
        }

        for (PluginRepositorySpec item in KeymapPluginsBuilder.buildKeymapPlugins(buildContext, autoUploadingDir)) {
          if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
            pluginsToIncludeInCustomRepository.add(item)
          }
        }

        PluginLayout helpPlugin = BuiltInHelpPlugin.helpPlugin(buildContext, pluginVersion)
        if (helpPlugin != null) {
          PluginRepositorySpec spec = buildHelpPlugin(helpPlugin, pluginsToPublishDir, autoUploadingDir, layoutBuilder)
          if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
            pluginsToIncludeInCustomRepository.add(spec)
          }
        }

        if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
          new PluginRepositoryXmlGenerator(buildContext).generate(pluginsToIncludeInCustomRepository, nonBundledPluginsArtifacts.toString())
          buildContext.notifyArtifactWasBuilt(nonBundledPluginsArtifacts.resolve("plugins.xml"))

          def autoUploadingDirPath = autoUploadingDir.toString()
          def autoUploadingPlugins = pluginsToIncludeInCustomRepository.findAll { it.pluginZip.startsWith(autoUploadingDirPath) }
          new PluginRepositoryXmlGenerator(buildContext).generate(autoUploadingPlugins, autoUploadingDirPath)
          buildContext.notifyArtifactWasBuilt(autoUploadingDir.resolve("plugins.xml"))
        }
      }
    })
  }

  /**
   * This function builds a blockmap and hash files for each non bundled plugin
   * to provide downloading plugins via incremental downloading algorithm Blockmap.
   */
  private void buildNonBundledPluginsBlockMaps(){
    String pluginsDirectoryName = "${buildContext.applicationInfo.productCode}-plugins"
    String nonBundledPluginsArtifacts = "$buildContext.paths.artifacts/$pluginsDirectoryName"
    Path path = Paths.get(nonBundledPluginsArtifacts)
    if (!Files.exists(path)) {
      return
    }

    Files.walk(path)
      .filter({ it -> it.toString().endsWith(".zip") && Files.isRegularFile(it) })
      .forEach { Path file ->
        Path blockMapFile = file.parent.resolve("${file.fileName}.blockmap.zip")
        String algorithm = "SHA-256"
        byte[] bytes
        new BufferedInputStream(Files.newInputStream(file)).withCloseable { input ->
          bytes = JsonOutput.toJson(new BlockMap(input, algorithm)).bytes
        }

        new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(blockMapFile))).withCloseable { stream ->
          try {
            //noinspection SpellCheckingInspection
            ZipEntry entry = new ZipEntry("blockmap.json")
            stream.putNextEntry(entry)
            stream.write(bytes)
            stream.closeEntry()
          }
          finally {
            stream.close()
          }
        }

        Path hashFile = file.parent.resolve("${file.fileName}.hash.json")
        new BufferedInputStream(Files.newInputStream(file)).withCloseable { input ->
          Files.writeString(hashFile, JsonOutput.toJson(new FileHash(input, algorithm)))
        }
      }
  }

  private PluginRepositorySpec buildHelpPlugin(PluginLayout helpPlugin, Path pluginsToPublishDir, Path targetDir, LayoutBuilder layoutBuilder) {
    String directory = getActualPluginDirectoryName(helpPlugin, buildContext)
    Path destFile = targetDir.resolve(directory + ".zip")
    Path patchedPluginXmlDir = buildContext.paths.tempDir.resolve("patched-plugin-xml/$helpPlugin.mainModule")
    layoutBuilder.patchModuleOutput(helpPlugin.mainModule, patchedPluginXmlDir)
    buildContext.messages.block("Building $directory plugin", new Supplier<Object>() {
      @Override
      Object get() {
        buildPlugins(layoutBuilder, List.of(helpPlugin), pluginsToPublishDir, null)
        BuildHelper.zipWithPrefix(buildContext, destFile, List.of(pluginsToPublishDir.resolve(directory)), directory)
        return null
      }
    })
    buildContext.notifyArtifactBuilt(destFile)
    Path pluginXmlPath = patchedPluginXmlDir.resolve("META-INF/plugin.xml")
    return new PluginRepositorySpec(pluginZip: destFile.toString(), pluginXml: pluginXmlPath.toString())
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

  static Set<PluginLayout> getPluginsByModules(BuildContext buildContext, Collection<String> modules) {
    if (modules.isEmpty()) {
      return Collections.emptySet()
    }

    List<PluginLayout> allNonTrivialPlugins = buildContext.productProperties.productLayout.allNonTrivialPlugins
    Map<String, List<PluginLayout>> nonTrivialPlugins = allNonTrivialPlugins.groupBy { it.mainModule }
    Set<PluginLayout> result = new LinkedHashSet<>(modules.size())
    for (String moduleName : modules) {
      PluginLayout layout = (nonTrivialPlugins[moduleName] ?: nonTrivialPlugins[buildContext.findModule(moduleName)?.name])?.first()
        ?: PluginLayout.plugin(moduleName)
      if (!result.add(layout)) {
        throw new IllegalStateException("Plugin layout for module $moduleName is already added (duplicated module name?)")
      }
    }
    return result
  }

  private void buildPlugins(LayoutBuilder layoutBuilder,
                            Collection<PluginLayout> pluginsToInclude,
                            Path targetDirectory,
                            ProjectStructureMapping parentMapping) {
    List<Pair<PluginLayout, Path>> pluginsToScramble = new ArrayList<>()
    for (PluginLayout plugin in pluginsToInclude) {
      boolean isHelpPlugin = "intellij.platform.builtInHelp" == plugin.mainModule
      if (!isHelpPlugin) {
        checkOutputOfPluginModules(plugin.mainModule, plugin.moduleJars, plugin.moduleExcludes)
        patchPluginXml(layoutBuilder, plugin)
      }

      List<Pair<File, String>> generatedResources = new ArrayList<>(plugin.resourceGenerators.size())
      for (Pair<ResourcesGenerator, String> item : plugin.resourceGenerators) {
        File resourceFile = item.first.generateResources(buildContext)
        if (resourceFile != null) {
          generatedResources.add(new Pair<>(resourceFile, item.second))
        }
      }

      for (Pair<String, ResourcesGenerator> item : plugin.moduleOutputPatches) {
        File resources = item.second.generateResources(buildContext)
        if (resources != null) {
          layoutBuilder.patchModuleOutput(item.first, resources.toPath())
        }
      }

      Path pluginDir = targetDirectory.resolve(getActualPluginDirectoryName(plugin, buildContext))
      processPluginLayout(plugin, layoutBuilder, pluginDir, generatedResources, parentMapping, true)
      if (!plugin.pathsToScramble.isEmpty()) {
        pluginsToScramble.add(new Pair<>(plugin, pluginDir))
      }
    }

    if (buildContext.proprietaryBuildTools.scrambleTool == null) {
      for (Pair<PluginLayout, Path> pluginPair in pluginsToScramble) {
        buildContext.messages.warning("Scrambling plugin $pluginPair.first.directoryName skipped: " +
                                      "'scrambleTool' isn't defined, but plugin defines paths to be scrambled")
      }
    }
    else {
      for (Pair<PluginLayout, Path> pluginPair in pluginsToScramble) {
        PluginLayout pluginLayout = pluginPair.first
        List<String> pathsToScramble = pluginLayout.pathsToScramble
        Path pluginDir = pluginPair.second
        buildContext.proprietaryBuildTools.scrambleTool.scramblePlugin(buildContext, pluginLayout, pluginDir, targetDirectory)
        BuildHelper buildHelper = BuildHelper.getInstance(buildContext)
        // update package index
        for (String path : pathsToScramble) {
          Path file = pluginDir.resolve(path)
          Path tempFile = pluginDir.resolve("temp_" + file.fileName.toString())
          Files.move(file, tempFile)
          buildHelper.buildJar.invokeWithArguments(file,
                                                   List.of(buildHelper.createZipSource.invokeWithArguments(tempFile, null)),
                                                   buildContext.messages,
                                                   false)
          Files.delete(tempFile)
        }
      }
    }
  }

  private void patchPluginXml(LayoutBuilder layoutBuilder, PluginLayout plugin) {
    def bundled = !pluginsToPublish.contains(plugin)
    def moduleOutput = buildContext.getModuleOutputPath(buildContext.findRequiredModule(plugin.mainModule))
    Path pluginXmlPath = Paths.get(moduleOutput, "META-INF/plugin.xml")
    if (!Files.exists(pluginXmlPath)) {
      buildContext.messages.error("plugin.xml not found in $plugin.mainModule module: $pluginXmlPath")
    }

    Path patchedPluginXmlDir = Paths.get(buildContext.paths.temp, "patched-plugin-xml/$plugin.mainModule")
    Path patchedPluginXmlMetaInfDir = patchedPluginXmlDir.resolve("META-INF")
    Files.createDirectories(patchedPluginXmlMetaInfDir)
    Path patchedPluginXmlFile = patchedPluginXmlMetaInfDir.resolve("plugin.xml")
    Files.copy(pluginXmlPath, patchedPluginXmlFile, StandardCopyOption.REPLACE_EXISTING)

    def productLayout = buildContext.productProperties.productLayout
    def includeInBuiltinCustomRepository = productLayout.prepareCustomPluginRepositoryForPublishedPlugins &&
            buildContext.proprietaryBuildTools.artifactsServer != null
    CompatibleBuildRange compatibleBuildRange = bundled || plugin.pluginCompatibilityExactVersion ||
            //plugins included into the built-in custom plugin repository should use EXACT range because such custom repositories are used for nightly builds and there may be API differences between different builds
            includeInBuiltinCustomRepository ? CompatibleBuildRange.EXACT :
                    //when publishing plugins with EAP build let's use restricted range to ensure that users will update to a newer version of the plugin when they update to the next EAP or release build
                    buildContext.applicationInfo.isEAP ? CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
                            : CompatibleBuildRange.NEWER_WITH_SAME_BASELINE

    def defaultPluginVersion = buildContext.buildNumber.endsWith(".SNAPSHOT")
      ? buildContext.buildNumber + ".${new SimpleDateFormat('yyyyMMdd').format(new Date())}"
      : buildContext.buildNumber

    def pluginVersion = plugin.versionEvaluator.evaluate(patchedPluginXmlFile, defaultPluginVersion, buildContext)

    Pair<String, String> sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, buildContext.buildNumber)

    try {
      pluginXmlPatcher.patchPluginXml(
        patchedPluginXmlFile,
        plugin.mainModule,
        pluginVersion,
        sinceUntil,
        pluginsToPublish.contains(plugin),
        plugin.retainProductDescriptorForBundledPlugin,
      )
      plugin.pluginXmlPatcher.accept(patchedPluginXmlFile)
    }
    catch (Throwable t) {
      throw new RuntimeException("Could not patch $pluginXmlPath: ${t.message}", t)
    }

    layoutBuilder.patchModuleOutput(plugin.mainModule, patchedPluginXmlDir)
  }

  private void processPluginLayout(PluginLayout plugin, LayoutBuilder layoutBuilder, Path targetDir,
                                   List<Pair<File, String>> generatedResources, ProjectStructureMapping parentMapping, boolean copyFiles) {
    def mapping = new ProjectStructureMapping()
    processLayout(layoutBuilder, plugin, targetDir, layoutBuilder.createLayoutSpec(mapping, copyFiles), plugin.moduleJars, generatedResources)
    if (parentMapping != null) {
      parentMapping.mergeFrom(mapping, "plugins/${getActualPluginDirectoryName(plugin, buildContext)}")
    }
  }

  void checkOutputOfPluginModules(String mainPluginModule, MultiMap<String, String> moduleJars, MultiMap<String, String> moduleExcludes) {
    // don't check modules which are not direct children of lib/ directory
    List<String> modulesWithPluginXml = new ArrayList<>()
    for (Map.Entry<String, Collection<String>> entry : moduleJars.entrySet()) {
      if (!entry.key.contains("/")) {
        for (String  moduleName : entry.value) {
          if (containsFileInOutput(moduleName, "META-INF/plugin.xml", moduleExcludes.get(moduleName))) {
            modulesWithPluginXml.add(moduleName)
          }
        }
      }
    }
    if (modulesWithPluginXml.size() > 1) {
      buildContext.messages.error("Multiple modules (${modulesWithPluginXml.join(", ")}) from '$mainPluginModule' plugin contain plugin.xml files so the plugin won't work properly")
    }
    if (modulesWithPluginXml.size() == 0) {
      buildContext.messages.error("No module from '$mainPluginModule' plugin contains plugin.xml")
    }

    for (moduleJar in moduleJars.values()) {
      if (moduleJar != "intellij.java.guiForms.rt" &&
          containsFileInOutput(moduleJar, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(moduleJar))) {
        buildContext.messages.error(
          "Runtime classes of GUI designer must not be packaged to '$moduleJar' module in '$mainPluginModule' plugin, because they are included into a platform JAR. " +
          "Make sure that 'Automatically copy form runtime classes to the output directory' is disabled in Settings | Editor | GUI Designer.")
      }
    }
  }

  private boolean containsFileInOutput(String moduleName, String filePath, Collection<String> excludes) {
    Path moduleOutput = Paths.get(buildContext.getModuleOutputPath(buildContext.findRequiredModule(moduleName)))
    Path fileInOutput = moduleOutput.resolve(filePath)
    return Files.exists(fileInOutput) && (excludes == null || excludes.every {
      createFileSet(it, moduleOutput).iterator().every {
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
  @CompileStatic(TypeCheckingMode.SKIP)
  void processLayout(LayoutBuilder layoutBuilder,
                     BaseLayout layout,
                     Path targetDirectory,
                     LayoutBuilder.LayoutSpec layoutSpec,
                     MultiMap<String, String> moduleJars,
                     List<Pair<File, String>> additionalResources) {
    AntBuilder ant = buildContext.ant
    BuildContext buildContext = buildContext
    if (layoutSpec.copyFiles) {
      checkModuleExcludes(layout.moduleExcludes)
    }
    Map<String, List<String>> actualModuleJars = new LinkedHashMap<>()
    for (Map.Entry<String, Collection<String>> entry in moduleJars.entrySet()) {
      Collection<String> modules = entry.value
      String jarPath = getActualModuleJarPath(entry.key, modules, layout.explicitlySetJarPaths, buildContext)
      actualModuleJars.computeIfAbsent(jarPath, { new ArrayList<>() }).addAll(modules)
    }

    JarPackager.pack(actualModuleJars, targetDirectory.resolve("lib"), layout, layoutSpec, buildContext)

    layoutBuilder.process(targetDirectory.toString(), layoutSpec) {
      dir("lib") {
        for (Map.Entry<String, String> entry in layout.includedArtifacts.entrySet()) {
          String artifactName = entry.key
          String relativePath = entry.value
          dir(relativePath) {
            artifact(artifactName)
          }
        }
      }
      if (layoutSpec.copyFiles) {
        for (ModuleResourceData resourceData in layout.resourcePaths) {
          String path = FileUtilRt.toSystemIndependentName(new File(basePath(buildContext, resourceData.moduleName),
                                                                    resourceData.resourcePath).absolutePath)
          if (resourceData.packToZip) {
            zip(resourceData.relativeOutputPath) {
              if (Files.isRegularFile(Path.of(path))) {
                ant.fileset(file: path)
              }
              else {
                ant.fileset(dir: path)
              }
            }
          }
          else {
            dir(resourceData.relativeOutputPath) {
              if (Files.isRegularFile(Path.of(path))) {
                ant.fileset(file: path)
              }
              else {
                ant.fileset(dir: path)
              }
            }
          }
        }
        for (Pair<File, String> additionalResource in additionalResources) {
          File resource = additionalResource.first
          dir(additionalResource.second) {
            if (resource.isFile()) {
              ant.fileset(file: resource.absolutePath)
            }
            else {
              ant.fileset(dir: resource.absolutePath)
            }
          }
        }
      }
    }
  }

  private void checkModuleExcludes(MultiMap<String, String> moduleExcludes) {
    for (entry in moduleExcludes.entrySet()) {
      String module = entry.key
      for (pattern in entry.value) {
        Path moduleOutput = Paths.get(buildContext.getModuleOutputPath(buildContext.findRequiredModule(module)))
        if (!Files.exists(moduleOutput)) {
          buildContext.messages.error("There are excludes defined for module '$module', but the module wasn't compiled; " +
                                      "most probably it means that '$module' isn't include into the product distribution so it makes no sense to define excludes for it.")
        }
        if (createFileSet(pattern, moduleOutput).size() == 0) {
          buildContext.messages.error("Incorrect excludes for module '$module': nothing matches to $pattern in the module output at $moduleOutput")
        }
      }
    }
  }

  private FileSet createFileSet(String pattern, Path baseDir) {
    FileSet fileSet = new FileSet()
    fileSet.setProject(buildContext.ant.antProject)
    fileSet.setDir(baseDir.toFile())
    fileSet.createInclude().setName(pattern)
    return fileSet
  }

  static String basePath(BuildContext buildContext, String moduleName) {
    JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first())
  }

  private LayoutBuilder createLayoutBuilder() {
    new LayoutBuilder(buildContext, COMPRESS_JARS)
  }

  static Pair<String, String> getCompatiblePlatformVersionRange(CompatibleBuildRange compatibleBuildRange, String buildNumber) {
    String sinceBuild
    String untilBuild
    if (compatibleBuildRange != CompatibleBuildRange.EXACT && buildNumber.matches(/(\d+\.)+\d+/)) {
      if (compatibleBuildRange == CompatibleBuildRange.ANY_WITH_SAME_BASELINE) {
        sinceBuild = buildNumber.substring(0, buildNumber.indexOf('.'))
        untilBuild = buildNumber.substring(0, buildNumber.indexOf('.')) + ".*"
      }
      else {
        if (buildNumber.matches(/\d+\.\d+/)) {
          sinceBuild = buildNumber
        }
        else {
          sinceBuild = buildNumber.substring(0, buildNumber.lastIndexOf('.'))
        }
        int end = compatibleBuildRange == CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE ? buildNumber.lastIndexOf('.') : buildNumber.indexOf('.')
        untilBuild = buildNumber.substring(0, end) + ".*"
      }
    }
    else {
      sinceBuild = buildNumber
      untilBuild = buildNumber
    }
    Pair.create(sinceBuild, untilBuild)
  }

  private @NotNull Path createKeyMapWithAltClickReassignedToMultipleCarets() {
    Path sourceFile = Paths.get(buildContext.getModuleOutputPath(buildContext.findModule("intellij.platform.resources")), "keymaps/\$default.xml")
    String defaultKeymapContent = Files.readString(sourceFile)
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt button1\"/>",
                                                        "<mouse-shortcut keystroke=\"to be alt shift button1\"/>")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt button1\"/>")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"to be alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt shift button1\"/>")
    Path patchedKeyMapDir = Paths.get(buildContext.paths.temp, "patched-keymap")
    Path targetFile = patchedKeyMapDir.resolve("keymaps/\$default.xml")
    Files.createDirectories(targetFile.parent)
    Files.writeString(targetFile, defaultKeymapContent)
    return patchedKeyMapDir
  }
}
