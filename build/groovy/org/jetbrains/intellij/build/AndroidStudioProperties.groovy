/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import kotlin.Pair
import org.jetbrains.intellij.build.io.FileKt
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.module.JpsModule

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

// Based on the IdeaCommunityProperties definition
// TODO: Need Windows installer and Mac DMG images
// TODO: Need uninstaller feedback URL
// Consider switching from AI to AS code
// Restore a lot of the custom logic from studio_properties
// TODO: Use separate bundle identifier for EAP and non-EAP
@CompileStatic
class AndroidStudioProperties extends BaseIdeaProperties {

  private static final List<String> INHERITED_PLUGINS = ProductModulesLayout.DEFAULT_BUNDLED_PLUGINS + BUNDLED_PLUGIN_MODULES

  private static final List<String> EXTRA_PLUGINS = List.of(
    // Android Studio: package CIDR plugins. This list is based on what we have been shipping in Android Studio
    // and the structure of CIDR plugins.
    "intellij.c.clangd",
    "intellij.c.clangdBridge",
    "intellij.c.plugin",
    "intellij.cidr.debugger.plugin",
    "intellij.cidr.base.plugin",
    )

  private static final List<String> EXCLUDED_PLUGINS = List.of(
    "intellij.android.design-plugin",
    "intellij.android.gradle.dsl",
    "intellij.android.plugin",
    "intellij.android.smali",
    "intellij.ant",
    "intellij.eclipse",
    "intellij.featuresTrainer",
    "intellij.gradle.dependencyUpdater",
    "intellij.gradle.java.maven",
    "intellij.grazie",
    "intellij.java.byteCodeViewer",
    "intellij.java.guiForms.designer",
    "intellij.javaFX.community",
    "intellij.lombok",
    "intellij.maven",
    "intellij.packageSearch",
    "intellij.platform.tracing.ide",
    "intellij.searchEverywhereMl",
    "intellij.statsCollector",
    "intellij.vcs.git.featuresTrainer",
    "intellij.xpath",
    "intellij.xslt.debugger",
    KotlinPluginBuilder.MAIN_KOTLIN_PLUGIN_MODULE,
  )

  AndroidStudioProperties(Path home, BuildOptions buildOptions) {
    baseFileName = "studio"
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "intellij.android.adt.branding"
    useSplash = true
    additionalIDEPropertiesFilePaths = List.of(home.resolve("build/conf/ideaCE.properties"))
    toolsJarRequired = true
    scrambleMainJar = false
    buildSourcesArchive = true;
    buildCrossPlatformDistribution = true

    allLibraryLicenses.addAll(AndroidStudioLibraryLicenses.LICENSES_LIST)
    includeIntoSourcesArchiveFilter = { JpsModule module, BuildContext buildContext -> true }

    productLayout.productApiModules = JAVA_IDE_API_MODULES
    productLayout.productImplementationModules = JAVA_IDE_IMPLEMENTATION_MODULES +
                                                  ["intellij.platform.duplicates.analysis", "intellij.platform.structuralSearch", "intellij.platform.main"] -
                                                  ["intellij.platform.jps.model.impl", "intellij.platform.jps.model.serialization"]
    productLayout.withAdditionalPlatformJar("resources.jar", "intellij.idea.community.resources", "intellij.android.adt.branding")

    def unknownExcludedPlugins = EXCLUDED_PLUGINS - INHERITED_PLUGINS
    assert unknownExcludedPlugins.empty : "AndroidStudioProperties.EXCLUDED_PLUGINS contains nonexistent plugins: $unknownExcludedPlugins"
    productLayout.bundledPluginModules.clear()
    productLayout.bundledPluginModules.addAll(INHERITED_PLUGINS + EXTRA_PLUGINS - EXCLUDED_PLUGINS)

    productLayout.mainModules = ["intellij.idea.community.main"]
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false

    List<PluginLayout> inheritedPluginLayouts = new ArrayList<>(CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS)
    // Remove plugin layouts that reference modules that do not exist in our fork.
    inheritedPluginLayouts.removeAll {
      it.mainModule in EXCLUDED_PLUGINS || it.mainModule == "intellij.python.community.plugin"
    }
    productLayout.allNonTrivialPlugins = inheritedPluginLayouts + [
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin([]),
      plugin("intellij.cidr.debugger.plugin") {
        it.withModule("intellij.cidr.debugger", it.mainJarName)
        it.withModule("intellij.cidr.debugger.backend", it.mainJarName)
        it.withModule("intellij.cidr.debugger.commandInterpreterLang", it.mainJarName)
        it.withModule("intellij.cidr.core", it.mainJarName)
        it.withModule("intellij.cidr.util", it.mainJarName)
        it.withModule("intellij.cidr.util.serializer", it.mainJarName)
        it.withModule("intellij.cidr.util.ui", it.mainJarName)
      },
      plugin("intellij.cidr.base.plugin") {
        it.withModule("intellij.c.dfa", it.mainJarName)
        it.withModule("intellij.cidr.base", it.mainJarName)
        it.withModule("intellij.cidr.projectModel", it.mainJarName)
        it.withModule("intellij.cidr.workspaceModel", it.mainJarName)
        it.withModule("intellij.cidr.lang.base", it.mainJarName)
        it.withModule("intellij.cidr.execution", it.mainJarName)
        // Note the following are in CLionProperties.groovy but we don't include them since
        // they were never shipped with Android Studio before.
        //   * intellij.cidr.toolchains
        //   * intellij.platform.ssh.nio
        //   * intellij.apple.sdk
        // The following are not in CLionProperties.groovy for this plugin. Instead they
        // are put under plugin "intellij.clion" or IDE implementation. We put them under
        // this base plugin so that they will still be shipped.
        it.withModule("intellij.cidr.psi.base", it.mainJarName)
        it.withModule("intellij.cidr.resources", it.mainJarName)
        it.withModule("intellij.cidr.common", it.mainJarName)
        it.withModule("intellij.cmake.psi", it.mainJarName)
        // The cidr test framework is included in the IDE base in Clion. But we
        // include it here to support writing tests in plugins.
        it.withModule("intellij.cidr.common.testFramework.core", it.mainJarName)
      },
      plugin("intellij.c.plugin") {
        it.withModule("intellij.c", it.mainJarName)
        it.withModule("intellij.c.debugger", it.mainJarName)
        it.withModule("intellij.c.doxygen", it.mainJarName)
        it.withModule("intellij.c.testing", it.mainJarName)
        it.withModule("intellij.cidr.modulemap.language", it.mainJarName)
      },
    ]
  }

  @Override
  @CompileDynamic
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    super.copyAdditionalFiles(buildContext, targetDirectory)

    new FileSet(buildContext.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(Path.of(targetDirectory))
    new FileSet(buildContext.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin"))
    new FileSet(buildContext.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/lldb/helpers"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin/lldb/helpers"))
    new FileSet(buildContext.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/helpers"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin/helpers"))

    // Android Studio: copy CIDR license to CIRR plugins
    new FileSet(buildContext.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/c-clangd/lib/LICENSE.txt"))
    new FileSet(buildContext.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/c-plugin/lib/LICENSE.txt"))
    new FileSet(buildContext.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/cidr-base-plugin/lib/LICENSE.txt"))
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        icoPath = "$projectHome/adt-branding/src/artwork/androidstudio.ico"
        icoPathForEAP = "$projectHome/adt-branding/src/artwork/preview/androidstudio.ico"
        zipArchiveWithBundledJre = false
        installerImagesPath = "$projectHome/build/conf/ideaCE/win/images"
        fileAssociations = [".java", ".groovy", ".kt"]
      }

      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { "Android Studio" }

      @Override
      String getFullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { "Android Studio" }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }

      @Override
      String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
// TODO
        "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
        new FileSet(buildContext.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/win"))
          .includeAll()
          .copyToDir(Path.of(targetDirectory, "plugins/c-clangd/bin/clang/win"))

        Path distBinDir = Paths.get(targetDirectory).resolve("bin")
        buildGameToolsScriptsForWindows(buildContext, distBinDir)
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        buildOnlyBareTarGz = true
        iconPngPath = "$projectHome/adt-branding/src/artwork/icon_AS_128.png"
        iconPngPathForEAP = "$projectHome/adt-branding/src/artwork/preview/icon_AS_128.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext buildContext, Path targetDirectory, JvmArchitecture arch) {
        new FileSet(buildContext.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/linux"))
          .includeAll()
          .copyToDir(targetDirectory.resolve("plugins/c-clangd/bin/clang/linux"))
        extraExecutables = List.of("plugins/c-clangd/bin/clang/linux/clangd", "plugins/c-clangd/bin/clang/linux/clang-tidy")

        Path distBinDir = targetDirectory.resolve("bin")
        buildGameToolsScriptsForUnix(buildContext, distBinDir, OsFamily.LINUX)
      }
    }
  }

  class StudioMacDistributionCustomizer extends MacDistributionCustomizer {
    StudioMacDistributionCustomizer(String projectHome) {
      urlSchemes = ["idea"]
      associateIpr = true
      bundleIdentifier = "com.google.android.studio"
      dmgImagePath = "$projectHome/build/conf/ideaCE/mac/images/dmg_background.tiff"
      // For now we have all 3 platform icons checked in and we change
      // the icons manually. Fix this when the other platforms have the
      // same mechanisms for our .ico and .svg files
      icnsPath = "$projectHome/adt-branding/src/artwork/AndroidStudio.icns"
      icnsPathForEAP = "$projectHome/adt-branding/src/artwork/preview/AndroidStudio.icns"
    }

    @Override
    String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
      applicationInfo.isEAP() ? "Android Studio Preview.app" : "Android Studio.app"
    }

    @Override
    @CompileDynamic
    void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
      new FileSet(buildContext.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/mac"))
        .includeAll()
        .copyToDir(Path.of(targetDirectory, "plugins/c-clangd/bin/clang/mac"))
      extraExecutables = List.of("plugins/c-clangd/bin/clang/mac/clangd", "plugins/c-clangd/bin/clang/mac/clang-tidy")

      Path distBinDir = Paths.get(targetDirectory).resolve("bin")
      buildGameToolsScriptsForUnix(buildContext, distBinDir, OsFamily.MACOS)
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    new StudioMacDistributionCustomizer(projectHome)
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo, String buildNumber) { "AndroidStudio${applicationInfo.isEAP() ? "Preview" : ""}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "studio" }

  private static void buildGameToolsScriptsForWindows(BuildContext buildContext, Path distBinDir) {
    List<String> classPathJars = buildContext.bootClassPathJarNames
    String classPath = "SET \"CLASS_PATH=%IDE_HOME%\\lib\\${classPathJars.get(0)}\""
    for (int i = 1; i < classPathJars.size(); i++) {
      classPath += "\nSET \"CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\lib\\${classPathJars.get(i)}\""
    }
    String fullName = buildContext.applicationInfo.productName
    String baseName = buildContext.productProperties.baseFileName
    String vmOptionsFileName = "${baseName}64.exe"

    // We manually set the classpath to include everything the game tools need and disable all plugin loading at runtime with
    // `-Didea.load.plugins=false`. This change on classpath is needed since AndroidGameDevelopmentToolsPlugin.xml, the starting plugin XML, is
    // located in plugins/android/lib/game-tools.jar, which is not in classpath by default. In addition, AndroidGameDevelopmentToolsPlugin.xml
    // directly references all needed Intellij platform components so that the unneeded ones (for example, shift-shift to find everything)
    // are ignored. See go/project-aplos-design for more details.
    String gameToolsClassPath = classPath + "\n" + [
      "plugins/android/lib/*",
      "plugins/android/resources/*",
      "plugins/java/lib/java-api.jar",
      "plugins/java/lib/java-impl.jar",
      "plugins/java/lib/resources.jar",
      "plugins/java/lib/java_resources_en.jar"
    ].collect { "SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\\$it" }.join("\n")

    Path winScripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/win/scripts")
    FileKt.substituteTemplatePlaceholders(
      winScripts.resolve("executable-template.bat"),
      distBinDir.resolve("game-tools.bat"),
      "@@",
      [
        new Pair<String, String>("product_full", fullName + "GameTools"),
        new Pair<String, String>("product_uc", buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)),
        new Pair<String, String>("product_vendor", buildContext.applicationInfo.shortCompanyName),
        new Pair<String, String>("vm_options", vmOptionsFileName),
        new Pair<String, String>("system_selector", "AndroidGameDevelopmentTools"),
        new Pair<String, String>("ide_jvm_args", buildContext.getAdditionalJvmArguments(OsFamily.WINDOWS).join(' ') + " -Didea.platform.prefix=AndroidGameDevelopmentTools -Didea.load.plugins=false -Didea.initially.ask.config=force-not"),
        new Pair<String, String>("class_path", gameToolsClassPath),
        new Pair<String, String>("base_name", "game_tools"),
     ]
    )

    Files.copy(
      winScripts.resolve("profiler.bat"),
      distBinDir.resolve("profiler.bat"),
      StandardCopyOption.REPLACE_EXISTING)

    // Copy the profiler launcher executable.
    Files.copy(
      Paths.get(buildContext.paths.communityHome)
        .resolve("../../prebuilts/tools/windows/game-tools/GameToolsWinLauncher/ProfilerWinLauncher.exe"),
      distBinDir.resolve("profiler.exe"),
      StandardCopyOption.REPLACE_EXISTING)
  }

  private static void buildGameToolsScriptsForUnix(BuildContext buildContext, Path distBinDir, OsFamily os) {
    String platformClassPath = "CLASS_PATH=\"\$IDE_HOME/lib/${buildContext.bootClassPathJarNames[0]}\"\n"
    platformClassPath += buildContext.bootClassPathJarNames[1..-1].collect { "CLASS_PATH=\"\$CLASS_PATH:\$IDE_HOME/lib/${it}\"" }.join("\n")

    // We manually set the classpath to include everything the game tools need and disable all plugin loading at runtime with
    // `-Didea.load.plugins=false`. This change on classpath is needed since AndroidGameDevelopmentToolsPlugin.xml, the starting plugin XML, is
    // located in plugins/android/lib/game-tools.jar, which is not in classpath by default. In addition, AndroidGameDevelopmentToolsPlugin.xml
    // directly references all needed Intellij platform components so that the unneeded ones (for example, shift-shift to find everything)
    // are ignored. See go/project-aplos-design for more details.
    String gameToolsClassPath = platformClassPath + "\n" + [
      "plugins/android/lib/*",
      "plugins/android/resources/*",
      "plugins/java/lib/java-api.jar",
      "plugins/java/lib/java-impl.jar",
      "plugins/java/lib/resources.jar",
      "plugins/java/lib/java_resources_en.jar"].
      collect { "CLASS_PATH=\"\$CLASS_PATH:\$IDE_HOME/${it}\"" }.join("\n")


    Path linuxScripts = buildContext.paths.communityHomeDir.resolve("platform/build-scripts/resources/linux/scripts")
    FileKt.substituteTemplatePlaceholders(
      linuxScripts.resolve("executable-template.sh"),
      distBinDir.resolve("game-tools.sh"),
      "__",
      [
        new Pair<String, String>("product_full", buildContext.applicationInfo.productName + "GameTools"),
        new Pair<String, String>("product_uc", buildContext.productProperties.getEnvironmentVariableBaseName(buildContext.applicationInfo)),
        new Pair<String, String>("product_vendor", buildContext.applicationInfo.shortCompanyName),
        new Pair<String, String>("vm_options", buildContext.productProperties.baseFileName),
        new Pair<String, String>("system_selector", "AndroidGameDevelopmentTools"),
        new Pair<String, String>("ide_jvm_args", buildContext.getAdditionalJvmArguments(os).join(' ') + " -Didea.platform.prefix=AndroidGameDevelopmentTools -Didea.load.plugins=false -Didea.initially.ask.config=force-not"),
        new Pair<String, String>("class_path", gameToolsClassPath),
      ]
    )

    Files.copy(
      linuxScripts.resolve("profiler.sh"),
      distBinDir.resolve("profiler.sh"),
      StandardCopyOption.REPLACE_EXISTING)
  }
}
