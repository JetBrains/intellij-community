// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.MultiValuesMap
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.types.resources.FileProvider
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil
/**
 * Assembles output of modules to platform JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAll distAll}/lib directory),
 * bundled plugins' JARs (in {@link org.jetbrains.intellij.build.BuildPaths#distAll distAll}/plugins directory) and zip archives with
 * non-bundled plugins (in {@link org.jetbrains.intellij.build.BuildPaths#artifacts artifacts}/plugins directory).
 *
 * @author nik
 */
class DistributionJARsBuilder {
  private static final boolean COMPRESS_JARS = false
  private static final String RESOURCES_INCLUDED = "resources.included"
  private static final String RESOURCES_EXCLUDED = "resources.excluded"
  private final BuildContext buildContext
  private final Set<String> usedModules = new LinkedHashSet<>()
  private final PlatformLayout platform
  private final File patchedApplicationInfo
  private final List<PluginLayout> pluginsToPublish

  DistributionJARsBuilder(BuildContext buildContext, File patchedApplicationInfo, List<PluginLayout> pluginsToPublish) {
    this.patchedApplicationInfo = patchedApplicationInfo
    this.buildContext = buildContext
    this.pluginsToPublish = pluginsToPublish
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
    List<JpsLibrary> projectLibrariesUsedByPlugins = getPluginsByModules(buildContext, enabledPluginModules).collectMany { plugin ->
      plugin.getActualModules(enabledPluginModules).values().collectMany {
        def module = buildContext.findRequiredModule(it)
        JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll {
          !(it.createReference().parentReference instanceof JpsModuleReference) && !plugin.includedProjectLibraries.contains(it.name)
        }
      }
    }

    Set<String> allProductDependencies = (productLayout.getIncludedPluginModules(enabledPluginModules) + getIncludedPlatformModules(productLayout)).collectMany(new LinkedHashSet<String>()) {
      JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(it)).productionOnly().getModules().collect {it.name}
    }

    platform = PlatformLayout.platform(productLayout.platformLayoutCustomizer) {
      productLayout.additionalPlatformJars.entrySet().each {
        def jarName = it.key
        it.value.each {
          withModule(it, jarName)
        }
      }
      getPlatformApiModules(productLayout).each {
        withModule(it, "platform-api.jar")
      }
      getPlatformImplModules(productLayout).each {
        withModule(it, "platform-impl.jar")
      }
      getProductApiModules(productLayout).each {
        withModule(it, "openapi.jar")
      }
      getProductImplModules(productLayout).each {
        withModule(it, productLayout.mainJarName)
      }
      productLayout.moduleExcludes.entrySet().each {
        layout.moduleExcludes.putAll(it.key, it.value)
      }
      withModule("util")
      withModule("util-rt", "util.jar")
      withModule("annotations")
      withModule("annotations-common", "annotations.jar")
      withModule("extensions")
      withModule("bootstrap")
      withModule("forms_rt")
      withModule("icons")
      withModule("boot")
      withModule("platform-resources", "resources.jar")
      withModule("colorSchemes", "resources.jar")
      withModule("platform-resources-en", productLayout.mainJarName)
      withModule("jps-model-serialization", "jps-model.jar")
      withModule("jps-model-impl", "jps-model.jar")

      if (allProductDependencies.contains("coverage-common") && !productLayout.bundledPluginModules.contains("coverage")) {
        withModule("coverage-common", productLayout.mainJarName)
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
      removeVersionFromProjectLibraryJarNames("Trove4j")
    }
  }

  private Set<String> getEnabledPluginModules() {
    buildContext.productProperties.productLayout.bundledPluginModules + pluginsToPublish.collect { it.mainModule } as Set<String>
  }

  List<String> getPlatformModules() {
    (platform.moduleJars.values() as List<String>) + toolModules
  }

  static List<String> getIncludedPlatformModules(ProductModulesLayout modulesLayout) {
    getPlatformApiModules(modulesLayout) + getPlatformImplModules(modulesLayout) + getProductApiModules(modulesLayout) +
    getProductImplModules(modulesLayout) + modulesLayout.additionalPlatformJars.values()
  }

  /**
   * @return module names which are required to run necessary tools from build scripts
   */
  static List<String> getToolModules() {
    ["java-runtime", "platform-main", /*required to build searchable options index*/ "updater"]
  }

  static List<String> getPlatformApiModules(ProductModulesLayout productLayout) {
    productLayout.platformApiModules.isEmpty() ? CommunityRepositoryModules.PLATFORM_API_MODULES : []
  }

  static List<String> getPlatformImplModules(ProductModulesLayout productLayout) {
    productLayout.platformImplementationModules.isEmpty() ? CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES : []
  }

  static List<String> getProductApiModules(ProductModulesLayout productLayout) {
    productLayout.platformApiModules.isEmpty() ? productLayout.productApiModules : productLayout.platformApiModules
  }

  static List<String> getProductImplModules(ProductModulesLayout productLayout) {
    productLayout.platformImplementationModules.isEmpty() ? productLayout.productImplementationModules : productLayout.platformImplementationModules
  }

  Collection<String> getIncludedProjectArtifacts() {
    platform.includedArtifacts.keySet() + pluginsToPublish.collectMany {it.includedArtifacts.keySet()}
  }

  void buildJARs() {
    buildLib()
    buildBundledPlugins()
    buildNonBundledPlugins()

    def loadingOrderFilePath = buildContext.productProperties.productLayout.classesLoadingOrderFilePath
    if (loadingOrderFilePath != null) {
      reorderJARs(loadingOrderFilePath)
    }
  }

  void reorderJARs(String loadingOrderFilePath) {
    buildContext.messages.block("Reorder JARs") {
      String targetDirectory = buildContext.paths.distAll
      buildContext.messages.progress("Reordering *.jar files in $targetDirectory")
      File ignoredJarsFile = new File(buildContext.paths.temp, "reorder-jars/required_for_dist.txt")
      ignoredJarsFile.parentFile.mkdirs()
      def moduleJars = platform.moduleJars.entrySet().collect(new HashSet()) { getActualModuleJarPath(it.key, it.value) }
      ignoredJarsFile.text = new File(buildContext.paths.distAll, "lib").list()
        .findAll {it.endsWith(".jar") && !moduleJars.contains(it)}
        .join("\n")

      buildContext.ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: true, failonerror: true) {
        arg(value: loadingOrderFilePath)
        arg(value: targetDirectory)
        arg(value: targetDirectory)
        arg(value: ignoredJarsFile.parent)
        classpath {
          buildContext.getModuleRuntimeClasspath(buildContext.findRequiredModule("util"), false).each {
            pathelement(location: it)
          }
        }
      }
    }
  }

  void buildAdditionalArtifacts() {
    def productProperties = buildContext.productProperties
    if (productProperties.generateLibrariesLicensesTable) {
      buildContext.messages.block("Generate table of licenses for used third-party libraries") {
        def generator = new LibraryLicensesListGenerator(buildContext.messages, buildContext.project, productProperties.allLibraryLicenses)
        def artifactNamePrefix = buildContext.productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)
        generator.generateLicensesTable("$buildContext.paths.artifacts/$artifactNamePrefix-third-party-libraries.txt", usedModules)
      }
    }

    if (productProperties.scrambleMainJar) {
      createLayoutBuilder().layout("$buildContext.paths.buildOutputRoot/internal") {
        jar("internalUtilities.jar") {
          module("internalUtilities")
        }
      }
    }

    if (productProperties.buildSourcesArchive) {
      def archiveName = "${productProperties.getBaseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}-sources.zip"
      BuildTasks.create(buildContext).zipSourcesOfModules(usedModules, "$buildContext.paths.artifacts/$archiveName")
    }
  }

  private void buildLib() {
    def ant = buildContext.ant
    def layoutBuilder = createLayoutBuilder()
    def productLayout = buildContext.productProperties.productLayout
    def searchableOptionsDir = new File(buildContext.paths.temp, "searchableOptions/result")

    //todo[nik] move buildSearchableOptions and patchedApplicationInfo methods to this class
    def buildTasks = new BuildTasksImpl(buildContext)
    buildTasks.buildSearchableOptionsIndex(searchableOptionsDir, productLayout.mainModules)
    if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      layoutBuilder.patchModuleOutput(productLayout.searchableOptionsModule, FileUtil.toSystemIndependentName(searchableOptionsDir.absolutePath))
    }

    def applicationInfoFile = FileUtil.toSystemIndependentName(patchedApplicationInfo.absolutePath)
    def applicationInfoDir = "$buildContext.paths.temp/applicationInfo"
    ant.copy(file: applicationInfoFile, todir: "$applicationInfoDir/idea")
    layoutBuilder.patchModuleOutput(buildContext.productProperties.applicationInfoModule, applicationInfoDir)

    if (buildContext.productProperties.reassignAltClickToMultipleCarets) {
      def patchedKeyMapDir = createKeyMapWithAltClickReassignedToMultipleCarets()
      layoutBuilder.patchModuleOutput("platform-resources", FileUtil.toSystemIndependentName(patchedKeyMapDir.absolutePath))
    }

    buildByLayout(layoutBuilder, platform, buildContext.paths.distAll, platform.moduleJars, [])

    if (buildContext.proprietaryBuildTools.scrambleTool != null) {
      def forbiddenJarNames = buildContext.proprietaryBuildTools.scrambleTool.namesOfJarsRequiredToBeScrambled
      def packagedFiles = new File(buildContext.paths.distAll, "lib").listFiles()
      def forbiddenJars = packagedFiles.findAll { forbiddenJarNames.contains(it.name) }
      if (!forbiddenJars.empty) {
        buildContext.messages.error( "The following JARs cannot be included into the product 'lib' directory, they need to be scrambled with the main jar: ${forbiddenJars}")
      }
    }

    usedModules.addAll(layoutBuilder.usedModules)
  }

  private void buildBundledPlugins() {
    def productLayout = buildContext.productProperties.productLayout
    def layoutBuilder = createLayoutBuilder()
    buildPlugins(layoutBuilder, getPluginsByModules(buildContext, productLayout.bundledPluginModules), "$buildContext.paths.distAll/plugins")
    usedModules.addAll(layoutBuilder.usedModules)
  }

  void buildNonBundledPlugins() {
    def productLayout = buildContext.productProperties.productLayout
    def ant = buildContext.ant
    def layoutBuilder = createLayoutBuilder()
    buildContext.executeStep("Build non-bundled plugins", BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
      if (buildContext.productProperties.setPluginAndIDEVersionInPluginXml) {
        pluginsToPublish.each { plugin ->
          def moduleOutput = buildContext.getModuleOutputPath(buildContext.findRequiredModule(plugin.mainModule))
          def pluginXmlPath = "$moduleOutput/META-INF/plugin.xml"
          if (!new File(pluginXmlPath)) {
            buildContext.messages.error("plugin.xml not found in $plugin.mainModule module: $pluginXmlPath")
          }
          def patchedPluginXmlDir = "$buildContext.paths.temp/patched-plugin-xml/$plugin.mainModule"
          ant.copy(file: pluginXmlPath, todir: "$patchedPluginXmlDir/META-INF")
          setPluginVersionAndSince("$patchedPluginXmlDir/META-INF/plugin.xml", getPluginVersion(plugin),
                                   buildContext.buildNumber,
                                   productLayout.prepareCustomPluginRepositoryForPublishedPlugins,
                                   productLayout.pluginModulesWithRestrictedCompatibleBuildRange.contains(plugin.mainModule))
          layoutBuilder.patchModuleOutput(plugin.mainModule, patchedPluginXmlDir)
        }
      }

      def pluginsToPublishDir = "$buildContext.paths.temp/${buildContext.productProperties.productCode}-plugins-to-publish"
      def pluginsDirectoryName = "${buildContext.productProperties.productCode}-plugins"
      buildPlugins(layoutBuilder, pluginsToPublish, pluginsToPublishDir)
      def nonBundledPluginsArtifacts = "$buildContext.paths.artifacts/$pluginsDirectoryName"
      def pluginZipFiles = new LinkedHashMap<PluginLayout, String>()
      pluginsToPublish.each { plugin ->
        def directory = getActualPluginDirectoryName(plugin, buildContext)
        String suffix = productLayout.prepareCustomPluginRepositoryForPublishedPlugins ? "" : "-${getPluginVersion(plugin)}"
        def destFile = "$nonBundledPluginsArtifacts/$directory${suffix}.zip"
        pluginZipFiles[plugin] = destFile.toString()
        ant.zip(destfile: destFile) {
          zipfileset(dir: "$pluginsToPublishDir/$directory", prefix: directory)
        }
        buildContext.notifyArtifactBuilt(destFile)
      }
      if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
        new PluginRepositoryXmlGenerator(buildContext).generate(pluginsToPublish, pluginZipFiles, nonBundledPluginsArtifacts)
        buildContext.notifyArtifactBuilt("$nonBundledPluginsArtifacts/plugins.xml")
      }
    }
  }

  private String getPluginVersion(PluginLayout plugin) {
    return plugin.versionEvaluator.apply(buildContext)
  }

  /**
   * Returns name of directory in the product distribution where plugin will be placed. For plugins which use the main module name as the
   * directory name return the old module name to temporary keep layout of plugins unchanged.
   */
  static String getActualPluginDirectoryName(PluginLayout plugin, BuildContext context) {
    if (plugin.directoryName == plugin.mainModule && context.getOldModuleName(plugin.mainModule) != null) {
      context.getOldModuleName(plugin.mainModule)
    }
    else {
      plugin.directoryName
    }
  }

  static List<PluginLayout> getPluginsByModules(BuildContext buildContext, Collection<String> modules) {
    def allNonTrivialPlugins = buildContext.productProperties.productLayout.allNonTrivialPlugins
    def allOptionalModules = allNonTrivialPlugins.collectMany {it.optionalModules}
    def nonTrivialPlugins = allNonTrivialPlugins.groupBy { it.mainModule }
    (modules - allOptionalModules).collect { (nonTrivialPlugins[it] ?: nonTrivialPlugins[buildContext.findModule(it)?.name])?.first() ?: PluginLayout.plugin(it) }
  }

  private void buildPlugins(LayoutBuilder layoutBuilder, List<PluginLayout> pluginsToInclude, String targetDirectory) {
    def enabledModulesSet = enabledPluginModules
    pluginsToInclude.each { plugin ->
      def actualModuleJars = plugin.getActualModules(enabledModulesSet)
      checkOutputOfPluginModules(plugin.mainModule, actualModuleJars.values(), plugin.moduleExcludes)
      List<Pair<File, String>> generatedResources = plugin.resourceGenerators.collectMany {
        File resourceFile = it.first.generateResources(buildContext)
        resourceFile != null ? [Pair.create(resourceFile, it.second)] : []
      }
      buildByLayout(layoutBuilder, plugin, "$targetDirectory/${getActualPluginDirectoryName(plugin, buildContext)}", actualModuleJars, generatedResources)
    }
  }

  private void checkOutputOfPluginModules(String mainPluginModule, Collection<String> moduleNames, MultiValuesMap<String, String> moduleExcludes) {
    def modulesWithPluginXml = moduleNames.findAll { containsFileInOutput(it, "META-INF/plugin.xml", moduleExcludes.get(it)) }
    if (modulesWithPluginXml.size() > 1) {
      buildContext.messages.error("Multiple modules (${modulesWithPluginXml.join(", ")}) from '$mainPluginModule' plugin contain plugin.xml files so the plugin won't work properly")
    }

    moduleNames.each {
      if (containsFileInOutput(it, "com/intellij/uiDesigner/core/GridLayoutManager.class", moduleExcludes.get(it))) {
        buildContext.messages.error("Runtime classes of GUI designer must not be packaged to '$mainPluginModule' plugin, because they are included into a platform JAR. " +
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
  private String getActualModuleJarPath(String relativeJarPath, Collection<String> moduleNames) {
    for (String moduleName : moduleNames) {
      if (relativeJarPath == "${moduleName}.jar" && buildContext.getOldModuleName(moduleName) != null) {
        return "${buildContext.getOldModuleName(moduleName)}.jar"
      }
    }
    return relativeJarPath
  }

  /**
   * @param moduleJars mapping from JAR path relative to 'lib' directory to names of modules
   * @param additionalResources pairs of resources files and corresponding relative output paths
   */
  private void buildByLayout(LayoutBuilder layoutBuilder, BaseLayout layout, String targetDirectory, MultiValuesMap<String, String> moduleJars,
                             List<Pair<File, String>> additionalResources) {
    def ant = buildContext.ant
    def resourceExcluded = RESOURCES_EXCLUDED
    def resourcesIncluded = RESOURCES_INCLUDED
    def buildContext = buildContext
    checkModuleExcludes(layout.moduleExcludes)
    layoutBuilder.layout(targetDirectory) {
      dir("lib") {
        moduleJars.entrySet().each {
          def modules = it.value
          def jarPath = getActualModuleJarPath(it.key, modules)
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
                layout.moduleExcludes.get(moduleName)?.each {
                  //noinspection GrUnresolvedAccess
                  ant.exclude(name: it)
                }
              }
            }
            layout.projectLibrariesToUnpack.get(jarPath)?.each {
              buildContext.project.libraryCollection.findLibrary(it)?.getFiles(JpsOrderRootType.COMPILED)?.each {
                ant.zipfileset(src: it.absolutePath)
              }
            }
          }
        }
        def outputResourceJars = new MultiValuesMap<String, String>()
        moduleJars.values().forEach {
          def resourcesJarName = layout.localizableResourcesJarName(it)
          if (resourcesJarName != null) {
            outputResourceJars.put(resourcesJarName, it)
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
                  layout.moduleExcludes.get(moduleName)?.each {
                    //noinspection GrUnresolvedAccess
                    ant.exclude(name: "$it/**")
                  }
                  ant.patternset(refid: resourcesIncluded)
                }
              }
            }
          }
        }
        layout.includedProjectLibraries.each {
          projectLibrary(it, layout instanceof PlatformLayout && layout.projectLibrariesWithRemovedVersionFromJarNames.contains(it))
        }
        layout.includedArtifacts.entrySet().each {
          def artifactName = it.key
          def relativePath = it.value
          dir(relativePath) {
            artifact(artifactName)
          }
        }

        //include all module libraries from the plugin modules added to IDE classpath to layout
        moduleJars.entrySet().findAll { !it.key.contains("/") }.collectMany { it.value }
                             .findAll {!layout.modulesWithExcludedModuleLibraries.contains(it)}.each { moduleName ->
          findModule(moduleName).dependenciesList.dependencies.
            findAll { it instanceof JpsLibraryDependency && it?.libraryReference?.parentReference?.resolve() instanceof JpsModule }.
            findAll { JpsJavaExtensionService.instance.getDependencyExtension(it)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) ?: false }.
            each {
              jpsLibrary(((JpsLibraryDependency)it).library)
            }
        }

        layout.includedModuleLibraries.each { data ->
          dir(data.relativeOutputPath) {
            moduleLibrary(data.moduleName, data.libraryName)
          }
        }
      }
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

  private void checkModuleExcludes(MultiValuesMap<String, String> moduleExcludes) {
    moduleExcludes.entrySet().each { entry ->
      String module = entry.key
      entry.value.each { pattern ->
        def moduleOutput = new File(buildContext.getModuleOutputPath(buildContext.findRequiredModule(module)))
        if (!moduleOutput.exists()) {
          buildContext.messages.error("There are excludes defined for module '$module', but the module wasn't compiled; " +
                                      "most probably it means that '$module' isn't include into the product distribution so it makes no sense to define excludes for it.")
        }
        if (createFileSet(pattern, moduleOutput).size() == 0) {
          buildContext.messages.error("Incorrect exludes for module '$module': nothing matches to $pattern in the module output")
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

  private void setPluginVersionAndSince(String pluginXmlPath, String version, String buildNumber, boolean setExactNumberInUntilBuild, boolean useRestrictedCompatibleBuildRange) {
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<version>[\\d.]*</version>",
                                   replace: "<version>${version}</version>")
    def sinceBuild
    def untilBuild
    if (!setExactNumberInUntilBuild && buildNumber.matches(/(\d+\.)+\d+/)) {
      if (buildNumber.matches(/\d+\.\d+/)) {
        sinceBuild = buildNumber
      }
      else {
        sinceBuild = buildNumber.substring(0, buildNumber.lastIndexOf('.'))
      }
      int end = useRestrictedCompatibleBuildRange ? buildNumber.lastIndexOf('.') : buildNumber.indexOf('.')
      untilBuild = buildNumber.substring(0, end) + ".*"
    }
    else {
      sinceBuild = buildNumber
      untilBuild = buildNumber
    }
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<idea-version\\s*since-build=\"\\d+\\.\\d+\"\\s*until-build=\"\\d+\\.\\d+\"",
                                   replace: "<idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"")
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<idea-version\\s*since-build=\"\\d+\\.\\d+\"",
                                   replace: "<idea-version since-build=\"${sinceBuild}\"")
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<change-notes>\\s*<\\!\\[CDATA\\[\\s*Plugin version: \\\$\\{version\\}",
                                   replace: "<change-notes>\n<![CDATA[\nPlugin version: ${version}")
    def file = new File(pluginXmlPath)
    def text = file.text
    def anchor = text.contains("</id>") ? "</id>" : "</name>"
    if (!text.contains("<version>")) {
      file.text = text.replace(anchor, "${anchor}\n  <version>${version}</version>")
      text = file.text
    }
    if (!text.contains("<idea-version since-build")) {
      file.text = text.replace(anchor, "${anchor}\n  <idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"/>")
    }
  }

  private File createKeyMapWithAltClickReassignedToMultipleCarets() {
    def sourceFile = new File("${buildContext.getModuleOutputPath(buildContext.findModule("platform-resources"))}/keymaps/\$default.xml")
    String defaultKeymapContent = sourceFile.text
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt button1\"/>")
    def patchedKeyMapDir = new File(buildContext.paths.temp, "patched-keymap")
    def targetFile = new File(patchedKeyMapDir, "keymaps/\$default.xml")
    FileUtil.createParentDirs(targetFile)
    targetFile.text = defaultKeymapContent
    return patchedKeyMapDir
  }
}
