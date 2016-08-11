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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtilRt
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsLibraryDependency
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil

/**
 * @author nik
 */
class DistributionJARsBuilder {
  public static final List<String> ADDITIONAL_MODULES_TO_COMPILE = [
    "java-runtime",//required to build searchable options index
    "colorSchemes", "platform-resources", "platform-resources-en", "boot", "icons", "forms_rt", "bootstrap"
  ]
  private static final boolean COMPRESS_JARS = false
  private static final String RESOURCES_INCLUDED = "resources.included"
  private static final String RESOURCES_EXCLUDED = "resources.excluded"
  private final BuildContext buildContext
  private final List<PluginLayout> allPlugins
  private final Set<String> usedModules = new LinkedHashSet<>()
  private final List<String> includedModules

  DistributionJARsBuilder(BuildContext buildContext, List<String> includedModules, List<PluginLayout> allPlugins) {
    this.includedModules = includedModules
    this.buildContext = buildContext
    this.allPlugins = allPlugins
    buildContext.ant.patternset(id: RESOURCES_INCLUDED) {
      include(name: "**/*.properties")
      include(name: "fileTemplates/**")
      include(name: "inspectionDescriptions/**")
      include(name: "intentionDescriptions/**")
      include(name: "tips/**")
      include(name: "search/**")
    }

    buildContext.ant.patternset(id: RESOURCES_EXCLUDED) {
      exclude(name: "**/*.properties")
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

    Set<String> usedJars = collectUsedJars(includedModules, []) - productLayout.additionalJarsToUnpackIntoMainJar.collect {FileUtil.toSystemIndependentName(it)}

    if (buildContext.scrambleTool != null) {
      def forbiddenJarNames = buildContext.scrambleTool.namesOfJarsRequiredToBeScrambled
      def forbiddenJars = usedJars.findAll { forbiddenJarNames.contains(PathUtilRt.getFileName(it)) }
      if (!forbiddenJars.empty) {
        buildContext.messages.error("The following JARs cannot be included into the product 'lib' directory, they need to be scrambled with the main jar: ${forbiddenJars}")
      }
    }

    def communityHome = "$buildContext.paths.communityHome"
    def resourcesIncluded = RESOURCES_INCLUDED
    def resourcesExcluded = RESOURCES_EXCLUDED

    layoutBuilder.layout("$buildContext.paths.distAll/lib") {
      jar("util.jar") {
        module("util")
        module("util-rt")
      }
      jar("openapi.jar") {
        productLayout.platformApiModules.each { module it }
      }

      jar("annotations.jar") {
        module("annotations-common")
        module("annotations")
      }
      jar("extensions.jar") { module("extensions") }
      jar("bootstrap.jar") { module("bootstrap") }
      jar("resources.jar", true) {
        modulePatches(["platform-resources"])
        module("colorSchemes")
        module("platform-resources")
      }

      jar("forms_rt.jar") { module("forms_rt") }

      productLayout.additionalPlatformModules.entrySet().findAll { it.value != productLayout.mainJarName }.each {
        def moduleName = it.key
        jar(it.value) {
          module(moduleName) {
            ant.patternset(refid: resourcesExcluded)
          }
        }
      }

      jar("resources_en.jar", true) {
        productLayout.additionalPlatformModules.keySet().each {
          modulePatches([it]) {
            ant.patternset(refid: resourcesIncluded)
          }
          module(it) {
            ant.patternset(refid: resourcesIncluded)
          }
        }
        module("platform-resources-en")
        module("coverage-common") {
          ant.patternset(refid: resourcesIncluded)
        }
      }

      jar("icons.jar") { module("icons") }
      jar("boot.jar") { module("boot") }
      projectLibrary("KotlinJavaRuntime")

      jar(productLayout.mainJarName, true, false) {
        modulePatches(productLayout.platformImplementationModules)
        productLayout.platformImplementationModules.each { module it }
        module("coverage-common") {
          ant.patternset(refid: resourcesExcluded)
        }
        productLayout.additionalPlatformModules.entrySet().findAll {it.value == productLayout.mainJarName}.each {
          modulePatches([it.key]) {
            ant.patternset(refid: resourcesExcluded)
          }
          module(it.key) {
            ant.patternset(refid: resourcesExcluded)
          }
        }
        productLayout.additionalJarsToUnpackIntoMainJar.each {
          ant.zipfileset(src: it)
        }
      }

      usedJars.each {
        ant.fileset(file: it)
      }

      dir("libpty") {
        ant.fileset(dir: "$communityHome/lib/libpty") {
          exclude(name: "*.txt")
        }
      }

      dir("src") {
        ant.fileset(dir: "$communityHome/lib/src") {
          include(name: "trove4j_changes.txt")
          include(name: "trove4j_src.jar")
        }
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

  private List<PluginLayout> getPluginsByModules(List<String> modules) {
    def modulesToInclude = modules as Set<String>
    allPlugins.findAll { modulesToInclude.contains(it.mainModule) }
  }

  private buildPlugins(LayoutBuilder layoutBuilder, List<PluginLayout> pluginsToInclude, String targetDirectory) {
    def ant = buildContext.ant
    def resourceExcluded = RESOURCES_EXCLUDED
    def resourcesIncluded = RESOURCES_INCLUDED
    def enabledModulesSet = buildContext.productProperties.productLayout.enabledPluginModules
    layoutBuilder.layout(targetDirectory) {
      pluginsToInclude.each { plugin ->
        dir(plugin.directoryName) {
          dir("lib") {
            def actualModuleJars = plugin.getActualModules(enabledModulesSet)
            actualModuleJars.entrySet().each {
              def modules = it.value
              def jarPath = it.key
              jar(jarPath, true) {
                modulePatches(modules)
                modules.each { moduleName ->
                  module(moduleName) {
                    if (plugin.packLocalizableResourcesInCommonJar(moduleName)) {
                      ant.patternset(refid: resourceExcluded)
                    }
                    plugin.moduleExcludes.get(moduleName)?.each {
                      ant.exclude(name: "$it/**")
                    }
                  }
                }
              }
            }
            def modulesWithResources = actualModuleJars.values().findAll { plugin.packLocalizableResourcesInCommonJar(it) }
            if (!modulesWithResources.empty) {
              jar("resources_en.jar") {
                modulesWithResources.each {
                  module(it) {
                    ant.patternset(refid: resourcesIncluded)
                  }
                }
              }
            }
            plugin.includedProjectLibraries.each {
              projectLibrary(it)
            }

            //include all module libraries from the plugin modules added to IDE classpath to layout
            actualModuleJars.entrySet().findAll {!it.key.contains("/")}.collectMany {it.value}.each { moduleName ->
              findModule(moduleName).dependenciesList.dependencies.
                findAll { it instanceof JpsLibraryDependency && it?.libraryReference?.parentReference?.resolve() instanceof JpsModule}.
                findAll { JpsJavaExtensionService.instance.getDependencyExtension(it)?.scope?.isIncludedIn(JpsJavaClasspathKind.PRODUCTION_RUNTIME) ?: false}.
                each {
                  jpsLibrary(((JpsLibraryDependency)it).library)
                }
            }

            plugin.includedModuleLibraries.each { data ->
              dir(data.relativeOutputPath) {
                moduleLibrary(data.moduleName, data.libraryName)
              }
            }
          }
          plugin.resourcePaths.entrySet().each {
            def contentRoot = JpsPathUtil.urlToPath(findModule(plugin.mainModule).contentRootsList.urls.first())
            def path = "$contentRoot/$it.key"
            dir(it.value) {
              if (new File(path).isFile()) {
                ant.fileset(file: path)
              }
              else {
                ant.fileset(dir: path)
              }
            }
          }
          plugin.resourceArchivePaths.entrySet().each {
            def contentRoot = JpsPathUtil.urlToPath(findModule(plugin.mainModule).contentRootsList.urls.first())
            def path = "$contentRoot/$it.key"
            zip(it.value) {
              ant.fileset(dir: path)
            }
          }
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
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                      match: "<idea-version\\s*since-build=\"\\d+\\.\\d+\"",
                      replace: "<idea-version since-build=\"${buildNumber}\"")
    buildContext.ant.replaceregexp(file: pluginXmlPath,
                      match: "<change-notes>\\s*<\\!\\[CDATA\\[\\s*Plugin version: \\\$\\{version\\}",
                      replace: "<change-notes>\n<![CDATA[\nPlugin version: ${buildNumber}")
    def file = new File(pluginXmlPath)
    def text = file.text
    if (!text.contains("<version>")) {
      def dotIndex = buildNumber.indexOf('.')
      def untilBuild = dotIndex > 0 ? Integer.parseInt(buildNumber.substring(0, dotIndex)) + ".*" : buildNumber
      def anchor = text.contains("</id>") ? "</id>" : "</name>"
      file.text = text.replace(anchor,
                               "${anchor}\n  <version>${buildNumber}</version>\n  <idea-version since-build=\"${buildNumber}\" until-build=\"${untilBuild}\"/>\n")
    }
  }

  private Set<String> collectUsedJars(List<String> modules, List<String> additionalLibFolders) {
    def usedJars = new LinkedHashSet<String>();
    List<String> approvedJars =
      (["$buildContext.paths.communityHome/lib", "$buildContext.paths.projectHome/lib", "$buildContext.paths.communityHome/xml/relaxng/lib"] as List<String>) +
      additionalLibFolders

    modules.each {
      def module = buildContext.findModule(it)
      if (module != null) {
        buildContext.projectBuilder.moduleRuntimeClasspath(module, false).each {
          File file = new File(it)
          if (file.exists()) {
            String path = FileUtil.toSystemIndependentName(file.canonicalPath)
            if (path.endsWith(".jar") && approvedJars.any { FileUtil.startsWith(path, it) }) {
              if (usedJars.add(path)) {
                buildContext.messages.info("\tADDED: $path for $module.name")
              }
            }
          }
        }
      }
    }

    return usedJars
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
