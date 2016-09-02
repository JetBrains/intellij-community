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
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleReference

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

  DistributionJARsBuilder(BuildContext buildContext) {
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

    Set<String> allProductDependencies = (productLayout.getIncludedPluginModules(buildContext.productProperties.allPlugins) + productLayout.includedPlatformModules).collectMany(new LinkedHashSet<String>()) {
      JpsJavaExtensionService.dependencies(buildContext.findRequiredModule(it)).productionOnly().getModules().collect {it.name}
    }

    platform = PlatformLayout.platform {
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
      if (allProductDependencies.contains("coverage-common")) {
        withModule("coverage-common", productLayout.mainJarName)
      }

      ["linux", "macosx", "win"].each {
        withResource("lib/libpty/$it", "lib/libpty/$it")
      }

      projectLibrariesUsedByPlugins.each {
        if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(it.name)) {
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
    ["java-runtime" /*required to build searchable options index*/, "updater"]

  }

  void buildJARs() {
    buildLib()
    buildPlugins()

    def productProperties = buildContext.productProperties
    if (productProperties.generateLibrariesLicensesTable) {
      buildContext.messages.block("Generate table of licenses for used third-party libraries") {
        def generator = new LibraryLicensesListGenerator(buildContext.projectBuilder, buildContext.project, productProperties.allLibraryLicenses)
        generator.generateLicensesTable("$buildContext.paths.artifacts/${buildContext.applicationInfo.productName}-third-party-libraries.txt", usedModules)
      }
    }

    if (productProperties.scrambleMainJar) {
      createLayoutBuilder().layout(buildContext.paths.artifacts) {
        jar("internalUtilities.jar") {
          module("internalUtilities")
        }
      }
    }
  }

  private void buildLib() {
    def ant = buildContext.ant
    def layoutBuilder = createLayoutBuilder()
    def productLayout = buildContext.productProperties.productLayout
    def searchableOptionsDir = new File(buildContext.paths.temp, "searchableOptions/result")

    //todo[nik] move buildSearchableOptions and patchedApplicationInfo methods to this class
    def buildTasks = new BuildTasksImpl(buildContext)
    buildTasks.buildSearchableOptions(searchableOptionsDir, [productLayout.mainModule], productLayout.licenseFilesToBuildSearchableOptions)
    if (!buildContext.options.buildStepsToSkip.contains(BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP)) {
      layoutBuilder.patchModuleOutput(productLayout.searchableOptionsModule, FileUtil.toSystemIndependentName(searchableOptionsDir.absolutePath))
    }

    def applicationInfoFile = FileUtil.toSystemIndependentName(buildTasks.patchApplicationInfo().absolutePath)
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

    if (buildContext.productProperties.setPluginAndIDEVersionInPluginXml) {
      def pluginsToBuild = getPluginsByModules(productLayout.pluginModulesToPublish)
      pluginsToBuild.each { plugin ->
        def moduleOutput = buildContext.projectBuilder.moduleOutput(buildContext.findRequiredModule(plugin.mainModule))
        def pluginXmlPath = "$moduleOutput/META-INF/plugin.xml"
        if (!new File(pluginXmlPath)) {
          buildContext.messages.error("plugin.xml not found in $plugin.mainModule module: $pluginXmlPath")
        }
        def patchedPluginXmlDir = "$buildContext.paths.temp/patched-plugin-xml/$plugin.mainModule"
        ant.copy(file: pluginXmlPath, todir: "$patchedPluginXmlDir/META-INF")
        setPluginVersionAndSince("$patchedPluginXmlDir/META-INF/plugin.xml", buildContext.buildNumber)
        layoutBuilder.patchModuleOutput(plugin.mainModule, patchedPluginXmlDir)
      }
    }

    buildPlugins(layoutBuilder, getPluginsByModules(productLayout.bundledPluginModules), "$buildContext.paths.distAll/plugins")
    usedModules.addAll(layoutBuilder.usedModules)

    def pluginsToPublishDir = "$buildContext.paths.temp/plugins-to-publish"
    def pluginsToPublish = getPluginsByModules(productLayout.pluginModulesToPublish)
    buildPlugins(layoutBuilder, pluginsToPublish, pluginsToPublishDir)
    pluginsToPublish.each { plugin ->
      def directory = plugin.directoryName
      ant.zip(destfile: "$buildContext.paths.artifacts/plugins/$directory-${buildContext.buildNumber}.zip") {
        zipfileset(dir: "$pluginsToPublishDir/$directory", prefix: directory)
      }
    }
  }

  private List<PluginLayout> getPluginsByModules(Collection<String> modules) {
    def modulesToInclude = modules as Set<String>
    buildContext.productProperties.allPlugins.findAll { modulesToInclude.contains(it.mainModule) }
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
            modulePatches(modules)
            modules.each { moduleName ->
              module(moduleName) {
                if (layout.packLocalizableResourcesInCommonJar(moduleName)) {
                  ant.patternset(refid: resourceExcluded)
                }
                layout.moduleExcludes.get(moduleName)?.each {
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
            modulesWithResources.each {
              modulePatches([it]) {
                ant.patternset(refid: resourcesIncluded)
              }
              module(it) {
                ant.patternset(refid: resourcesIncluded)
              }
            }
          }
        }
        layout.includedProjectLibraries.each {
          projectLibrary(it)
        }

        //include all module libraries from the plugin modules added to IDE classpath to layout
        moduleJars.entrySet().findAll { !it.key.contains("/") }.collectMany { it.value }.each { moduleName ->
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
      layout.resourcePaths.entrySet().each {
        def path = FileUtil.toSystemIndependentName(new File("${layout.basePath(buildContext)}/$it.key").absolutePath)
        dir(it.value) {
          if (new File(path).isFile()) {
            ant.fileset(file: path)
          }
          else {
            ant.fileset(dir: path)
          }
        }
      }
      layout.resourceArchivePaths.entrySet().each {
        def path = "${layout.basePath(buildContext)}/$it.key"
        zip(it.value) {
          ant.fileset(dir: path)
        }
      }
    }
  }

  private LayoutBuilder createLayoutBuilder() {
    new LayoutBuilder(buildContext.ant, buildContext.project, COMPRESS_JARS)
  }

  private void setPluginVersionAndSince(String pluginXmlPath, String buildNumber) {
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                      match: "<version>[\\d.]*</version>",
                      replace: "<version>${buildNumber}</version>")
    def sinceBuild = buildNumber.matches(/\d+\.\d+\.\d+/) ? buildNumber.substring(0, buildNumber.lastIndexOf('.')) : buildNumber;
    def dotIndex = buildNumber.indexOf('.')
    def untilBuild = dotIndex > 0 ? Integer.parseInt(buildNumber.substring(0, dotIndex)) + ".*" : buildNumber
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
    if (!text.contains("<version>")) {
      def anchor = text.contains("</id>") ? "</id>" : "</name>"
      file.text = text.replace(anchor,
                               "${anchor}\n  <version>${buildNumber}</version>\n  <idea-version since-build=\"${sinceBuild}\" until-build=\"${untilBuild}\"/>\n")
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
