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
    scrambleMainJar = false
    buildSourcesArchive = buildOptions.studioSdk;
    buildCrossPlatformDistribution = true

    allLibraryLicenses.addAll(AndroidStudioLibraryLicenses.LICENSES_LIST)

    if (!buildOptions.studioSdk) {
    // TODO: This doesn't cover all used libraries, but it's exactly what ShowLicensesUsedAction is checking.
      additionalDirectoriesWithLicenses.addAll(
        "$home/../adt/idea/android/lib/licenses",
        "$home/../studio/google/appindexing/lib/licenses",
        "$home/../studio/google/cloud/testing/firebase-testing/lib/licenses",
        "$home/../studio/google/cloud/testing/test-recorder/lib/licenses",
        "$home/../studio/google/cloud/tools/android-studio-plugin/lib/licenses",
        "$home/../studio/google/cloud/tools/core-plugin/lib/licenses",
        "$home/../studio/google/cloud/tools/google-login-plugin/lib/licenses",
        "$home/../vendor/google/firebase/lib/licenses",
        "$home/../../prebuilts/studio/layoutlib/licenses",
      )
    }

    productLayout.productApiModules = JAVA_IDE_API_MODULES +
                                                  [
                                                    // Android Studio: CIDR/CLion: Must be included here to be packaged into core, not as separate plugins
                                                    "intellij.cidr.common.testFramework"
                                                  ]
    productLayout.productImplementationModules = JAVA_IDE_IMPLEMENTATION_MODULES +
                                                  [
                                                    // Android Studio: CIDR/CLion: Must be included here to be packaged into core, not as separate plugins
                                                    "intellij.cidr.common",
                                                    "intellij.cidr.debugger",
                                                    "intellij.cidr.debugger.backend",
                                                    "intellij.cidr.debugger.commandInterpreterLang",
                                                    "intellij.c",
                                                    "intellij.c.debugger",
                                                    "intellij.c.dfa",
                                                    "intellij.cidr.util",
                                                    "intellij.c.doxygen",
                                                    "intellij.cidr.core",
                                                    "intellij.cidr.execution",
                                                    "intellij.cidr.modulemap.language",
                                                    "intellij.cidr.projectModel",
                                                    "intellij.cidr.util.serializer",
                                                    "intellij.cidr.workspaceModel",
                                                    "intellij.cidr.workspaceModelBridge",
                                                    "intellij.cmake.psi",
                                                  ] +
                                                  ["intellij.platform.duplicates.analysis", "intellij.platform.structuralSearch", "intellij.platform.main"] -
                                                  ["intellij.platform.jps.model.impl", "intellij.platform.jps.model.serialization"]
    productLayout.additionalPlatformJars.putAll("resources.jar", "intellij.idea.community.resources", "intellij.android.adt.branding")

    if (!buildOptions.studioSdk) {
      // Android Studio: including the common base library to avoid classloader issues (?)
      productLayout.additionalPlatformJars.put("android-base-common.jar", "android.sdktools.common")
      // Android Studio: include metrics libraries in $install/lib
      productLayout.additionalPlatformJars.putAll("google-analytics-library.jar",
                                                  "android.sdktools.android-annotations",
                                                  "analytics-shared",
                                                  "analytics-tracker",
                                                  "analytics-publisher",
                                                  "analytics-crash")
    }

    productLayout.bundledPluginModules = ProductModulesLayout.DEFAULT_BUNDLED_PLUGINS + BUNDLED_PLUGIN_MODULES
    if (!buildOptions.studioSdk) {
      // Android Studio bundles these:
      productLayout.bundledPluginModules.addAll(
                                           "android-apk",
                                           "android-ndk",
                                           "firebase",
                                           "firebase-testing",
                                           "google-appindexing",
                                           "google-login-as",
                                           "google-cloud-tools-as",
                                           "google-cloud-tools-core-as",
                                           "google-samples",
                                           "intellij.android.plugin",
                                           "intellij.android.compose-ide-plugin",
                                           "intellij.android.layoutlib",
                                           "intellij.android.layoutlib-native",
                                           "intellij.android.smali",
                                           "test-recorder",
                                           "url-assistant")
    }
    productLayout.mainModules = ["intellij.idea.community.main"]
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false
    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin([]),
    ]
    if (!buildOptions.studioSdk) {
      productLayout.allNonTrivialPlugins.addAll(
        androidPluginInStudio([:]),
        layoutlibPlugin(),
        layoutlibNativePlugin())
    }
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
      withModule("intellij.android.app-inspection.api", "android.jar")
      withModule("intellij.android.app-inspection.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspector.api", "android.jar")
      withModule("intellij.android.app-inspection.inspector.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.ide", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.model", "android.jar")
      withModule("intellij.android.app-inspection.inspectors.workmanager.view", "android.jar")
      withModule("intellij.android.dagger", "android.jar")
      withModule("intellij.android.databinding", "android.jar")
      withModule("intellij.android.debuggers", "android.jar")
      withModule("intellij.android.emulator", "android.jar")
      withModule("intellij.android.gradle.dsl", "android.jar")
      withModule("intellij.android.lang", "android.jar")
      withModule("intellij.android.lang-databinding", "android.jar")
      withModule("intellij.android.mlkit", "android.jar")
      withModule("intellij.android.room", "android.jar")
      withModule("intellij.android.plugin", "android.jar")
      withModule("intellij.android.artwork")
      withModule("intellij.android.build-attribution", "android.jar")
      withModule("intellij.android.observable", "android.jar")
      withModule("intellij.android.observable.ui", "android.jar")
      withModule("android.sdktools.flags", "android.jar")
      withModule("intellij.android.layout-inspector", "android.jar")
      withModule("intellij.android.layout-ui", "android.jar")
      withModule("intellij.android.transport", "android.jar")
      withModule("intellij.android.designer", "android.jar")
      withModule("intellij.android.compose-designer", "android.jar")
      withModule("intellij.android.designer.customview", "android.jar")
      withModule("intellij.android.nav.editor", "android.jar")
      withModule("intellij.android.nav.safeargs", "android.jar")
      withModule("intellij.android.sdkUpdates", "android.jar")
      withModule("intellij.android.wizard", "android.jar")
      withModule("intellij.android.wizard.model", "android.jar")
      withModule("intellij.android.wizardTemplate.plugin", "wizard-template.jar")
      withModule("intellij.android.wizardTemplate.impl", "wizard-template.jar")
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
      withModule("intellij.android.projectSystem.gradle.psd", "android.jar")
      withModule("intellij.android.projectSystem.gradle.sync", "android.jar")
      withModule("intellij.android.gradle-tooling.api", "android.jar")
      withModule("intellij.android.gradle-tooling.impl", "android.jar")
      withModule("intellij.android.newProjectWizard", "android.jar")
      withModule("intellij.android.resources-base", "android.jar")
      withModule("intellij.android.testRetention", "android.jar")
      withModule("android-layout-inspector", "android.jar")
      withModule("analytics", "android.jar")
      withModule("assistant", "android.jar")
      withModule("connection-assistant", "android.jar")
      withModule("whats-new-assistant", "android.jar")
      withModule("intellij.lint", "lint-ide.jar")
      withModule("intellij.android.adt.ui", "adt-ui.jar")
      withModule("intellij.android.adt.ui.model", "adt-ui.jar")
      withModule("android.sdktools.repository")
      withModule("db-baseLibrary", "data-binding.jar")
      withModule("db-baseLibrarySupport", "data-binding.jar")
      withModule("db-compilerCommon", "data-binding.jar")
      withModule("db-compiler", "data-binding.jar")
      withModule("android.sdktools.sdklib", "sdklib.jar")
      withModule("android.sdktools.sdk-common", "sdk-common.jar")
      withModule("intellij.android.layoutlib-loader", "layoutlib-loader.jar")
      withModule("android.game-tools.main", "game-tools.jar")
      withModule("android.sdktools.manifest-merger", "manifest-merger.jar")
      withModule("android.sdktools.chunkio", "pixelprobe.jar")
      withModule("android.sdktools.pixelprobe", "pixelprobe.jar")

      withModule("android.sdktools.binary-resources", "sdk-tools.jar")
      withModule("android.sdktools.analyzer", "sdk-tools.jar")
      withModule("android.sdktools.ddmlib", "sdk-tools.jar")
      withModule("android.sdktools.dvlib", "sdk-tools.jar")
      withModule("android.sdktools.deployer", "sdk-tools.jar")
      withModule("android.sdktools.zipflinger", "sdk-tools.jar")
      withModule("android.sdktools.tracer", "sdk-tools.jar")
      withModule("android.sdktools.draw9patch", "sdk-tools.jar")
      withModule("android.sdktools.lint-api", "sdk-tools.jar")
      withModule("android.sdktools.lint-checks", "sdk-tools.jar")
      withModule("android.sdktools.lint-model", "sdk-tools.jar")
      withModule("android.sdktools.mlkit-common", "sdk-tools.jar")
      withModule("android.sdktools.ninepatch", "sdk-tools.jar")
      withModule("android.sdktools.perflib", "sdk-tools.jar")
      withModule("android.sdktools.builder-model", "sdk-tools.jar")
      withModule("android.sdktools.builder-test-api", "sdk-tools.jar")
      withModule("android.sdktools.android-annotations", "sdk-tools.jar")
      withModule("android.sdktools.layoutinspector", "sdk-tools.jar")
      withModule("usb-devices", "sdk-tools.jar")

      withModule("intellij.android.jps", "jps/android-jps-plugin.jar", null)

      withResourceFromModule("intellij.android.core", "lib/asm-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/asm-analysis-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/asm-tree-5.0.3.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/commons-compress-1.8.1.jar", "lib")
      withResourceFromModule("intellij.android.core", "lib/javawriter-2.2.1.jar", "lib")

      withResourceFromModule("intellij.android.artwork", "resources/device-art-resources", "lib/device-art-resources")
      withResourceFromModule("intellij.android.core", "lib/sampleData", "lib/sampleData")
      withResourceArchiveFromModule("intellij.android.core", "annotations", "lib/androidAnnotations.jar")

      additionalModulesToJars.entrySet().each {
        withModule(it.key, it.value)
      }
      doNotCreateSeparateJarForLocalizableResources()
    }
  }

  static PluginLayout layoutlibPlugin () {
    plugin("intellij.android.layoutlib") {
      withModule("android.sdktools.layoutlib-api")
    }
  }

  static PluginLayout layoutlibNativePlugin () {
    plugin("intellij.android.layoutlib-native") {
      withModule("android.sdktools.layoutlib-api")
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

    if (buildContext.options.studioSdk) {
      return
    }

    buildContext.ant.copy(todir: targetDirectory) {
      fileset(file: "$buildContext.paths.communityHome/LICENSE.txt")
      fileset(file: "$buildContext.paths.communityHome/NOTICE.txt")
    }
    buildContext.ant.copy(todir: "$targetDirectory/bin") {
      fileset(dir: "$buildContext.paths.communityHome/build/conf/ideaCE/common/bin")
    }

    def root = "$buildContext.paths.communityHome/../.."

    if (buildContext.options.includeUiTests) {
      bundleDependenciesForUiTests(buildContext, root, targetDirectory)
    }

    buildContext.ant.touch(file: "$targetDirectory/license/dev01_license.txt", mkdirs: true)

    // TODO: figure out if some of these misc resources can be included in a better way
    def androidPluginLib = "$targetDirectory/plugins/android/lib"

    buildContext.ant.copy(todir: "$androidPluginLib/layoutlib") {
      fileset(dir: "$root/prebuilts/studio/layoutlib") {
        include(name: "build.prop")
        include(name: "data/framework_res.jar")
        include(name: "data/layoutlib-extensions.jar")
        include(name: "data/fonts/*")
        include(name: "data/fonts/native/fonts.xml")
        include(name: "data/fonts/standard/fonts.xml")
        exclude(name: "data/fonts/BUILD")
      }
    }

    // Profiler prebuilt binaries:
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-bin/tools/base/profiler/transform/profilers-transform.jar")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-bin/tools/base/profiler/app/perfa.jar")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-bin/tools/base/profiler/app/perfa_okhttp.dex")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/transport") {
      fileset(dir: "$root/bazel-bin/tools/base/transport/android")
    }
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/transport/native/agent") {
      fileset(dir: "$root/bazel-bin/tools/base/transport/native/agent/android")
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

    // App Inspection: Inspector prebuilts
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/app-inspection/") {
      fileset(file: "$root/prebuilts/tools/common/app-inspection/androidx/sqlite/sqlite-inspection.jar")
      // TODO(b/167608400): Remove after inspector bundling is implemented
      fileset(file: "$root/prebuilts/tools/common/app-inspection/androidx/work/workmanager-inspection.jar")
    }

    // Trace agent. TODO(b/149320690): remove in 4.1 final release.
    buildContext.ant.copy(todir: "$androidPluginLib/../resources") {
      fileset(file: "$root/bazel-bin/tools/base/tracer/trace_agent.jar")
    }

    // Instant run
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/installer") {
      fileset(dir: "$root/bazel-bin/tools/base/deploy/installer/android-installer") {
        exclude(name: "test-installer")
      }
    }

    // Asset Studio images.
    buildContext.ant.copy(todir: "$androidPluginLib/../resources/images/asset_studio") {
      fileset(dir: "$root/tools/adt/idea/android/resources/images/asset_studio")
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
  private static void bundleDependenciesForUiTests(BuildContext buildContext, String root, String targetDirectory) {
    def gradleVersion = getGradleVersionToBundle(buildContext)
    buildContext.messages.block("Bundle dependencies for UI tests including zipped Gradle $gradleVersion") {
      // when creating gradle wrappers for UI test projects, we need a zipped copy of gradle to point to
      buildContext.ant.copy(todir: "$targetDirectory/gradle/gradle-$gradleVersion") {
        fileset(file: "$root/tools/external/gradle/gradle-$gradleVersion-bin.zip")
      }
      buildContext.ant.copy(todir: "$targetDirectory/gradle/m2repository/com/android/databinding") {
        fileset(dir: "$root/out/repo/com/android/databinding")
      }
      buildContext.ant.unzip(src: "$root/bazel-bin/tools/adt/idea/android/test_deps.zip", dest: "$targetDirectory/gradle/m2repository")
      buildContext.ant.
        unzip(src: "$root/bazel-bin/tools/adt/idea/uitest-framework/uitest_deps.zip", dest: "$targetDirectory/gradle/m2repository")
    }
  }

  @Override
  WindowsDistributionCustomizer createWindowsCustomizer(String projectHome) {
    return new WindowsDistributionCustomizer() {
      {
        icoPath = "$projectHome/adt-branding/src/artwork/androidstudio.ico"
        icoPathForEAP = "$projectHome/adt-branding/src/artwork/preview/androidstudio.ico"
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
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."
        context.ant.copy(todir: "$targetDirectory/bin/clang/win") {
          fileset(dir: "$root/prebuilts/tools/clion/bin/clang/win")
        }

        if (context.options.studioSdk) {
          return
        }

        def androidRoot = "$root/tools/adt/idea"
        context.ant.copy(file: "$androidRoot/native/installer/win/builds/uninstall.exe", tofile: "$targetDirectory/uninstall.exe")

        def lldbTarget = "$targetDirectory/bin/lldb/"
        context.ant.copy(todir: "$lldbTarget") {
          fileset(dir: "$root/prebuilts/tools/windows-x86_64/lldb")
        }
        context.ant.copy(todir: "$lldbTarget/lib") {
          fileset(dir: "$root/prebuilts/python/windows-x86/x64/Lib") {
            exclude(name: "test/**")
            exclude(name: "unittest/**")
            exclude(name: "config/**")
            exclude(name: "distutils/**")
            exclude(name: "idlelib/**")
            exclude(name: "lib2to3/**")
            exclude(name: "plat-linux2/**")
            exclude(name: "bsddb/test/**")
            exclude(name: "ctypes/test/**")
            exclude(name: "email/test/**")
            exclude(name: "lib-tk/test/**")
            exclude(name: "sqlite3/test/**")
          }
        }

        def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
        context.ant.copy(todir: "$simpleperfTarget/windows") {
          fileset(dir: "$root/prebuilts/tools/windows/simpleperf")
        }
        context.ant.copy(todir: "$simpleperfTarget/windows-x86_64") {
          fileset(dir: "$root/prebuilts/tools/windows-x86_64/simpleperf")
        }

        context.ant.copy(todir: "$targetDirectory/plugins/android/resources/trace_processor_daemon") {
          fileset(dir: "$root/prebuilts/tools/common/trace-processor-daemon/windows")
        }

        context.ant.copy(todir: "$targetDirectory/plugins/android/lib/layoutlib/data") {
          fileset(dir: "$root/prebuilts/studio/layoutlib/data") {
            include(name: "icu/*")
            exclude(name: "icu/BUILD")
            include(name: "win/**")
            exclude(name: "win/BUILD")
          }
        }
      }
    }
  }

  @Override
  LinuxDistributionCustomizer createLinuxCustomizer(String projectHome) {
    return new LinuxDistributionCustomizer() {
      {
        buildTarGzWithoutBundledJre = false
        iconPngPath = "$projectHome/adt-branding/src/artwork/icon_AS_128.png"
        iconPngPathForEAP = "$projectHome/adt-branding/src/artwork/preview/icon_AS_128.png"
      }

      @Override
      String getRootDirectoryName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio" }

      @Override
      @CompileDynamic
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."

        context.ant.copy(todir: "$targetDirectory/bin/clang/linux") {
          fileset(dir: "$root/prebuilts/tools/clion/bin/clang/linux")
        }
        extraExecutables.add("bin/clang/linux/clangd")
        extraExecutables.add("bin/clang/linux/clang-tidy")

        if (context.options.studioSdk) {
          return
        }

        def lldbTarget = "$targetDirectory/bin/lldb/"
        context.ant.copy(todir: "$lldbTarget") {
          fileset(dir: "$root/prebuilts/tools/linux-x86_64/lldb")
        }
        extraExecutables.add("bin/lldb/bin/LLDBFrontend")
        extraExecutables.add("bin/lldb/bin/llvm-symbolizer")
        extraExecutables.add("bin/lldb/bin/minidump_stackwalk")
        context.ant.copy(todir: "$lldbTarget/lib/python2.7") {
          fileset(dir: "$root/prebuilts/python/linux-x86/lib/python2.7") {
            exclude(name: "test/**")
            exclude(name: "unittest/**")
            exclude(name: "config/**")
            exclude(name: "distutils/**")
            exclude(name: "idlelib/**")
            exclude(name: "lib2to3/**")
            exclude(name: "plat-linux2/**")
            exclude(name: "bsddb/test/**")
            exclude(name: "ctypes/test/**")
            exclude(name: "email/test/**")
            exclude(name: "lib-tk/test/**")
            exclude(name: "sqlite3/test/**")
          }
        }

        def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
        context.ant.copy(todir: "$simpleperfTarget/linux-x86_64") {
          fileset(dir: "$root/prebuilts/tools/linux-x86_64/simpleperf")
        }
        extraExecutables.add("plugins/android/resources/simpleperf/linux-x86_64/simpleperf")

        context.ant.copy(todir: "$targetDirectory/plugins/android/resources/trace_processor_daemon") {
          fileset(dir: "$root/prebuilts/tools/common/trace-processor-daemon/linux")
        }
        extraExecutables.add("plugins/android/resources/trace_processor_daemon/trace_processor_daemon")

        context.ant.copy(todir: "$targetDirectory/plugins/android/lib/layoutlib/data") {
          fileset(dir: "$root/prebuilts/studio/layoutlib/data") {
            include(name: "icu/*")
            exclude(name: "icu/BUILD")
            include(name: "linux/**")
            exclude(name: "linux/BUILD")
          }
        }
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
      applicationInfo.isEAP ? "Android Studio ${applicationInfo.majorVersion}.${applicationInfo.minorVersion} Preview.app"
                            : "Android Studio.app"
    }

    @Override
    @CompileDynamic
    void copyAdditionalFiles(BuildContext context, String targetDirectory) {
      def root = "$context.paths.communityHome/../.."

      context.ant.copy(file: "$root/tools/idea/platform/build-scripts/tools/mac/scripts/entitlements.xml", tofile: "$targetDirectory/_codesign/entitlements.xml")

      def bundleName = getRootDirectoryName(context.applicationInfo, context.buildNumber)
      context.ant.copy(file: "$root/tools/idea/macos_codesign_filelist.txt", tofile: "$targetDirectory/_codesign/filelist")
      context.ant.replace(file: "$targetDirectory/_codesign/filelist") {
        replaceFilter(token: "@@bundle@@", value: bundleName)
      }

      context.ant.copy(todir: "$targetDirectory/bin/clang/mac") {
        fileset(dir: "$root/prebuilts/tools/clion/bin/clang/mac")
      }
      extraExecutables.add("bin/clang/mac/clangd")
      extraExecutables.add("bin/clang/mac/clang-tidy")

      if (context.options.studioSdk) {
        return
      }

      context.ant.copy(todir: "$targetDirectory/bin/lldb") {
        fileset(dir: "$root/prebuilts/tools/darwin-x86_64/lldb")
      }
      extraExecutables.add("bin/lldb/bin/LLDBFrontend")
      extraExecutables.add("bin/lldb/bin/llvm-symbolizer")
      extraExecutables.add("bin/lldb/bin/minidump_stackwalk")

      def simpleperfTarget = "$targetDirectory/plugins/android/resources/simpleperf"
      context.ant.copy(todir: "$simpleperfTarget/darwin-x86_64") {
        fileset(dir: "$root/prebuilts/tools/darwin-x86_64/simpleperf")
      }
      extraExecutables.add("plugins/android/resources/simpleperf/darwin-x86_64/simpleperf")

      context.ant.copy(todir: "$targetDirectory/plugins/android/resources/trace_processor_daemon") {
        fileset(dir: "$root/prebuilts/tools/common/trace-processor-daemon/darwin")
      }
      extraExecutables.add("plugins/android/resources/trace_processor_daemon/trace_processor_daemon")

      context.ant.copy(todir: "$targetDirectory/plugins/android/lib/layoutlib/data") {
        fileset(dir: "$root/prebuilts/studio/layoutlib/data") {
          include(name: "icu/*")
          exclude(name: "icu/BUILD")
          include(name: "mac/**")
          exclude(name: "mac/BUILD")
        }
      }
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
