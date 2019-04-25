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

  AndroidStudioProperties(String home, BuildOptions buildOptions) {
    baseFileName = "studio"
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "intellij.android.adt.branding"
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

    productLayout.productApiModules = JAVA_API_MODULES
    productLayout.productImplementationModules = JAVA_IMPLEMENTATION_MODULES +
                                                  [
                                                    // Android Studio: CIDR/CLion: Must be included here to be packaged into core, not as separate plugins
                                                    "intellij.cidr.common",
                                                    "intellij.cidr.debugger",
                                                    "intellij.c",
                                                    "intellij.c.dfa",
                                                    "intellij.cidr.util",
                                                    "intellij.c.doxygen",
                                                    "intellij.cmake.psi",
                                                  ] +
                                                  ["intellij.platform.duplicates.analysis", "intellij.platform.structuralSearch", "intellij.java.structuralSearch", "intellij.java.typeMigration", "intellij.platform.main"] -
                                                  ["intellij.platform.jps.model.impl", "intellij.platform.jps.model.serialization"]
    productLayout.additionalPlatformJars.putAll("resources.jar", "intellij.idea.community.resources", "intellij.android.adt.branding")

    // Android Studio: including the common base library to avoid classloader issues (?)
    productLayout.additionalPlatformJars.put("android-base-common.jar", "android.sdktools.common")
    // Android Studio: include metrics libraries in $install/lib
    productLayout.additionalPlatformJars.putAll("google-analytics-library.jar",
                                                "android.sdktools.android-annotations",
                                                "analytics-shared",
                                                "analytics-tracker",
                                                "analytics-publisher",
                                                "analytics-crash")

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
                                           "intellij.android.smali",
                                           "test-recorder",
                                           "url-assistant",
                                         ]
    productLayout.mainModules = ["intellij.idea.community.main"]
    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      androidPluginInStudio([:]),
      CommunityRepositoryModules.groovyPlugin([])
    ]
    if (buildOptions.includeUiTests) {
      modulesToCompileTests += ["intellij.android.guiTests", "intellij.android.guiTestFramework", "intellij.android.testFramework"]
      productLayout.allNonTrivialPlugins.add(uitestPlugin())
      productLayout.bundledPluginModules += ["intellij.android.guiTestFramework"]
    }
    productLayout.classesLoadingOrderFilePath = "$home/build/order.txt"
  }

  static PluginLayout uitestPlugin () {
    plugin("intellij.android.guiTestFramework") {
      withTestModule("intellij.android.guiTestFramework")
      withTestModule("intellij.android.guiTests")
      withModule("fest-swing")
      withTestModule("android.sdktools.testutils")
      withTestModule("intellij.android.testFramework")
      withModule("intellij.platform.testFramework")
      withTestModule("intellij.android.observable")
      withModule("android.sdktools.fakeadbserver")
    }
  }

  static PluginLayout androidPluginInStudio(Map<String, String> additionalModulesToJars) {
    plugin("intellij.android.plugin") {
      directoryName = "android"
      mainJarName = "android.jar"
      withModule("intellij.android.common", "android-common.jar", false)
      withModule("intellij.android.buildCommon", "build-common.jar", false)
      withModule("intellij.android.rt", "android-rt.jar", false)

      withModule("intellij.android.core", "android.jar", false)
      withModule("intellij.android.adb", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.artwork")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("android.sdktools.flags", "android.jar")
      withModule("intellij.android.property-editor", "android.jar")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.naveditor", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("intellij.android.profilersAndroid", "android.jar")
      withModule("intellij.android.deploy", "android.jar")
      withModule("intellij.android.kotlin.idea", "android-kotlin.jar")
      withModule("intellij.android.kotlin.output.parser", "android-kotlin.jar")
      withModule("intellij.android.kotlin.extensions", "android-extensions-ide.jar")
      withModule("intellij.android.transportDatabase", "android-profilers.jar")
      withModule("intellij.android.profilers", "android-profilers.jar")
      withModule("intellij.android.profilers.ui", "android-profilers.jar")
      withModule("intellij.android.profilers.atrace", "android-profilers.jar")
      withModule("native-symbolizer", "android.jar")
      withModule("intellij.android.apkanalyzer", "android.jar")
      withModule("intellij.android.projectSystem", "android.jar")
      withModule("intellij.android.projectSystem.gradle", "android.jar")
      withModule("intellij.android.resources-aar", "android.jar")
      withModule("android-layout-inspector", "android.jar")
      withModule("assistant", "android.jar")
      withModule("connection-assistant", "android.jar")
      withModule("whats-new-assistant", "android.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModule("android.sdktools.repository")
      withModule("db-baseLibrary", "data-binding.jar")
      withModule("db-baseLibrarySupport", "data-binding.jar")
      withModule("db-compilerCommon", "data-binding.jar")
      withModule("db-compiler", "data-binding.jar")
      withModule("android.sdktools.sdklib", "sdklib.jar")
      withModule("android.sdktools.sdk-common", "sdk-common.jar")
      withModule("android.sdktools.layoutlib-api", "layoutlib-api.jar")
      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")
      withModuleLibrary("layoutlib", "intellij.android.layoutlib", "")
      withModule("android.sdktools.manifest-merger", "manifest-merger.jar")
      withModule("android.sdktools.chunkio", "pixelprobe.jar")
      withModule("android.sdktools.pixelprobe", "pixelprobe.jar")

      withModule("android.sdktools.binary-resources", "sdk-tools.jar")
      withModule("android.sdktools.analyzer", "sdk-tools.jar")
      withModule("android.sdktools.ddmlib", "sdk-tools.jar")
      withModule("android.sdktools.dvlib", "sdk-tools.jar")
      withModule("android.sdktools.deployer", "sdk-tools.jar")
      withModule("android.sdktools.tracer", "sdk-tools.jar")
      withModule("android.sdktools.draw9patch", "sdk-tools.jar")
      withModule("android.sdktools.lint-api", "sdk-tools.jar")
      withModule("android.sdktools.lint-checks", "sdk-tools.jar")
      withModule("android.sdktools.ninepatch", "sdk-tools.jar")
      withModule("android.sdktools.perflib", "sdk-tools.jar")
      withModule("android.sdktools.builder-model", "sdk-tools.jar")
      withModule("android.sdktools.builder-test-api", "sdk-tools.jar")
      withModule("android.sdktools.android-annotations", "sdk-tools.jar")
      withModule("android.sdktools.layoutinspector", "sdk-tools.jar")
      withModule("android.sdktools.java-lib-model", "sdk-tools.jar")
      withModule("android.sdktools.java-lib-model-builder", "sdk-tools.jar")
      withModule("usb-devices", "sdk-tools.jar")

      withModule("intellij.android.jps", "jps/android-jps-plugin.jar", null)

      withProjectLibrary("freemarker") //todo[nik] move to module libraries
      withProjectLibrary("kxml2") //todo[nik] move to module libraries

      withResourceFromModule("intellij.android.core", "lib/asm-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/asm-analysis-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/asm-tree-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/javawriter-2.2.1.jar", "lib")

      withResourceFromModule("intellij.android.core", "lib/androidWidgets", "lib/androidWidgets")
      withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("intellij.android.core", "lib/sampleData", "lib/sampleData")
      withResourceArchiveFromModule("intellij.android.core", "annotations", "lib/androidAnnotations.jar")

      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }
    }
  }

  static String getGradleVersionToBundle(BuildContext buildContext) {
    File sdkConstants = buildContext.findFileInModuleSources("android.sdktools.common", "com/android/SdkConstants.java")
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

    if (buildContext.options.bundleGradleAndOfflineRepo) {
      bundleGradleAndOfflineRepo(buildContext, root, targetDirectory)
    }

    buildContext.ant.touch(file: "$targetDirectory/license/dev01_license.txt", mkdirs: true)

    // TODO: figure out if some of these misc resources can be included in a better way
    def androidPluginLib = "$targetDirectory/plugins/android/lib"

    buildContext.ant.copy(todir: "$androidPluginLib/layoutlib") {
      fileset(dir: "$root/prebuilts/studio/layoutlib") {
        exclude(name: "PREBUILT")
        exclude(name: "BUILD")
        exclude(name: "data/layoutlib.jar")
        exclude(name: "data/res/**")
      }
    }

    buildContext.ant.copy(todir: "$androidPluginLib/layoutlib/data") {
      fileset(file: "$root/bazel-genfiles/tools/adt/idea/resources-aar/framework_res.jar")
    }

    // TODO: This extra copying is unfortunate, but our TemplateManager doesn't seem to handle the default resources.jar packaging (which
    // works out just fine for the rest of Intellij, see lib/resources.jar).
    buildContext.ant.copy(todir: "$androidPluginLib/templates") {
      fileset(dir: "$root/tools/base/templates") {
        exclude(name: "BUILD")
      }
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
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-genfiles/tools/base/profiler/app/perfa_okhttp.dex")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/transport") {
      fileset(dir: "$root/bazel-bin/tools/base/transport/android")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/transport/agent") {
      fileset(dir: "$root/bazel-bin/tools/base/transport/agent/android")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/simpleperf") {
      fileset(dir: "$root/prebuilts/tools/common/simpleperf") {
        exclude(name: "BUILD")
        exclude(name: "README.md")
      }
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/perfetto") {
      fileset(dir: "$root/prebuilts/tools/common/perfetto") {
        exclude(name: "BUILD")
        exclude(name: "README.md")
      }
    }

    // Instant run
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/installer") {
      fileset(dir: "$root/bazel-genfiles/tools/base/deploy/installer/android-installer")
    }

    // Native debugger.
    buildContext.ant.copy(todir: "$targetDirectory/bin/lldb") {
      fileset(dir: "$root/prebuilts/tools/common/lldb")
    }

    // UI test data directory
    if (buildContext.options.includeUiTests) {
      buildContext.ant.copy(todir: "$targetDirectory/plugins/uitest-framework/testData") {
        fileset(dir: "$root/tools/adt/idea/android-uitests/testData")
      }
      buildContext.ant.copy(todir: "$targetDirectory/bin", overwrite: "true") {// BuildTasksImpl.copyLogXml copies a version of this without the CONSOLE-WARN appender, which we want for UI tests.
        fileset(file: "$root/tools/idea/bin/log.xml")
        fileset(file: "$root/tools/adt/idea/uitest-framework/testSrc/com/android/tools/idea/tests/gui/framework/run_uitests.py")
      }
    }
  }

  @CompileDynamic
  protected void bundleGradleAndOfflineRepo(BuildContext buildContext, String root, String targetDirectory) {
    def gradleVersion = getGradleVersionToBundle(buildContext)

    buildContext.messages.block("Bundle Gradle $gradleVersion and the offline Maven repo") {
      buildContext.ant.unzip(src: "$root/tools/external/gradle/gradle-$gradleVersion-bin.zip", dest: "$targetDirectory/gradle")
      // when creating gradle wrappers for UI test projects, we need a zipped copy of gradle to point to
      if (buildContext.options.includeUiTests) {
        buildContext.ant.copy(todir: "$targetDirectory/gradle/gradle-$gradleVersion") {
          fileset(file: "$root/tools/external/gradle/gradle-$gradleVersion-bin.zip")
        }
        buildContext.ant.copy(todir: "$targetDirectory/gradle/m2repository/com/android/databinding") {
          fileset(dir: "$root/out/repo/com/android/databinding")
        }
        buildContext.ant.unzip(src: "$root/bazel-bin/tools/adt/idea/android/test_deps.zip", dest: "$targetDirectory/gradle/m2repository")
        buildContext.ant.unzip(src: "$root/bazel-bin/tools/adt/idea/uitest-framework/uitest_deps.zip", dest: "$targetDirectory/gradle/m2repository")
      }

      buildContext.ant.copy(todir: "$targetDirectory/gradle/m2repository") {
        fileset(dir: System.getenv().STUDIO_CUSTOM_REPO ?: "$root/prebuilts/tools/common/offline-m2") {
          exclude(name: "BUILD")
        }
        fileset(dir: "$root/out/studio/repo")
      }
    }
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        icoPath = "$projectHome/../adt/idea/adt-branding/src/artwork/androidstudio.ico"
        icoPathForEAP = "$projectHome/../adt/idea/adt-branding/src/artwork/preview/androidstudio.ico"
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
        context.ant.copy(file: "$androidRoot/adt-ui/lib/libwebp/win/webp_jni.dll", tofile: "$targetDirectory/plugins/android/lib/webp_jni.dll")
        context.ant.copy(file: "$androidRoot/adt-ui/lib/libwebp/win/webp_jni64.dll", tofile: "$targetDirectory/plugins/android/lib/webp_jni64.dll")

        def lldbTarget = "$targetDirectory/bin/lldb/"
        context.ant.copy(todir: "$lldbTarget") {
          fileset(dir: "$root/prebuilts/tools/windows-x86_64/lldb")
        }
        context.ant.copy(todir: "$lldbTarget/lib") {
          fileset(dir: "$root/prebuilts/python/windows-x86/x64/Lib")
        }

        def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
        context.ant.copy(todir: "$simpleperfTarget/windows") {
          fileset(dir: "$root/prebuilts/tools/windows/simpleperf")
        }
        context.ant.copy(todir: "$simpleperfTarget/windows-x86_64") {
          fileset(dir: "$root/prebuilts/tools/windows-x86_64/simpleperf")
        }
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        buildTarGzWithoutBundledJre = false
        iconPngPath = "$projectHome/../adt/idea/adt-branding/src/artwork/icon_AS_128.png"
        iconPngPathForEAP = "$projectHome/../adt/idea/adt-branding/src/artwork/preview/icon_AS_128.png"
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
        context.ant.copy(file: "$androidRoot/adt-ui/lib/libwebp/linux/libwebp_jni.so", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni.so")
        context.ant.copy(file: "$androidRoot/adt-ui/lib/libwebp/linux/libwebp_jni64.so", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni64.so")

        def lldbTarget = "$targetDirectory/bin/lldb/"
        context.ant.copy(todir: "$lldbTarget") {
          fileset(dir: "$root/prebuilts/tools/linux-x86_64/lldb")
        }
        extraExecutables.add("bin/lldb/bin/LLDBFrontend")
        extraExecutables.add("bin/lldb/bin/llvm-symbolizer")
        extraExecutables.add("bin/lldb/bin/minidump_stackwalk")
        context.ant.copy(todir: "$lldbTarget/lib/python2.7") {
          fileset(dir: "$root/prebuilts/python/linux-x86/lib/python2.7")
        }

        def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
        context.ant.copy(todir: "$simpleperfTarget/linux-x86") {
          fileset(dir: "$root/prebuilts/tools/linux-x86/simpleperf")
        }
        extraExecutables.add("plugins/android/resources/simpleperf/linux-x86/simpleperf")

        context.ant.copy(todir: "$simpleperfTarget/linux-x86_64") {
          fileset(dir: "$root/prebuilts/tools/linux-x86_64/simpleperf")
        }
        extraExecutables.add("plugins/android/resources/simpleperf/linux-x86_64/simpleperf")
      }
    }
  }

  class StudioMacDistributionCustomizer extends MacDistributionCustomizer {
    StudioMacDistributionCustomizer(String projectHome) {
      urlSchemes = ["idea"]
      associateIpr = true
      enableYourkitAgentInEAP = false
      bundleIdentifier = "com.google.android.studio"
      dmgImagePath = "$projectHome/build/conf/ideaCE/mac/images/dmg_background.tiff"
      // For now we have all 3 platform icons checked in and we change
      // the icons manually. Fix this when the other platforms have the
      // same mechanisms for our .ico and .svg files
      icnsPath = "$projectHome/../adt/idea/adt-branding/src/artwork/AndroidStudio.icns"
      icnsPathForEAP = "$projectHome/../adt/idea/adt-branding/src/artwork/preview/AndroidStudio.icns"
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
      context.ant.copy(file: "$androidRoot/adt-ui/lib/libwebp/mac/libwebp_jni64.dylib", tofile: "$targetDirectory/plugins/android/lib/libwebp_jni64.dylib")

      context.ant.copy(todir: "$targetDirectory/bin/lldb") {
        fileset(dir: "$root/prebuilts/tools/darwin-x86_64/lldb")
      }
      extraExecutables.add("bin/lldb/bin/LLDBFrontend")
      extraExecutables.add("bin/lldb/bin/llvm-symbolizer")
      extraExecutables.add("bin/lldb/bin/minidump_stackwalk")

      def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
      context.ant.copy(todir: "$simpleperfTarget/darwin-x86") {
        fileset(dir: "$root/prebuilts/tools/darwin-x86/simpleperf")
      }
      extraExecutables.add("plugins/android/resources/simpleperf/darwin-x86/simpleperf")

      context.ant.copy(todir: "$simpleperfTarget/darwin-x86_64") {
        fileset(dir: "$root/prebuilts/tools/darwin-x86_64/simpleperf")
      }
      extraExecutables.add("plugins/android/resources/simpleperf/darwin-x86_64/simpleperf")
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    new StudioMacDistributionCustomizer(projectHome)
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo) { "AndroidStudio${applicationInfo.isEAP ? "Preview" : ""}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "studio" }
}
