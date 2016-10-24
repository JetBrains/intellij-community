/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.MultiValuesMap
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference
import org.jetbrains.jps.util.JpsPathUtil

/**
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

  DistributionJARsBuilder(BuildContext buildContext, File patchedApplicationInfo) {
    this.patchedApplicationInfo = patchedApplicationInfo
    this.buildContext = buildContext
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

    List<JpsLibrary> projectLibrariesUsedByPlugins = getPluginsByModules(productLayout.enabledPluginModules).collectMany { plugin ->
      plugin.getActualModules(productLayout.enabledPluginModules).values().collectMany {
        def module = buildContext.findRequiredModule(it)
        JpsJavaExtensionService.dependencies(module).includedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME).libraries.findAll {
          !(it.createReference().parentReference instanceof JpsModuleReference) && !plugin.includedProjectLibraries.contains(it.name)
        }
      }
    }

    Set<String> allProductDependencies = (productLayout.includedPluginModules + productLayout.includedPlatformModules).collectMany(new LinkedHashSet<String>()) {
      JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(it)).productionOnly().getModules().collect {it.name}
    }

    platform = PlatformLayout.platform(productLayout.platformLayoutCustomizer) {
      productLayout.additionalPlatformJars.entrySet().each {
        def jarName = it.key
        it.value.each {
          withModule(it, jarName)
        }
      }
      productLayout.platformApiModules.each {
        withModule(it, "openapi.jar")
      }
      productLayout.platformImplementationModules.each {
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
    }
  }

  List<String> getPlatformModules() {
    (platform.moduleJars.values() as List<String>) +
    ["java-runtime", "platform-main", /*required to build searchable options index*/
     "updater"] + buildContext.productProperties.productLayout.mainModules

  }

  void buildJARs() {
    buildLib()
    buildPlugins()

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
      ignoredJarsFile.text = new File(buildContext.paths.distAll, "lib").list()
        .findAll {it.endsWith(".jar") && !platform.moduleJars.containsKey(it)}
        .join("\n")

      buildContext.ant.java(classname: "com.intellij.util.io.zip.ReorderJarsMain", fork: true, failonerror: true) {
        arg(value: loadingOrderFilePath)
        arg(value: targetDirectory)
        arg(value: targetDirectory)
        arg(value: ignoredJarsFile.parent)
        classpath {
          buildContext.projectBuilder.moduleRuntimeClasspath(buildContext.findRequiredModule("util"), false).each {
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
        def generator = new LibraryLicensesListGenerator(buildContext.projectBuilder, buildContext.project, productProperties.allLibraryLicenses)
        generator.generateLicensesTable("$buildContext.paths.artifacts/${buildContext.applicationInfo.productName}-third-party-libraries.txt", usedModules)
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
      def archiveName = "${productProperties.baseArtifactName(buildContext.applicationInfo, buildContext.buildNumber)}-sources.zip"
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
    buildTasks.buildSearchableOptions(searchableOptionsDir, productLayout.mainModules, productLayout.licenseFilesToBuildSearchableOptions)
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

    buildByLayout(layoutBuilder, platform, buildContext.paths.distAll, platform.moduleJars)

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

  private void buildPlugins() {
    def ant = buildContext.ant
    def productLayout = buildContext.productProperties.productLayout
    def layoutBuilder = createLayoutBuilder()
    buildPlugins(layoutBuilder, getPluginsByModules(productLayout.bundledPluginModules), "$buildContext.paths.distAll/plugins")
    usedModules.addAll(layoutBuilder.usedModules)

    buildContext.executeStep("Build non-bundled plugins", BuildOptions.NON_BUNDLED_PLUGINS_STEP) {
      def pluginsToPublish = getPluginsByModules(productLayout.pluginModulesToPublish)
      if (buildContext.productProperties.setPluginAndIDEVersionInPluginXml) {
        pluginsToPublish.each { plugin ->
          def moduleOutput = buildContext.projectBuilder.moduleOutput(buildContext.findRequiredModule(plugin.mainModule))
          def pluginXmlPath = "$moduleOutput/META-INF/plugin.xml"
          if (!new File(pluginXmlPath)) {
            buildContext.messages.error("plugin.xml not found in $plugin.mainModule module: $pluginXmlPath")
          }
          def patchedPluginXmlDir = "$buildContext.paths.temp/patched-plugin-xml/$plugin.mainModule"
          ant.copy(file: pluginXmlPath, todir: "$patchedPluginXmlDir/META-INF")
          setPluginVersionAndSince("$patchedPluginXmlDir/META-INF/plugin.xml", buildContext.buildNumber,
                                   productLayout.prepareCustomPluginRepositoryForPublishedPlugins)
          layoutBuilder.patchModuleOutput(plugin.mainModule, patchedPluginXmlDir)
        }
      }

      def pluginsToPublishDir = "$buildContext.paths.temp/plugins-to-publish"
      buildPlugins(layoutBuilder, pluginsToPublish, pluginsToPublishDir)
      def nonBundledPluginsArtifacts = "$buildContext.paths.artifacts/plugins"
      pluginsToPublish.each { plugin ->
        def directory = plugin.directoryName
        String suffix = productLayout.prepareCustomPluginRepositoryForPublishedPlugins ? "" : "-${buildContext.buildNumber}"
        ant.zip(destfile: "$nonBundledPluginsArtifacts/$directory${suffix}.zip") {
          zipfileset(dir: "$pluginsToPublishDir/$directory", prefix: directory)
        }
      }
      if (productLayout.prepareCustomPluginRepositoryForPublishedPlugins) {
        new PluginRepositoryXmlGenerator(buildContext).generate(pluginsToPublish, nonBundledPluginsArtifacts)
      }
    }
  }

  private List<PluginLayout> getPluginsByModules(Collection<String> modules) {
    def allNonTrivialPlugins = buildContext.productProperties.productLayout.allNonTrivialPlugins
    def allOptionalModules = allNonTrivialPlugins.collectMany {it.optionalModules}
    def nonTrivialPlugins = allNonTrivialPlugins.groupBy { it.mainModule }
    (modules - allOptionalModules).collect { nonTrivialPlugins[it]?.first() ?: PluginLayout.plugin(it) }
  }

  private void buildPlugins(LayoutBuilder layoutBuilder, List<PluginLayout> pluginsToInclude, String targetDirectory) {
    def enabledModulesSet = buildContext.productProperties.productLayout.enabledPluginModules
    pluginsToInclude.each { plugin ->
      def actualModuleJars = plugin.getActualModules(enabledModulesSet)
      buildByLayout(layoutBuilder, plugin, "$targetDirectory/$plugin.directoryName", actualModuleJars)
    }
  }

  private void buildByLayout(LayoutBuilder layoutBuilder, BaseLayout layout, String targetDirectory, MultiValuesMap<String, String> moduleJars) {
    def ant = buildContext.ant
    def resourceExcluded = RESOURCES_EXCLUDED
    def resourcesIncluded = RESOURCES_INCLUDED
    def buildContext = buildContext
    layoutBuilder.layout(targetDirectory) {
      dir("lib") {
        moduleJars.entrySet().each {
          def modules = it.value
          def jarPath = it.key
          jar(jarPath, true) {
            modules.each { moduleName ->
              modulePatches([moduleName]) {
                if (layout.packLocalizableResourcesInCommonJar(moduleName)) {
                  ant.patternset(refid: resourceExcluded)
                }
              }
              module(moduleName) {
                if (layout.packLocalizableResourcesInCommonJar(moduleName)) {
                  ant.patternset(refid: resourceExcluded)
                }
                layout.moduleExcludes.get(moduleName)?.each {
                  //noinspection GrUnresolvedAccess
                  ant.exclude(name: "$it/**")
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
        def modulesWithResources = moduleJars.values().findAll { layout.packLocalizableResourcesInCommonJar(it) }
        if (!modulesWithResources.empty) {
          jar("resources_en.jar", true) {
            modulesWithResources.each { moduleName ->
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
        layout.includedProjectLibraries.each {
          projectLibrary(it)
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
    }
  }

  static String basePath(BuildContext buildContext, String moduleName) {
    JpsPathUtil.urlToPath(buildContext.findRequiredModule(moduleName).contentRootsList.urls.first())
  }


  private LayoutBuilder createLayoutBuilder() {
    new LayoutBuilder(buildContext.ant, buildContext.project, COMPRESS_JARS)
  }

  private void setPluginVersionAndSince(String pluginXmlPath, String buildNumber, boolean setExactNumberInUntilBuild) {
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<version>[\\d.]*</version>",
                                   replace: "<version>${buildNumber}</version>")
    def sinceBuild = buildNumber.matches(/\d+\.\d+\.\d+/) ? buildNumber.substring(0, buildNumber.lastIndexOf('.')) : buildNumber;
    def dotIndex = buildNumber.indexOf('.')
    def untilBuild
    if (setExactNumberInUntilBuild) {
      untilBuild = buildNumber
    }
    else {
      untilBuild = dotIndex > 0 ? Integer.parseInt(buildNumber.substring(0, dotIndex)) + ".*" : buildNumber
    }
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<idea-version\\s*since-build=\"\\d+\\.\\d+\"\\s*until-build=\"\\d+\\.\\d+\"",
                                   replace: "<idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"")
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<idea-version\\s*since-build=\"\\d+\\.\\d+\"",
                                   replace: "<idea-version since-build=\"${sinceBuild}\"")
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                                   match: "<change-notes>\\s*<\\!\\[CDATA\\[\\s*Plugin version: \\\$\\{version\\}",
                                   replace: "<change-notes>\n<![CDATA[\nPlugin version: ${buildNumber}")
    def file = new File(pluginXmlPath)
    def text = file.text
    def anchor = text.contains("</id>") ? "</id>" : "</name>"
    if (!text.contains("<version>")) {
      file.text = text.replace(anchor, "${anchor}\n  <version>${buildNumber}</version>")
      text = file.text
    }
    if (!text.contains("<idea-version since-build")) {
      file.text = text.replace(anchor, "${anchor}\n  <idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"/>")
    }
  }

  private File createKeyMapWithAltClickReassignedToMultipleCarets() {
    def sourceFile = new File("${buildContext.projectBuilder.moduleOutput(buildContext.findModule("platform-resources"))}/idea/Keymap_Default.xml")
    String defaultKeymapContent = sourceFile.text
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt button1\"/>", "")
    defaultKeymapContent = defaultKeymapContent.replace("<mouse-shortcut keystroke=\"alt shift button1\"/>",
                                                        "<mouse-shortcut keystroke=\"alt button1\"/>")
    def patchedKeyMapDir = new File(buildContext.paths.temp, "patched-keymap")
    def targetFile = new File(patchedKeyMapDir, "idea/Keymap_Default.xml")
    FileUtil.createParentDirs(targetFile)
    targetFile.text = defaultKeymapContent
    return patchedKeyMapDir
  }
}
