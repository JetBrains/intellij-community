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
import org.jetbrains.intellij.build.kotlin.KotlinPluginBuilder

import java.nio.file.Path
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

  AndroidStudioProperties(String home, BuildOptions buildOptions) {
    baseFileName = "studio"
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "intellij.android.adt.branding"
    useSplash = true
    additionalIDEPropertiesFilePaths = ["$home/build/conf/ideaCE.properties".toString()]
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
    productLayout.bundledPluginModules = INHERITED_PLUGINS + EXTRA_PLUGINS - EXCLUDED_PLUGINS

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

    buildContext.ant.copy(todir: targetDirectory) {
      fileset(file: "$buildContext.paths.communityHome/LICENSE.txt")
      fileset(file: "$buildContext.paths.communityHome/NOTICE.txt")
    }
    buildContext.ant.copy(todir: "$targetDirectory/bin") {
      fileset(dir: "$buildContext.paths.communityHome/build/conf/ideaCE/common/bin")
    }
    def root = "$buildContext.paths.communityHome/../.."
    buildContext.ant.copy(todir: "$targetDirectory/bin/lldb/helpers") {
      fileset(dir: "$root/tools/vendor/intellij/cidr/cidr-debugger/bin/lldb/helpers/")
    }
    buildContext.ant.copy(todir: "$targetDirectory/bin/helpers") {
      fileset(dir: "$root/tools/vendor/intellij/cidr/cidr-debugger/bin/helpers")
    }

    // Android Studio: copy CIDR license to CIRR plugins
    buildContext.ant.copy(tofile: "$targetDirectory/plugins/c-clangd/lib/LICENSE.txt") {
      fileset(file: "$buildContext.paths.communityHome/CIDR_LICENSE.txt")
    }
    buildContext.ant.copy(tofile: "$targetDirectory/plugins/c-plugin/lib/LICENSE.txt") {
      fileset(file: "$buildContext.paths.communityHome/CIDR_LICENSE.txt")
    }
    buildContext.ant.copy(tofile: "$targetDirectory/plugins/cidr-base-plugin/lib/LICENSE.txt") {
      fileset(file: "$buildContext.paths.communityHome/CIDR_LICENSE.txt")
    }
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
      void copyAdditionalFiles(BuildContext context, String targetDirectory) {
        def root = "$context.paths.communityHome/../.."
        context.ant.copy(todir: "$targetDirectory/plugins/c-clangd/bin/clang/win") {
          fileset(dir: "$root/prebuilts/tools/clion/bin/clang/win")
        }
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
      void copyAdditionalFiles(BuildContext context, Path targetDirectory, JvmArchitecture arch) {
        def root = "$context.paths.communityHome/../.."

        context.ant.copy(todir: "$targetDirectory/plugins/c-clangd/bin/clang/linux") {
          fileset(dir: "$root/prebuilts/tools/clion/bin/clang/linux")
        }
        extraExecutables.add("plugins/c-clangd/bin/clang/linux/clangd")
        extraExecutables.add("plugins/c-clangd/bin/clang/linux/clang-tidy")
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
    void copyAdditionalFiles(BuildContext context, String targetDirectory) {
      def root = "$context.paths.communityHome/../.."

      context.ant.copy(todir: "$targetDirectory/plugins/c-clangd/bin/clang/mac") {
        fileset(dir: "$root/prebuilts/tools/clion/bin/clang/mac")
      }
      extraExecutables.add("plugins/c-clangd/bin/clang/mac/clangd")
      extraExecutables.add("plugins/c-clangd/bin/clang/mac/clang-tidy")
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
}
