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
import org.jetbrains.intellij.build.impl.PluginLayout

import static org.jetbrains.intellij.build.impl.PluginLayout.plugin

// Based on the IdeaCommunityProperties definition
// TODO: Need Windows installer and Mac DMG images
// TODO: Need uninstaller feedback URL
// Consider switching from AI to AS code
// Restore a lot of the custom logic from studio_properties
// TODO: Use separate bundle identifier for EAP and non-EAP
@CompileStatic
class AndroidStudioProperties extends BaseIdeaProperties {

  private String gradleVersion = "3.2"

  AndroidStudioProperties(String home) {
    baseFileName = "studio"
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "adt-branding"
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaCE.properties".toString()]
    toolsJarRequired = true
    buildCrossPlatformDistribution = true

    productLayout.platformApiModules = CommunityRepositoryModules.PLATFORM_API_MODULES + JAVA_API_MODULES
    productLayout.platformImplementationModules = CommunityRepositoryModules.PLATFORM_IMPLEMENTATION_MODULES + JAVA_IMPLEMENTATION_MODULES +
                                                  [
                                                    // Android Studio: CIDR/CLion: Must be included here to be packaged into core, not as separate plugins
                                                    "cidr-common",
                                                    "cidr-debugger",
                                                    "cidr-lang",
                                                    "cidr-lang-dfa",
                                                    "cidr-util",
                                                    "doxygen",
                                                  ] +
                                                  ["duplicates-analysis", "structuralsearch", "structuralsearch-java", "typeMigration", "platform-main"] -
                                                  ["jps-model-impl", "jps-model-serialization"]
    productLayout.additionalPlatformJars.put("resources.jar", "adt-branding")

    // Android Studio: including the common base library to avoid classloader issues (?)
    productLayout.additionalPlatformJars.put("android-base-common.jar", "common")
    // Android Studio: include metrics libraries in $install/lib
    productLayout.additionalPlatformJars.putAll("google-analytics-library.jar",
                                                "android-annotations",
                                                "analytics-protos",
                                                "analytics-shared",
                                                "analytics-tracker",
                                                "analytics-publisher")

    productLayout.bundledPluginModules = BUNDLED_PLUGIN_MODULES +
                                         [
                                           // Android Studio bundles these:
                                           "android-ndk",
                                           "firebase",
                                           "firebase-testing",
                                           "games",
                                           "google-appindexing",
                                           "google-login-as",
                                           "google-cloud-tools-as",
                                           "google-cloud-tools-core-as",
                                           "google-samples",
                                           "google-services",
                                           "ndk-workspace",
                                           "test-recorder",
                                           "url-assistant",
                                         ]
    productLayout.mainModules = ["community-main"]
    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      androidPluginInStudio([:]),
      CommunityRepositoryModules.groovyPlugin([])
    ]
    productLayout.classesLoadingOrderFilePath = "$home/build/order.txt"
  }

  static PluginLayout androidPluginInStudio(Map<String, String> additionalModulesToJars) {
    plugin("android-plugin") {
      directoryName = "android"
      mainJarName = "android.jar"
      withModule("android-common", "android-common.jar", false)
      withModule("android-rt", "android-rt.jar", false)

      withModule("android", "android.jar", false)
      withModule("android-plugin", "android.jar")
      withModule("observable", "android.jar")
      withModule("designer", "android.jar")
      withModule("sdk-updates", "android.jar")
      withModule("wizard", "android.jar")
      withModule("profilers-android", "android.jar")
      withModule("adt-ui", "adt-ui.jar")
      withModule("adt-ui-model", "adt-ui.jar")
      withModule("repository")
      withModule("db-baseLibrary", "data-binding.jar")
      withModule("db-compilerCommon", "data-binding.jar")
      withModule("db-compiler", "data-binding.jar")
      withModule("sherpa-solver", "constraint-layout.jar")
      withModule("sherpa-ui", "constraint-layout.jar")
      withModule("sdklib", "sdklib.jar")
      withModule("sdk-common", "sdk-common.jar")
      withModule("layoutlib-api", "layoutlib-api.jar")
      withModule("layoutlib", "layoutlib-loader.jar")
      withModule("manifest-merger", "manifest-merger.jar")
      withModule("chunkio", "pixelprobe.jar")
      withModule("pixelprobe", "pixelprobe.jar")

      withModule("assetstudio", "sdk-tools.jar")
      withModule("binary-resources", "sdk-tools.jar")
      withModule("ddmlib", "sdk-tools.jar")
      withModule("dvlib", "sdk-tools.jar")
      withModule("draw9patch", "sdk-tools.jar")
      withModule("instant-run-client", "sdk-tools.jar")
      withModule("instant-run-common", "sdk-tools.jar")
      withModule("lint-api", "sdk-tools.jar")
      withModule("lint-checks", "sdk-tools.jar")
      withModule("ninepatch", "sdk-tools.jar")
      withModule("perflib", "sdk-tools.jar")
      withModule("builder-model", "sdk-tools.jar")
      withModule("builder-test-api", "sdk-tools.jar")
      withModule("android-annotations", "sdk-tools.jar")

      withJpsModule("android-gradle-jps")
      withJpsModule("android-jps-plugin")

      /*
      TODO:
+      dir("resources") {
+        fileset(file: "${home}/../../out/studio/transform/libs/profilers-transform.jar")
+        dir("perfd") {
+          fileset(dir: "${home}/../../out/studio/native/out/release")
+        }
+      }
       */

      withProjectLibrary("freemarker-2.3.20") //todo[nik] move to module libraries
      //withProjectLibrary("builder-model") //todo[nik] move to module libraries
      withProjectLibrary("jgraphx-3.4.0.1") //todo[nik] move to module libraries
      withProjectLibrary("kxml2") //todo[nik] move to module libraries
      withProjectLibrary("lombok-ast") //todo[nik] move to module libraries
      withProjectLibrary("layoutlib") //todo[nik] move to module libraries
      withResourceFromModule("android","device-art-resources", "lib/device-art-resources")
      withResourceFromModule("sdklib", "../templates", "lib/templates")
      withResourceFromModule("android","annotations", "lib/androidAnnotations.jar")
      withResourceFromModule("android","lib/antlr4-runtime-4.5.3.jar", "lib")
      withResourceFromModule("android","lib/asm-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/asm-analysis-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/asm-tree-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/commons-io-2.4.jar", "lib")
      withResourceFromModule("android","lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("android","lib/javawriter-2.2.1.jar", "lib")
      withResourceFromModule("android","lib/juniversalchardet-1.0.3.jar", "lib")

      withResource("../../../../prebuilts/studio/layoutlib/data/layoutlib.jar", "lib")

      withResourceFromModule("android","lib/gluegen-rt.jar", "lib")
      withResourceFromModule("android","lib/gluegen-rt-natives-linux-amd64.jar", "lib")
      withResourceFromModule("android","lib/gluegen-rt-natives-linux-i586.jar", "lib")
      withResourceFromModule("android","lib/gluegen-rt-natives-macosx-universal.jar", "lib")
      withResourceFromModule("android","lib/gluegen-rt-natives-windows-amd64.jar", "lib")
      withResourceFromModule("android","lib/gluegen-rt-natives-windows-i586.jar", "lib")
      withProjectLibrary("jogl-all") //todo[nik] move to module libraries
      withResourceFromModule("android","lib/jogl-all-natives-linux-amd64.jar", "lib")
      withResourceFromModule("android","lib/jogl-all-natives-linux-i586.jar", "lib")
      withResourceFromModule("android","lib/jogl-all-natives-macosx-universal.jar", "lib")
      withResourceFromModule("android","lib/jogl-all-natives-windows-amd64.jar", "lib")
      withResourceFromModule("android","lib/jogl-all-natives-windows-i586.jar", "lib")
      withResourceFromModule("android","lib/androidWidgets", "lib/androidWidgets")
      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }
    }
  }


  // TODO: Exclude BUILD files
  // TODO: Remove JavaFX
  // TODO: Remove annotations-java8.jar
/*
  def layoutAndroid(String androidHome, String androidToolsBaseHome) {
    dir("plugins") {
      dir("android") {
        dir("lib") {
          resources("android")

          fileset(file: "${home}/../../prebuilts/studio/layoutlib/data/layoutlib.jar")
          fileset(dir: "${androidHome}/android/lib") {
            include(name: "** /*.jar")
            include(name: "licenses/*")
            exclude(name: "** /fest-*.jar")
            exclude(name: "src/*.jar")
          }
          fileset(dir: "${home}/../../out/studio/runtime")
          jar("androidAnnotations.jar") {
            fileset(dir: "$androidHome/android/annotations")
          }
          dir("device-art-resources") {
            fileset(dir: "$androidHome/android/device-art-resources")
          }

          dir("templates") {
            fileset(dir: "$androidToolsBaseHome/templates")
          }

          dir("layoutlib") {
            fileset(dir: "${home}/../../prebuilts/studio/layoutlib") {
              exclude(name: "PREBUILT")
              exclude(name: "** /layoutlib.jar")
            }
          }
        }
      }
    }
  }
  */

  @Override
  @CompileDynamic
  void copyAdditionalFiles(BuildContext buildContext, String targetDirectory) {
    super.copyAdditionalFiles(buildContext, targetDirectory)
    buildContext.ant.copy(todir: targetDirectory) {
      fileset(file: "$buildContext.paths.communityHome/LICENSE.txt")
      fileset(file: "$buildContext.paths.communityHome/NOTICE.txt")
    }
    buildContext.ant.copy(todir: "$targetDirectory/bin") {
      fileset(dir: "$buildContext.paths.communityHome/build/conf/ideaCE/common/bin")
    }

    def root = "$buildContext.paths.communityHome/../.."

    // Bundle Gradle and an offline Maven repo
    buildContext.ant.unzip(src: "$root/tools/external/gradle/gradle-$gradleVersion-bin.zip", dest: "$targetDirectory/gradle")
    buildContext.ant.copy(todir: "$targetDirectory/gradle/m2repository") {
      fileset(dir: System.getenv().STUDIO_CUSTOM_REPO ?: "$root/prebuilts/tools/common/offline-m2")
      fileset(dir: "$root/out/studio/repo")
    }

    buildContext.ant.touch(file: "$targetDirectory/license/dev01_license.txt", mkdirs: true)
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        icoPath = "$projectHome/../adt/idea/adt-branding/src/artwork/androidstudio.ico"
// TODO
        installerImagesPath = "$projectHome/build/conf/ideaCE/win/images"
        fileAssociations = [".java", ".groovy", ".kt"]
      }

      @Override
      String getFullNameIncludingEdition(ApplicationInfoProperties applicationInfo) { "Android Studio" }

      @Override
      String getFullNameIncludingEditionAndVendor(ApplicationInfoProperties applicationInfo) { "Android Studio" }

      @Override
      String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
// TODO
        "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }

// TODO:
      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/idea" }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        iconPngPath = "$projectHome/../adt/idea/adt-branding/src/artwork/icon_AS_128.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    return new MacDistributionCustomizer() {
      {
        helpId = "AI"
        urlSchemes = ["idea"]
        associateIpr = true
        enableYourkitAgentInEAP = false
// TODO: In EAP include suffix to have separate icons!
        bundleIdentifier = "com.jetbrains.intellij.ce"
        dmgImagePath = "$projectHome/build/conf/mac/communitydmg.png"
        icnsPathForEAP = "$projectHome/../adt/idea/adt-branding/src/artwork/AndroidStudio.icns"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        applicationInfo.isEAP ? "Android Studio ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} Preview.app"
                              : "Android Studio.app"
      }
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) { "AndroidStudio${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "studio" }
}