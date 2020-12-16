// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.MultiMap
import com.jetbrains.plugin.blockmap.core.BlockMap
import com.jetbrains.plugin.blockmap.core.FileHash
import groovy.io.FileType
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
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer
import java.util.stream.Collectors
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
  private static final String RESOURCES_INCLUDED = "resources.included"
  private static final String RESOURCES_EXCLUDED = "resources.excluded"
  /**
   * Path to file with third party libraries HTML content,
   * see the same constant at com.intellij.ide.actions.AboutPopup#THIRD_PARTY_LIBRARIES_FILE_PATH
   */
  private static final String THIRD_PARTY_LIBRARIES_FILE_PATH = "license/third-party-libraries.html"
  static final String PLUGINS_DIRECTORY = "/plugins"

  private final BuildContext buildContext
  private final ProjectStructureMapping projectStructureMapping = new ProjectStructureMapping()
  final PlatformLayout platform
  private final Path patchedApplicationInfo
  private final LinkedHashSet<PluginLayout> pluginsToPublish

  @CompileStatic(TypeCheckingMode.SKIP)
  DistributionJARsBuilder(BuildContext buildContext,
                          @Nullable Path patchedApplicationInfo,
                          Set<PluginLayout> pluginsToPublish = Collections.emptyList()) {
    this.patchedApplicationInfo = patchedApplicationInfo
    this.buildContext = buildContext
    this.pluginsToPublish = filterPluginsToPublish(pluginsToPublish)
    buildContext.ant.patternset(id: RESOURCES_INCLUDED) {
      include(name: "**/*Bundle*.properties")
      include(name: "**/*Messages.properties")
      include(name: "messages/**/*.properties")
      include(name: "fileTemplates/**")
      include(name: "inspectionDescriptions/**")
      include(name: "intentionDescriptions/**")
      include(name: "tips/**")
      include(name: "search/**")
    }

    buildContext.ant.patternset(id: RESOURCES_EXCLUDED) {
      exclude(name: "**/*Bundle*.properties")
      exclude(name: "**/*Messages.properties")
      exclude(name: "messages/**/*.properties")
      exclude(name: "fileTemplates/**")
      exclude(name: "fileTemplates")
      exclude(name: "inspectionDescriptions/**")
      exclude(name: "inspectionDescriptions")
      exclude(name: "intentionDescriptions/**")
      exclude(name: "intentionDescriptions")
      exclude(name: "tips/**")
      exclude(name: "tips")
      exclude(name: "search/**")
      exclude(name: "**/icon-robots.txt")
    }

    def productLayout = buildContext.productProperties.productLayout
    def enabledPluginModules = getEnabledPluginModules()
    buildContext.messages.debug("Collecting project libraries used by plugins: ")
    List<JpsLibrary> projectLibrariesUsedByPlugins = getPluginsByModules(buildContext, enabledPluginModules).collectMany { plugin ->
      final Collection<String> libsToUnpack = plugin.projectLibrariesToUnpack.values()
      plugin.moduleJars.values().collectMany {
        def module = buildContext.findRequiredModule(it)
        def libraries =
          JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll { library ->
            !(library.createReference().parentReference instanceof JpsModuleReference) && !plugin.includedProjectLibraries.any {
              it.libraryName == library.name && it.relativeOutputPath == ""
            } && !libsToUnpack.contains(library.name)
          }
        if (!libraries.isEmpty()) {
          buildContext.messages.debug(" plugin '$plugin.mainModule', module '$it': ${libraries.collect { "'$it.name'" }.join(",")}")
        }
        libraries
      }
    }

    Set<String> allProductDependencies = (productLayout.getIncludedPluginModules(enabledPluginModules) +
                                          getIncludedPlatformModules(productLayout))
      .collectMany(new LinkedHashSet<String>()) {
        JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(it))
          .productionOnly()
          .getModules()
          .collect { it.name }
      }

    platform = PlatformLayout.platform(productLayout.platformLayoutCustomizer) {
      BaseLayoutSpec.metaClass.addModule = { String moduleName ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName)
        }
      }
      BaseLayoutSpec.metaClass.addModule = { String moduleName, String relativeJarPath ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName, relativeJarPath)
        }
      }

      productLayout.additionalPlatformJars.entrySet().each {
        def jarName = it.key
        it.value.each {
          addModule(it, jarName)
        }
      }
      CommunityRepositoryModules.PLATFORM_API_MODULES.each {
        addModule(it, "platform-api.jar")
      }
      CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES.each {
        addModule(it, "platform-impl.jar")
      }
      productLayout.productApiModules.each {
        addModule(it, "openapi.jar")
      }

      productLayout.productImplementationModules.each {
        addModule(it, productLayout.mainJarName)
      }

      productLayout.moduleExcludes.entrySet().each {
        layout.moduleExcludes.putValues(it.key, it.value)
      }

      addModule("intellij.platform.util", "util.jar")
      addModule("intellij.platform.util.rt", "util.jar")
      addModule("intellij.platform.util.classLoader", "util.jar")
      addModule("intellij.platform.util.text.matching", "util.jar")
      addModule("intellij.platform.util.collections", "util.jar")
      addModule("intellij.platform.util.strings", "util.jar")
      addModule("intellij.platform.util.diagnostic", "util.jar")
      addModule("intellij.platform.util.ui", "util.jar")
      addModule("intellij.platform.util.ex", "util.jar")
      addModule("intellij.platform.rd.community")

      addModule("intellij.platform.diagnostic")
      addModule("intellij.platform.ide.util.io", "util.jar")

      addModule("intellij.platform.core.ui")

      addModule("intellij.platform.credentialStore")
      withoutModuleLibrary("intellij.platform.credentialStore", "dbus-java")
      addModule("intellij.json")
      addModule("intellij.spellchecker")
      addModule("intellij.platform.statistics", "stats.jar")
      addModule("intellij.platform.statistics.uploader", "stats.jar")
      addModule("intellij.platform.statistics.config", "stats.jar")
      addModule("intellij.platform.statistics.devkit")

      addModule("intellij.relaxng", "intellij-xml.jar")
      addModule("intellij.xml.analysis.impl", "intellij-xml.jar")
      addModule("intellij.xml.psi.impl", "intellij-xml.jar")
      addModule("intellij.xml.structureView.impl", "intellij-xml.jar")
      addModule("intellij.xml.impl", "intellij-xml.jar")

      addModule("intellij.platform.vcs.impl", "intellij-dvcs.jar")
      addModule("intellij.platform.vcs.dvcs.impl", "intellij-dvcs.jar")
      addModule("intellij.platform.vcs.log.graph.impl", "intellij-dvcs.jar")
      addModule("intellij.platform.vcs.log.impl", "intellij-dvcs.jar")
      addModule("intellij.platform.vcs.codeReview", "intellij-dvcs.jar")

      addModule("intellij.platform.objectSerializer.annotations")

      addModule("intellij.platform.extensions")
      addModule("intellij.platform.bootstrap")
      addModule("intellij.java.guiForms.rt")
      addModule("intellij.platform.icons")
      addModule("intellij.platform.boot", "bootstrap.jar")
      addModule("intellij.platform.resources", "resources.jar")
      addModule("intellij.platform.colorSchemes", "resources.jar")
      addModule("intellij.platform.resources.en", "resources.jar")
      addModule("intellij.platform.jps.model.serialization", "jps-model.jar")
      addModule("intellij.platform.jps.model.impl", "jps-model.jar")

      addModule("intellij.platform.externalSystem.rt", "external-system-rt.jar")

      addModule("intellij.platform.cdsAgent", "cds/classesLogAgent.jar")

      if (allProductDependencies.contains("intellij.platform.coverage")) {
        addModule("intellij.platform.coverage")
      }

      projectLibrariesUsedByPlugins.each {
        if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(it.name) && !layout.excludedProjectLibraries.contains(it.name)) {
          withProjectLibrary(it.name)
        }
      }
      productLayout.projectLibrariesToUnpackIntoMainJar.each {
        withProjectLibraryUnpackedIntoJar(it, productLayout.mainJarName)
      }
      withProjectLibrariesFromIncludedModules(buildContext)

      for (def toRemoveVersion : getLibsToRemoveVersion()) {
        removeVersionFromProjectLibraryJarNames(toRemoveVersion)
      }
    }
  }

  @NotNull Set<PluginLayout> filterPluginsToPublish(@NotNull Set<PluginLayout> plugins) {
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

  private static @NotNull Set<String> getLibsToRemoveVersion() {
    return Set.of("Trove4j", "Log4J", "jna", "jetbrains-annotations-java5", "JDOM")
  }

  private Set<String> getEnabledPluginModules() {
    buildContext.productProperties.productLayout.bundledPluginModules + pluginsToPublish.collect { it.mainModule } as Set<String>
  }

  List<String> getPlatformModules() {
    (platform.moduleJars.values() as List<String>) + toolModules
  }

  static List<String> getIncludedPlatformModules(ProductModulesLayout modulesLayout) {
    CommunityRepositoryModules.PLATFORM_API_MODULES + CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + modulesLayout.productApiModules +
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

  void buildJARs() {
    validateModuleStructure()

    BuildTasksImpl.runInParallel(List.<BuildTaskRunnable<Void>>of(
      SVGPreBuilder.createPrebuildSvgIconsTask(),
      createBuildSearchableOptionsTask(getModulesForPluginsToPublish()),
      createBuildBrokenPluginListTask(),
      createBuildThirdPartyLibrariesListTask(projectStructureMapping)
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

    reorderJars(buildContext)

    buildNonBundledPlugins()
    buildNonBundledPluginsBlockMaps()
  }

  static void reorderJars(@NotNull BuildContext buildContext) {
    if (buildContext.options.buildStepsToSkip.contains(BuildOptions.GENERATE_JAR_ORDER_STEP)) {
      return
    }

    Path result = (Path)BuildHelper.getInstance(buildContext).reorderJars
      .invokeWithArguments(buildContext.paths.distAllDir, buildContext.paths.distAllDir,
                           buildContext.getBootClassPathJarNames(),
                           buildContext.paths.tempDir,
                           buildContext.productProperties.platformPrefix ?: "idea",
                           buildContext.productProperties.isAntRequired ? Paths.get(buildContext.paths.communityHome, "lib/ant/lib") : null,
                           buildContext.messages)
    buildContext.addResourceFile(result)
  }

  private static BuildTaskRunnable<Void> createBuildBrokenPluginListTask() {
    return BuildTaskRunnable.task(BuildOptions.BROKEN_PLUGINS_LIST_STEP, "Build broken plugin list") { BuildContext buildContext ->
      Path targetFile = Paths.get(buildContext.paths.temp, "brokenPlugins.db")
      String currentBuildString = buildContext.buildNumber
      BuildHelper.getInstance(buildContext).brokenPluginsTask.invokeWithArguments(targetFile,
                                                                                  currentBuildString,
                                                                                  buildContext.options.isInDevelopmentMode,
                                                                                  buildContext.messages)
      buildContext.addResourceFile(targetFile)
    }
  }

  /**
   * Validates module structure to be ensure all module dependencies are included
   */
  @CompileStatic
  void validateModuleStructure() {
    if (!buildContext.options.validateModuleStructure)
      return

    def validator = new ModuleStructureValidator(buildContext, platform.moduleJars)
    validator.validate()
  }

  @CompileStatic
  List<String> getProductModules() {
    List<String> result = new ArrayList<>()
    for (moduleJar in platform.moduleJars.entrySet()) {
      // Filter out jars with relative paths in name
      if (moduleJar.key.contains("\\") || moduleJar.key.contains("/"))
        continue

      result.addAll(moduleJar.value)
    }
    return result
  }

  /**
   * Build index which is used to search options in the Settings dialog.
   */
  static BuildTaskRunnable<Void> createBuildSearchableOptionsTask(@NotNull List<String> modulesForPluginsToPublish) {
    BuildTaskRunnable.task(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP, "Build searchable options index", new Consumer<BuildContext>() {
      @Override
      void accept(BuildContext buildContext) {
        ProductModulesLayout productLayout = buildContext.productProperties.productLayout
        List<String> modulesToIndex = productLayout.mainModules + getModulesToCompile(buildContext) + modulesForPluginsToPublish
        modulesToIndex -= "intellij.clion.plugin" // TODO [AK] temporary solution to fix CLion build
        Path targetDirectory = getSearchableOptionsDir(buildContext)
        buildContext.messages.progress("Building searchable options for ${modulesToIndex.size()} modules")
        buildContext.messages.debug("Searchable options are going to be built for the following modules: $modulesToIndex")
        FileUtil.delete(targetDirectory)
        // Start the product in headless mode using com.intellij.ide.ui.search.TraverseUIStarter.
        // It'll process all UI elements in Settings dialog and build index for them.
        BuildTasksImpl.runApplicationStarter(buildContext,
                                             buildContext.paths.tempDir.resolve("searchableOptions"),
                                             modulesToIndex, List.of("traverseUI", targetDirectory.toString(), "true"),
                                             Collections.emptyMap(),
                                             List.of("-ea", "-Xmx1024m"))
        String[] modules = targetDirectory.toFile().list()
        if (modules == null || modules.length == 0) {
          buildContext.messages.error("Failed to build searchable options index: $targetDirectory is empty")
        }
        else {
          buildContext.messages.info("Searchable options are built successfully for $modules.length modules")
          buildContext.messages.debug("The following modules contain searchable options: $modules")
        }
      }
    })
  }

  static List<String> getModulesToCompile(BuildContext buildContext) {
    def productLayout = buildContext.productProperties.productLayout
    def modulesToInclude = productLayout.getIncludedPluginModules(productLayout.bundledPluginModules as Set<String>) +
            CommunityRepositoryModules.PLATFORM_API_MODULES +
            CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES +
            productLayout.productApiModules +
            productLayout.productImplementationModules +
            productLayout.additionalPlatformJars.values() +
            toolModules + buildContext.productProperties.additionalModulesToCompile +
            ["intellij.idea.community.build.tasks", "intellij.platform.images.build"]
    modulesToInclude - productLayout.excludedModuleNames
  }

  List<String> getModulesForPluginsToPublish() {
    return platformModules + pluginsToPublish.collectMany(new LinkedHashSet()) { it.moduleJars.values() }
  }

  void buildAdditionalArtifacts() {
    def productProperties = buildContext.productProperties

    if (productProperties.generateLibrariesLicensesTable && !buildContext.options.buildStepsToSkip.
      contains(BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP)) {
      String artifactNamePrefix = productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
      FileUtil.copy(new File(getThirdPartyLibrariesHtmlFilePath(buildContext)), new File(buildContext.paths.artifacts, "$artifactNamePrefix-third-party-libraries.html"))
      FileUtil.copy(new File(getThirdPartyLibrariesJsonFilePath(buildContext)), new File(buildContext.paths.artifacts, "$artifactNamePrefix-third-party-libraries.json"))
    }

    buildInternalUtilities()

    if (productProperties.buildSourcesArchive) {
      def archiveName = "${productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}-sources.zip"
      BuildTasks.create(buildContext).zipSourcesOfModules(projectStructureMapping.includedModules, "$buildContext.paths.artifacts/$archiveName")
    }
  }

  void generateProjectStructureMapping(File targetFile) {
    LayoutBuilder layoutBuilder = createLayoutBuilder()
    processLibDirectoryLayout(layoutBuilder, projectStructureMapping, false)
    def allPlugins = getPluginsByModules(buildContext, buildContext.productProperties.productLayout.bundledPluginModules)
    def pluginsToBundle = allPlugins.findAll { satisfiesBundlingRequirements(it, null) }
    pluginsToBundle.each {
      processPluginLayout(it, layoutBuilder, buildContext.paths.temp, [], projectStructureMapping, false)
    }
    projectStructureMapping.generateJsonFile(targetFile)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void buildInternalUtilities() {
    if (buildContext.productProperties.scrambleMainJar) {
      createLayoutBuilder().layout("$buildContext.paths.buildOutputRoot/internal") {
        jar("internalUtilities.jar") {
          module("intellij.tools.internalUtilities")
        }
      }
    }
  }

  @NotNull
  private static BuildTaskRunnable<Void> createBuildThirdPartyLibrariesListTask(@NotNull ProjectStructureMapping projectStructureMapping) {
    return BuildTaskRunnable.task(BuildOptions.THIRD_PARTY_LIBRARIES_LIST_STEP,
                                    "Generate table of licenses for used third-party libraries") { buildContext ->
      LibraryLicensesListGenerator generator = LibraryLicensesListGenerator.create(buildContext.messages,
                                                                                   buildContext.project,
                                                                                   buildContext.productProperties.allLibraryLicenses,
                                                                                   projectStructureMapping.includedModules as Set<String>)
      generator.generateHtml(getThirdPartyLibrariesHtmlFilePath(buildContext))
      generator.generateJson(getThirdPartyLibrariesJsonFilePath(buildContext))
    }
  }

  private static String getThirdPartyLibrariesHtmlFilePath(@NotNull BuildContext buildContext) {
    return "$buildContext.paths.distAll/$THIRD_PARTY_LIBRARIES_FILE_PATH"
  }

  private static String getThirdPartyLibrariesJsonFilePath(@NotNull BuildContext buildContext) {
    return "$buildContext.paths.temp/third-party-libraries.json"
  }

  static Map<String, String> getPluginModulesToJar(@NotNull BuildContext buildContext) {
    def pluginsToJar = new HashMap<String, String>()
    def productLayout = buildContext.productProperties.productLayout
    def allPlugins = getPluginsByModules(buildContext, productLayout.bundledPluginModules + productLayout.pluginModulesToPublish)
    for (def plugin : allPlugins) {
      def directory = getActualPluginDirectoryName(plugin, buildContext)
      getModuleToJarMap(plugin, buildContext, pluginsToJar, "$PLUGINS_DIRECTORY/$directory/lib/")
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

  private void buildLib() {
    def layoutBuilder = createLayoutBuilder()
    def productLayout = buildContext.productProperties.productLayout

    addSearchableOptions(layoutBuilder)

    String applicationInfoDir = "$buildContext.paths.temp/applicationInfo"
    Path ideaDir = Paths.get(applicationInfoDir, "idea")
    Files.createDirectories(ideaDir)
    Files.copy(patchedApplicationInfo, ideaDir.resolve(patchedApplicationInfo.fileName), StandardCopyOption.REPLACE_EXISTING)
    layoutBuilder.patchModuleOutput(buildContext.productProperties.applicationInfoModule, applicationInfoDir)

    if (buildContext.productProperties.reassignAltClickToMultipleCarets) {
      layoutBuilder.patchModuleOutput("intellij.platform.resources", createKeyMapWithAltClickReassignedToMultipleCarets())
    }
    if (buildContext.proprietaryBuildTools.featureUsageStatisticsProperties != null) {
      buildContext.executeStep("Bundling a default version of feature usage statistics", BuildOptions.FUS_METADATA_BUNDLE_STEP) {
        try {
          def metadata = StatisticsRecorderBundledMetadataProvider.downloadMetadata(buildContext)
          layoutBuilder.patchModuleOutput('intellij.platform.ide.impl', metadata.absolutePath)
        }
        catch (Exception e) {
          buildContext.messages.warning('Failed to bundle default version of feature usage statistics metadata')
          e.printStackTrace()
        }
      }
    }

    def libDirectoryMapping = new ProjectStructureMapping()
    buildContext.messages.block("Build platform JARs in lib directory") {
      processLibDirectoryLayout(layoutBuilder, projectStructureMapping, true)
    }
    projectStructureMapping.mergeFrom(libDirectoryMapping, "")

    if (buildContext.proprietaryBuildTools.scrambleTool != null) {
      def forbiddenJarNames = buildContext.proprietaryBuildTools.scrambleTool.namesOfJarsRequiredToBeScrambled
      File[] packagedFiles = buildContext.paths.distAllDir.resolve("lib").toFile().listFiles()
      def forbiddenJars = packagedFiles.findAll { forbiddenJarNames.contains(it.name) }
      if (!forbiddenJars.empty) {
        buildContext.messages.error( "The following JARs cannot be included into the product 'lib' directory, they need to be scrambled with the main jar: ${forbiddenJars}")
      }
      def modulesToBeScrambled = buildContext.proprietaryBuildTools.scrambleTool.namesOfModulesRequiredToBeScrambled
      platform.moduleJars.keySet().each { jarName ->
        if (jarName != productLayout.mainJarName) {
          def notScrambled = platform.moduleJars.get(jarName).intersect(modulesToBeScrambled)
          if (!notScrambled.isEmpty()) {
            buildContext.messages.error("Module '${notScrambled.first()}' is included into $jarName which is not scrambled.")
          }
        }
      }
    }
  }

  void processLibDirectoryLayout(LayoutBuilder layoutBuilder, ProjectStructureMapping projectStructureMapping, boolean copyFiles) {
    processLayout(layoutBuilder, platform, buildContext.paths.distAll, projectStructureMapping, copyFiles, platform.moduleJars, [])
  }

  private void buildBundledPlugins() {
    def layoutBuilder = createLayoutBuilder()
    def allPlugins = getPluginsByModules(buildContext, buildContext.productProperties.productLayout.bundledPluginModules)
    def pluginDirectoriesToSkip = buildContext.options.bundledPluginDirectoriesToSkip as Set<String>
    buildContext.messages.debug("Plugin directories to skip: " + pluginDirectoriesToSkip)
    buildContext.messages.block("Build bundled plugins") {
      def pluginsToBundle = allPlugins.findAll {
        satisfiesBundlingRequirements(it, null) && !pluginDirectoriesToSkip.contains(it.directoryName)
      }
      buildPlugins(layoutBuilder, pluginsToBundle, "$buildContext.paths.distAll$PLUGINS_DIRECTORY", projectStructureMapping)
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
      List<PluginLayout> osSpecificPlugins = getPluginsByModules(buildContext, productLayout.bundledPluginModules).findAll {
        satisfiesBundlingRequirements(it, osFamily)
      }

      if (!osSpecificPlugins.isEmpty() && buildContext.shouldBuildDistributionForOS(osFamily.osId)) {
        LayoutBuilder layoutBuilder = createLayoutBuilder()
        buildContext.messages.block("Build bundled plugins for $osFamily.osName") {
          buildPlugins(layoutBuilder, osSpecificPlugins,
                       "$buildContext.paths.buildOutputRoot/dist.$osFamily.distSuffix/plugins", projectStructureMapping)
        }
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  void buildNonBundledPlugins() {
    if (pluginsToPublish.isEmpty()) return

    def productLayout = buildContext.productProperties.productLayout
    def ant = buildContext.ant
    def layoutBuilder = createLayoutBuilder()
    buildContext.executeStep("Build non-bundled plugins", BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
      def pluginsToPublishDir = "$buildContext.paths.temp/${buildContext.applicationInfo.productCode}-plugins-to-publish"
      buildPlugins(layoutBuilder, new ArrayList<PluginLayout>(pluginsToPublish), pluginsToPublishDir, null)

      def pluginVersion = buildContext.buildNumber.endsWith(".SNAPSHOT")
        ? buildContext.buildNumber + ".${new SimpleDateFormat('yyyyMMdd').format(new Date())}"
        : buildContext.buildNumber
      def pluginsDirectoryName = "${buildContext.applicationInfo.productCode}-plugins"
      def nonBundledPluginsArtifacts = "$buildContext.paths.artifacts/$pluginsDirectoryName"
      def pluginsToIncludeInCustomRepository = new ArrayList<PluginRepositorySpec>()
      def whiteList = new File("$buildContext.paths.communityHome/../build/plugins-autoupload-whitelist.txt").readLines()
        .stream().map { it.trim() }.filter { !it.isEmpty() && !it.startsWith("//") }.collect(Collectors.toSet())

      pluginsToPublish.each { plugin ->
        def directory = getActualPluginDirectoryName(plugin, buildContext)
        def targetDirectory = whiteList.contains(plugin.mainModule)
          ? "$nonBundledPluginsArtifacts/auto-uploading"
          : nonBundledPluginsArtifacts
        def destFile = "$targetDirectory/$directory-${pluginVersion}.zip"

        if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
          def pluginXmlPath = "$buildContext.paths.temp/patched-plugin-xml/$plugin.mainModule/META-INF/plugin.xml"
          if (!new File(pluginXmlPath).exists()) {
            buildContext.messages.error("patched plugin.xml not found for $plugin.mainModule module: $pluginXmlPath")
          }
          pluginsToIncludeInCustomRepository.add(new PluginRepositorySpec(pluginZip: destFile.toString(), pluginXml: pluginXmlPath))
        }

        ant.zip(destfile: destFile) {
          zipfileset(dir: "$pluginsToPublishDir/$directory", prefix: directory)
        }
        buildContext.notifyArtifactBuilt(destFile)
      }

      KeymapPluginsBuilder.buildKeymapPlugins(buildContext, "$nonBundledPluginsArtifacts/auto-uploading").forEach {
        if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
          pluginsToIncludeInCustomRepository.add(it)
        }
      }

      def helpPlugin = BuiltInHelpPlugin.helpPlugin(buildContext, pluginVersion)
      if (helpPlugin != null) {
        def spec = buildHelpPlugin(helpPlugin, pluginsToPublishDir, "$nonBundledPluginsArtifacts/auto-uploading", layoutBuilder)
        if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
          pluginsToIncludeInCustomRepository.add(spec)
        }
      }

      if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
        new PluginRepositoryXmlGenerator(buildContext).generate(pluginsToIncludeInCustomRepository, nonBundledPluginsArtifacts)
        buildContext.notifyArtifactBuilt("$nonBundledPluginsArtifacts/plugins.xml")
      }
    }
  }

  /**
   * This function builds a blockmap and hash files for each non bundled plugin
   * to provide downloading plugins via incremental downloading algorithm Blockmap.
   */
  private void buildNonBundledPluginsBlockMaps(){
    def pluginsDirectoryName = "${buildContext.applicationInfo.productCode}-plugins"
    def nonBundledPluginsArtifacts = "$buildContext.paths.artifacts/$pluginsDirectoryName"
    Path path = Paths.get(nonBundledPluginsArtifacts)
    if (!Files.exists(path)) {
      return
    }

    Files.walk(path)
      .filter({ it -> (it.toString().endsWith(".zip") && Files.isRegularFile(it)) })
      .forEach { Path file ->
        Path blockMapFile = file.parent.resolve("${file.fileName}.blockmap.zip")
        Path hashFile = file.parent.resolve("${file.fileName}.hash.json")
        String blockMapJson = "blockmap.json"
        String algorithm = "SHA-256"
        new BufferedInputStream(Files.newInputStream(file)).withCloseable { input ->
          BlockMap blockMap = new BlockMap(input, algorithm)
          new BufferedOutputStream(Files.newOutputStream(blockMapFile)).withCloseable { output ->
            writeBlockMapToZip(output, JsonOutput.toJson(blockMap).bytes, blockMapJson)
          }
        }
        new BufferedInputStream(Files.newInputStream(file)).withCloseable { input ->
          Files.writeString(hashFile, JsonOutput.toJson(new FileHash(input, algorithm)))
        }
      }
  }

  private static void writeBlockMapToZip(OutputStream output, byte[] bytes, String blockMapJson){
    new BufferedOutputStream(output).withStream {bufferedOutput ->
      new ZipOutputStream(bufferedOutput).withStream { zipOutputStream ->
        def entry = new ZipEntry(blockMapJson)
        zipOutputStream.putNextEntry(entry)
        zipOutputStream.write(bytes)
        zipOutputStream.closeEntry()
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private PluginRepositorySpec buildHelpPlugin(PluginLayout helpPlugin, String pluginsToPublishDir, String targetDir, LayoutBuilder layoutBuilder) {
    def directory = getActualPluginDirectoryName(helpPlugin, buildContext)
    def destFile = "${targetDir}/${directory}.zip"
    def patchedPluginXmlDir = "$buildContext.paths.temp/patched-plugin-xml/$helpPlugin.mainModule"
    layoutBuilder.patchModuleOutput(helpPlugin.mainModule, patchedPluginXmlDir)
    buildContext.messages.block("Building $directory plugin") {
      buildPlugins(layoutBuilder, new ArrayList<PluginLayout>([helpPlugin]), pluginsToPublishDir, null)
      buildContext.ant.zip(destfile: destFile) {
        zipfileset(dir: "$pluginsToPublishDir/$directory", prefix: directory)
      }
    }
    buildContext.notifyArtifactBuilt(destFile)
    def pluginXmlPath = "$patchedPluginXmlDir/META-INF/plugin.xml"
    return new PluginRepositorySpec(pluginZip: destFile,
                                    pluginXml: pluginXmlPath)
  }

  /**
   * Returns name of directory in the product distribution where plugin will be placed. For plugins which use the main module name as the
   * directory name return the old module name to temporary keep layout of plugins unchanged.
   */
  static String getActualPluginDirectoryName(PluginLayout plugin, BuildContext context) {
    if (!plugin.directoryNameSetExplicitly && plugin.directoryName == BaseLayout.convertModuleNameToFileName(plugin.mainModule)
                                           && context.getOldModuleName(plugin.mainModule) != null) {
      context.getOldModuleName(plugin.mainModule)
    }
    else {
      return plugin.directoryName
    }
  }

  static List<PluginLayout> getPluginsByModules(BuildContext buildContext, Collection<String> modules) {
    def allNonTrivialPlugins = buildContext.productProperties.productLayout.allNonTrivialPlugins
    def nonTrivialPlugins = allNonTrivialPlugins.groupBy { it.mainModule }
    modules.collect { (nonTrivialPlugins[it] ?: nonTrivialPlugins[buildContext.findModule(it)?.name])?.first() ?: PluginLayout.plugin(it) }
  }

  private void buildPlugins(LayoutBuilder layoutBuilder, List<PluginLayout> pluginsToInclude, String targetDirectory,
                            ProjectStructureMapping parentMapping) {
    addSearchableOptions(layoutBuilder)
    pluginsToInclude.each { plugin ->
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

      String targetDir = "$targetDirectory/${getActualPluginDirectoryName(plugin, buildContext)}"
      processPluginLayout(plugin, layoutBuilder, targetDir, generatedResources, parentMapping, true)
      if (buildContext.proprietaryBuildTools.scrambleTool != null) {
        buildContext.proprietaryBuildTools.scrambleTool.scramblePlugin(buildContext, plugin, targetDir)
      }
      else if (!plugin.pathsToScramble.isEmpty()){
        buildContext.messages.warning("Scrambling plugin $plugin.directoryName skipped: 'scrambleTool' isn't defined, but plugin defines paths to be scrambled")
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
    CompatibleBuildRange compatibleBuildRange = bundled ||
            //plugins included into the built-in custom plugin repository should use EXACT range because such custom repositories are used for nightly builds and there may be API differences between different builds
            includeInBuiltinCustomRepository ? CompatibleBuildRange.EXACT :
                    //when publishing plugins with EAP build let's use restricted range to ensure that users will update to a newer version of the plugin when they update to the next EAP or release build
                    buildContext.applicationInfo.isEAP ? CompatibleBuildRange.RESTRICTED_TO_SAME_RELEASE
                            : CompatibleBuildRange.NEWER_WITH_SAME_BASELINE

    def defaultPluginVersion = buildContext.buildNumber.endsWith(".SNAPSHOT")
      ? buildContext.buildNumber + ".${new SimpleDateFormat('yyyyMMdd').format(new Date())}"
      : buildContext.buildNumber

    def pluginVersion = plugin.versionEvaluator.apply(patchedPluginXmlFile, defaultPluginVersion)

    setPluginVersionAndSince(patchedPluginXmlFile, pluginVersion, compatibleBuildRange, pluginsToPublish.contains(plugin))
    layoutBuilder.patchModuleOutput(plugin.mainModule, patchedPluginXmlDir)
  }

  private void processPluginLayout(PluginLayout plugin, LayoutBuilder layoutBuilder, String targetDir,
                                   List<Pair<File, String>> generatedResources, ProjectStructureMapping parentMapping, boolean copyFiles) {
    def mapping = new ProjectStructureMapping()
    processLayout(layoutBuilder, plugin, targetDir, mapping, copyFiles, plugin.moduleJars, generatedResources)
    if (parentMapping != null) {
      parentMapping.mergeFrom(mapping, "plugins/${getActualPluginDirectoryName(plugin, buildContext)}")
    }
  }

  private void addSearchableOptions(LayoutBuilder layoutBuilder) {
    if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      Path searchableOptionsDir = getSearchableOptionsDir(buildContext)
      if (!Files.exists(searchableOptionsDir)) {
        buildContext.messages.error("There are no searchable options available. " +
                                    "Please ensure that you call DistributionJARsBuilder#buildSearchableOptions before this method.")
      }
      searchableOptionsDir.eachFile(FileType.DIRECTORIES) {
        layoutBuilder.patchModuleOutput(it.fileName.toString(), it)
      }
    }
  }

  @NotNull
  private static Path getSearchableOptionsDir(@NotNull BuildContext buildContext) {
    return buildContext.paths.tempDir.resolve("searchableOptions/result")
  }

  void checkOutputOfPluginModules(String mainPluginModule, MultiMap<String, String> moduleJars, MultiMap<String, String> moduleExcludes) {
    // Don't check modules which are not direct children of lib/ directory
    def modulesWithPluginXml = moduleJars.entrySet().stream()
      .filter { !it.key.contains("/") }
      .flatMap { it.value.stream() }
      .filter { containsFileInOutput(it, "META-INF/plugin.xml", moduleExcludes.get(it)) }
      .collect(Collectors.toList()) as List<String>
    if (modulesWithPluginXml.size() > 1) {
      buildContext.messages.error("Multiple modules (${modulesWithPluginXml.join(", ")}) from '$mainPluginModule' plugin contain plugin.xml files so the plugin won't work properly")
    }
    if (modulesWithPluginXml.size() == 0) {
      buildContext.messages.error("No module from '$mainPluginModule' plugin contains plugin.xml")
    }

    moduleJars.values().each {
      if (it != "intellij.java.guiForms.rt" && containsFileInOutput(it, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(it))) {
        buildContext.messages.error("Runtime classes of GUI designer must not be packaged to '$it' module in '$mainPluginModule' plugin, because they are included into a platform JAR. " +
                                    "Make sure that 'Automatically copy form runtime classes to the output directory' is disabled in Settings | Editor | GUI Designer.")
      }
    }
  }

  private boolean containsFileInOutput(String moduleName, String filePath, Collection<String> excludes) {
    def moduleOutput = new File(buildContext.getModuleOutputPath(buildContext.findRequiredModule(moduleName)))
    def fileInOutput = new File(moduleOutput, filePath)
    return fileInOutput.exists() && (excludes == null || excludes.every {
      createFileSet(it, moduleOutput).iterator().every { !(it instanceof FileProvider && FileUtil.filesEqual(it.file, fileInOutput))}
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
  void processLayout(LayoutBuilder layoutBuilder, BaseLayout layout, String targetDirectory,
                             ProjectStructureMapping mapping, boolean copyFiles,
                             MultiMap<String, String> moduleJars,
                             List<Pair<File, String>> additionalResources) {
    def ant = buildContext.ant
    def resourceExcluded = RESOURCES_EXCLUDED
    def resourcesIncluded = RESOURCES_INCLUDED
    def buildContext = buildContext
    if (copyFiles) {
      checkModuleExcludes(layout.moduleExcludes)
    }
    MultiMap<String, String> actualModuleJars = MultiMap.createLinked()
    moduleJars.entrySet().each {
      def modules = it.value
      def jarPath = getActualModuleJarPath(it.key, modules, layout.explicitlySetJarPaths, buildContext)
      actualModuleJars.putValues(jarPath, modules)
    }
    layoutBuilder.process(targetDirectory, mapping, copyFiles) {
      dir("lib") {
        actualModuleJars.entrySet().each {
          def modules = it.value
          def jarPath = it.key
          jar(jarPath, true) {
            modules.each { moduleName ->
              modulePatches([moduleName]) {
                if (layout.localizableResourcesJarName(moduleName) != null) {
                  ant.patternset(refid: resourceExcluded)
                }
              }
              module(moduleName) {
                if (layout.localizableResourcesJarName(moduleName) != null) {
                  ant.patternset(refid: resourceExcluded)
                }
                else {
                  ant.exclude(name: "**/icon-robots.txt")
                }

                layout.moduleExcludes.get(moduleName).each {
                  //noinspection GrUnresolvedAccess
                  ant.exclude(name: it)
                }
              }
            }
            layout.projectLibrariesToUnpack.get(jarPath).each {
              buildContext.project.libraryCollection.findLibrary(it)?.getFiles(JpsOrderRootType.COMPILED)?.each {
                if (copyFiles) {
                  ant.zipfileset(src: it.absolutePath)
                }
              }
            }
          }
        }
        MultiMap<String, String> outputResourceJars = MultiMap.createLinked()
        actualModuleJars.values().forEach {
          def resourcesJarName = layout.localizableResourcesJarName(it)
          if (resourcesJarName != null) {
            outputResourceJars.putValue(resourcesJarName, it)
          }
        }
        if (!outputResourceJars.empty) {
          outputResourceJars.keySet().forEach { resourceJarName ->
            jar(resourceJarName, true) {
              outputResourceJars.get(resourceJarName).each { moduleName ->
                modulePatches([moduleName]) {
                  ant.patternset(refid: resourcesIncluded)
                }
                module(moduleName) {
                  layout.moduleExcludes.get(moduleName).each {
                    //noinspection GrUnresolvedAccess
                    ant.exclude(name: "$it/**")
                  }
                  ant.patternset(refid: resourcesIncluded)
                }
              }
            }
          }
        }
        layout.includedProjectLibraries.each { libraryData ->
          dir(libraryData.relativeOutputPath) {
            projectLibrary(libraryData.libraryName, layout instanceof PlatformLayout && layout.projectLibrariesWithRemovedVersionFromJarNames.contains(libraryData.libraryName))
          }
        }
        layout.includedArtifacts.entrySet().each {
          def artifactName = it.key
          def relativePath = it.value
          dir(relativePath) {
            artifact(artifactName)
          }
        }

        //include all module libraries from the plugin modules added to IDE classpath to layout
        actualModuleJars.entrySet()
          .stream()
          .filter { !it.key.contains("/") }
          .flatMap { it.value.stream() }
          .filter { !layout.modulesWithExcludedModuleLibraries.contains(it) }
          .forEach { moduleName ->
            def excluded = layout.excludedModuleLibraries.get(moduleName)
            findModule(moduleName).dependenciesList.dependencies.stream()
              .filter { it instanceof JpsLibraryDependency && it?.libraryReference?.parentReference?.resolve() instanceof JpsModule }
              .filter { JpsJavaExtensionService.instance.getDependencyExtension(it)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) ?: false }
              .map { ((JpsLibraryDependency)it).library }
              .filter { !excluded.contains(getLibraryName(it)) }
              .forEach {
                jpsLibrary(it)
              }
          }

        layout.includedModuleLibraries.each { data ->
          dir(data.relativeOutputPath) {
            moduleLibrary(data.moduleName, data.libraryName)
          }
        }
      }
      if (copyFiles) {
        layout.resourcePaths.each {
          def path = FileUtil.toSystemIndependentName(new File("${basePath(buildContext, it.moduleName)}/$it.resourcePath").absolutePath)
          if (it.packToZip) {
            zip(it.relativeOutputPath) {
              if (new File(path).isFile()) {
                ant.fileset(file: path)
              }
              else {
                ant.fileset(dir: path)
              }
            }
          }
          else {
            dir(it.relativeOutputPath) {
              if (new File(path).isFile()) {
                ant.fileset(file: path)
              }
              else {
                ant.fileset(dir: path)
              }
            }
          }
        }
        additionalResources.each {
          File resource = it.first
          dir(it.second) {
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
    moduleExcludes.entrySet().each { entry ->
      String module = entry.key
      entry.value.each { pattern ->
        def moduleOutput = new File(buildContext.getModuleOutputPath(buildContext.findRequiredModule(module)))
        if (!moduleOutput.exists()) {
          buildContext.messages.error("There are excludes defined for module '$module', but the module wasn't compiled; " +
                                      "most probably it means that '$module' isn't include into the product distribution so it makes no sense to define excludes for it.")
        }
        if (createFileSet(pattern, moduleOutput).size() == 0) {
          buildContext.messages.error("Incorrect excludes for module '$module': nothing matches to $pattern in the module output at $moduleOutput")
        }
      }
    }
  }

  private FileSet createFileSet(String pattern, File baseDir) {
    def fileSet = new FileSet()
    fileSet.setProject(buildContext.ant.antProject)
    fileSet.setDir(baseDir)
    fileSet.createInclude().setName(pattern)
    return fileSet
  }

  static String basePath(BuildContext buildContext, String moduleName) {
    JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first())
  }

  private LayoutBuilder createLayoutBuilder() {
    new LayoutBuilder(buildContext, COMPRESS_JARS)
  }

  private void setPluginVersionAndSince(@NotNull Path pluginXmlFile, String pluginVersion, CompatibleBuildRange compatibleBuildRange, boolean toPublish) {
    Pair<String, String> sinceUntil = getCompatiblePlatformVersionRange(compatibleBuildRange, buildContext.buildNumber)
    def text = Files.readString(pluginXmlFile)
            .replaceFirst(
                    "<version>[\\d.]*</version>",
                    "<version>${pluginVersion}</version>")
            .replaceFirst(
                    "<idea-version\\s+since-build=\"(\\d+\\.)+\\d+\"\\s+until-build=\"(\\d+\\.)+\\d+\"",
                    "<idea-version since-build=\"${sinceUntil.first}\" until-build=\"${sinceUntil.second}\"")
            .replaceFirst(
                    "<idea-version\\s+since-build=\"(\\d+\\.)+\\d+\"",
                    "<idea-version since-build=\"${sinceUntil.first}\"")
            .replaceFirst(
                    "<change-notes>\\s+<\\!\\[CDATA\\[\\s*Plugin version: \\\$\\{version\\}",
                    "<change-notes>\n<![CDATA[\nPlugin version: ${pluginVersion}")

    if (text.contains("<product-descriptor ")) {
      def eapAttribute = buildContext.applicationInfo.isEAP ? "eap=\"true\"" : ""
      def releaseDate = buildContext.applicationInfo.majorReleaseDate ?:
              ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("uuuuMMdd"))
      def releaseVersion = "${buildContext.applicationInfo.majorVersion}${buildContext.applicationInfo.minorVersionMainPart}00"
      text = text.replaceFirst(
              "<product-descriptor code=\"([\\w]*)\"\\s+release-date=\"[^\"]*\"\\s+release-version=\"[^\"]*\"([^/]*)/>",
              !toPublish ? "" :
              "<product-descriptor code=\"\$1\" release-date=\"$releaseDate\" release-version=\"$releaseVersion\" $eapAttribute \$2 />")
      buildContext.messages.info("        ${toPublish ? "Patching" : "Skipping"} ${pluginXmlFile.parent.parent.fileName} <product-descriptor/>")
    }

    def anchor = text.contains("</id>") ? "</id>" : "</name>"
    if (!text.contains("<version>")) {
      text = text.replace(anchor, "${anchor}\n  <version>${pluginVersion}</version>")
    }
    if (!text.contains("<idea-version since-build")) {
      text = text.replace(anchor, "${anchor}\n  <idea-version since-build=\"${sinceUntil.first}\" until-build=\"${sinceUntil.second}\"/>")
    }
    Files.writeString(pluginXmlFile, text)
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
