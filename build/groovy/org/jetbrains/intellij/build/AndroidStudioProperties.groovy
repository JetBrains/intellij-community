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

  AndroidStudioProperties(String home) {
    baseFileName = "studio"
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "adt-branding"
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaCE.properties".toString()]
    toolsJarRequired = true
    buildCrossPlatformDistribution = true

    allLibraryLicenses.addAll(AndroidStudioLibraryLicenses.LICENSES_LIST)
    // TODO: This doesn't cover all used libraries, but it's exactly what ShowLicensesUsedAction is checking.
    additionalDirectoriesWithLicenses.addAll(
      "$home/../adt/idea/android/lib/licenses",
      "$home/../studio/google/appindexing/lib/licenses",
      "$home/../studio/google/cloud/testing/firebase-testing/lib/licenses",
      "$home/../studio/google/cloud/testing/test-recorder/lib/licenses",
      "$home/../studio/google/cloud/tools/android-studio-plugin/lib/licenses",
      "$home/../studio/google/cloud/tools/core-plugin/lib/licenses",
      "$home/../studio/google/cloud/tools/google-login-plugin/lib/licenses",
      "$home/../studio/google/services/lib/licenses",
      "$home/../vendor/google/firebase/lib/licenses",
    )

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
    productLayout.additionalPlatformJars.putAll("resources.jar", "community-resources", "adt-branding")

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
                                           "android-apk",
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
                                           "smali",
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
      withModule("observable-ui", "android.jar")
      withModule("flags", "android.jar")
      withModule("designer", "android.jar")
      withModule("sdk-updates", "android.jar")
      withModule("wizard", "android.jar")
      withModule("wizard-model", "android.jar")
      withModule("profilers-android", "android.jar")
      withModule("instantapp-deploy", "android.jar")
      withModule("perfd-host", "android-profilers.jar")
      withModule("profilers", "android-profilers.jar")
      withModule("profilers-ui", "android-profilers.jar")
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
      withModule("analyzer", "sdk-tools.jar")
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
      withModule("layoutinspector", "sdk-tools.jar")

      withJpsModule("android-gradle-jps")
      withJpsModule("android-jps-plugin")

      withProjectLibrary("freemarker-2.3.20") //todo[nik] move to module libraries
      //withProjectLibrary("builder-model") //todo[nik] move to module libraries
      withProjectLibrary("jgraphx-3.4.0.1") //todo[nik] move to module libraries
      withProjectLibrary("kxml2") //todo[nik] move to module libraries
      withProjectLibrary("lombok-ast") //todo[nik] move to module libraries
      withProjectLibrary("layoutlib") //todo[nik] move to module libraries

      withResourceFromModule("android","lib/antlr4-runtime-4.5.3.jar", "lib")
      withResourceFromModule("android","lib/asm-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/asm-analysis-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/asm-tree-5.0.3.jar", "lib")
      withResourceFromModule("android","lib/commons-io-2.4.jar", "lib")
      withResourceFromModule("android","lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("android","lib/javawriter-2.2.1.jar", "lib")
      withResourceFromModule("android","lib/juniversalchardet-1.0.3.jar", "lib")

      withResourceFromModule("android","lib/androidWidgets", "lib/androidWidgets")
      withResourceFromModule("android","device-art-resources", "lib/device-art-resources")
      withResourceFromModule("android","lib/sampleData", "lib/sampleData")
      withResourceArchiveFromModule("android", "annotations", "lib/androidAnnotations.jar")

      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }
    }
  }

  static String getGradleVersionToBundle(BuildContext buildContext) {
    File sdkConstants = buildContext.findFileInModuleSources("common", "com/android/SdkConstants.java")
    if (sdkConstants != null && sdkConstants.exists()) {
      return sdkConstants.readLines().find { line -> line =~ ".*GRADLE_MINIMUM_VERSION.*" }.split("\"")[1]
    }
    buildContext.messages.error("Cannot parse GRADLE_MINIMUM_VERSION in com/android/SdkConstants.java from module 'common'.")
  }

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
    def gradleVersion = getGradleVersionToBundle(buildContext)

    buildContext.messages.block("Bundle Gradle $gradleVersion and the offline Maven repo") {
      buildContext.ant.unzip(src: "$root/tools/external/gradle/gradle-$gradleVersion-bin.zip", dest: "$targetDirectory/gradle")
      buildContext.ant.copy(todir: "$targetDirectory/gradle/m2repository") {
        fileset(dir: System.getenv().STUDIO_CUSTOM_REPO ?: "$root/prebuilts/tools/common/offline-m2") {
          exclude(name: "BUILD")
        }
        fileset(dir: "$root/out/studio/repo")
      }
    }

    buildContext.ant.touch(file: "$targetDirectory/license/dev01_license.txt", mkdirs: true)

    // TODO: figure out if some of these misc resources can be included in a better way
    def androidPluginLib = "$targetDirectory/plugins/android/lib"

    buildContext.ant.copy(todir: "$androidPluginLib") {
      fileset(file: "$root/prebuilts/studio/layoutlib/data/layoutlib.jar")
    }

    buildContext.ant.copy(todir: "$androidPluginLib/layoutlib") {
      fileset(dir: "$root/prebuilts/studio/layoutlib") {
        exclude(name: "PREBUILT")
        exclude(name: "BUILD")
        exclude(name: "data/layoutlib.jar")
      }
    }

    // TODO: This extra copying is unfortunate, but our TemplateManager doesn't seem to handle the default resources.jar packaging (which
    // works out just fine for the rest of Intellij, see lib/resources.jar).
    buildContext.ant.copy(todir: "$androidPluginLib/templates") {
      fileset(dir: "$root/tools/base/templates") {
        exclude(name: "BUILD")
      }
    }
    // TODO: Cloud Tools declares templates/ and clientTemplates/ as source roots (why?!), so they'll also be packaged in the .jar.
    buildContext.ant.copy(todir: "$targetDirectory/plugins/google-cloud-tools-as/lib/templates") {
      fileset(dir: "$root/tools/studio/google/cloud/tools/android-studio-plugin/resources/templates")
    }
    buildContext.ant.copy(todir: "$targetDirectory/plugins/google-cloud-tools-as/lib/clientTemplates") {
      fileset(dir: "$root/tools/studio/google/cloud/tools/android-studio-plugin/resources/clientTemplates")
    }


    // Profiler prebuilt binaries:
    buildContext.ant.copy(todir: "$androidPluginLib") {
      fileset(file: "$root/bazel-genfiles/tools/base/profiler/studio-profiler-grpc-1.0-jarjar.jar")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-genfiles/tools/base/profiler/transform/profilers-transform.jar")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-genfiles/tools/base/profiler/app/perfa.jar")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/perfd") {
      fileset(dir: "$root/bazel-bin/tools/base/profiler/native/perfd/android")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/perfa") {
      fileset(dir: "$root/bazel-bin/tools/base/profiler/native/perfa/android")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/simpleperf") {
      fileset(dir: "$root/prebuilts/tools/common/simpleperf")
    }

    buildContext.ant.copy(todir: "$targetDirectory/bin/lldb/shared") {
      fileset(dir: "$root/tools/vendor/google/android-ndk/bin/lldb/shared")
    }
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
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }

      @Override
      String getUninstallFeedbackPageUrl(ApplicationInfoProperties applicationInfo) {
// TODO
        "https://www.jetbrains.com/idea/uninstall/?edition=IC-${applicationInfo.majorVersion}.${applicationInfo.minorVersion}"
      }

// TODO:
      @Override
      String getBaseDownloadUrlForJre() { "https://download.jetbrains.com/idea" }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."
        context.ant.copy(todir: "$targetDirectory/plugins/sdk-updates/offline-repo") {
          fileset(dir: "$root/prebuilts/tools/windows-x86_64/offline-sdk")
        }

        def androidRoot = "$root/tools/adt/idea"
        context.ant.copy(file: "$androidRoot/native/installer/win/builds/uninstall.exe", tofile: "$targetDirectory/uninstall.exe")
        context.ant.copy(file: "$androidRoot/android/lib/libwebp/win/webp_jni.dll", tofile: "$targetDirectory/plugins/android/lib/webp_jni.dll")
        context.ant.copy(file: "$androidRoot/android/lib/libwebp/win/webp_jni64.dll", tofile: "$targetDirectory/plugins/android/lib/webp_jni64.dll")
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        buildTarGzWithoutBundledJre = false
        iconPngPath = "$projectHome/../adt/idea/adt-branding/src/artwork/icon_AS_128.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."
        context.ant.copy(todir: "$targetDirectory/plugins/sdk-updates/offline-repo") {
          fileset(dir: "$root/prebuilts/tools/linux-x86_64/offline-sdk")
        }

        def androidRoot = "$root/tools/adt/idea"
        context.ant.copy(file: "$androidRoot/android/lib/libwebp/linux/libwebp_jni.so", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni.so")
        context.ant.copy(file: "$androidRoot/android/lib/libwebp/linux/libwebp_jni64.so", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni64.so")
      }
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
        bundleIdentifier = "com.google.android.studio"
        dmgImagePath = "$projectHome/build/conf/mac/communitydmg.png"
        // For now we have all 3 platform icons checked in and we change
        // the icons manually. Fix this when the other platforms have the
        // same mechanisms for our .ico and .svg files
        icnsPath = "$projectHome/../adt/idea/adt-branding/src/artwork/AndroidStudio.icns"
        icnsPathForEAP = "$projectHome/../adt/idea/adt-branding/src/artwork/AndroidStudio.icns"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) {
        applicationInfo.isEAP ? "Android Studio ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} Preview.app"
                              : "Android Studio.app"
      }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."
        context.ant.copy(todir: "$targetDirectory/plugins/sdk-updates/offline-repo") {
          fileset(dir: "$root/prebuilts/tools/darwin-x86_64/offline-sdk")
        }

        def androidRoot = "$root/tools/adt/idea"
        context.ant.copy(file: "$androidRoot/android/lib/libwebp/mac/libwebp_jni64.dylib", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni64.dylib")
      }
    }
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) { "AndroidStudio${applicationInfo.isEAP ? "Preview" : ""}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "studio" }
}
