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
    buildSourcesArchive = true;
    buildCrossPlatformDistribution = true

    allLibraryLicenses.addAll(AndroidStudioLibraryLicenses.LICENSES_LIST)

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

    productLayout.bundledPluginModules = ProductModulesLayout.DEFAULT_BUNDLED_PLUGINS + BUNDLED_PLUGIN_MODULES
    productLayout.mainModules = ["intellij.idea.community.main"]
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false
    productLayout.allNonTrivialPlugins = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + [
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin([]),
    ]
    productLayout.classesLoadingOrderFilePath = "$home/build/order.txt"
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

      context.ant.copy(todir: "$targetDirectory/bin/clang/mac") {
        fileset(dir: "$root/prebuilts/tools/clion/bin/clang/mac")
      }
      extraExecutables.add("bin/clang/mac/clangd")
      extraExecutables.add("bin/clang/mac/clang-tidy")
    }
  }

  @Override
  MacDistributionCustomizer createMacCustomizer(String projectHome) {
    new StudioMacDistributionCustomizer(projectHome)
  }

  @Override
  String getSystemSelector(ApplicationInfoProperties applicationInfo, String buildNumber) { "AndroidStudio${applicationInfo.isEAP ? "Preview" : ""}${applicationInfo.majorVersion}.${applicationInfo.minorVersionMainPart}" }

  @Override
  String getBaseArtifactName(ApplicationInfoProperties applicationInfo, String buildNumber) { "android-studio-$buildNumber" }

  @Override
  String getOutputDirectoryName(ApplicationInfoProperties applicationInfo) { "studio" }
}
