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

import kotlinx.collections.immutable.putAll
import org.jetbrains.intellij.build.CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.ModuleItem
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.plugin
import java.nio.file.Path
import java.util.function.BiPredicate

// Based on the IdeaCommunityProperties definition
// TODO: Need Windows installer and Mac DMG images
// TODO: Need uninstaller feedback URL
// Consider switching from AI to AS code
// Restore a lot of the custom logic from studio_properties
// TODO: Use separate bundle identifier for EAP and non-EAP
class AndroidStudioProperties(home: Path) : BaseIdeaProperties() {

  companion object {
    private val INHERITED_PLUGINS = IDEA_BUNDLED_PLUGINS + "intellij.javaFX.community"

    private val EXTRA_PLUGINS = listOf(
      // Android Studio: package CIDR plugins. This list is based on what we have been shipping in Android Studio
      // and the structure of CIDR plugins.
      "intellij.c.clangd",
      "intellij.c.clangdBridge",
      "intellij.c.plugin",
      "intellij.cidr.debugger.plugin",
      "intellij.cidr.base.plugin",
      "intellij.cidr.clangConfig",
      "intellij.cidr.clangFormat",
    )

    private val EXCLUDED_PLUGINS = listOf(
      "intellij.settingsSync", // Not supported yet in Studio (b/267070185).
      "intellij.android.design-plugin",
      "intellij.android.gradle.dsl",
      "intellij.android.plugin",
      "intellij.android.smali",
      "intellij.ant",
      "intellij.eclipse",
      "intellij.featuresTrainer",
      "intellij.gradle.analysis",
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
      "intellij.xpath",
    )
  }

  override val baseFileName: String = "studio"

  init {
    platformPrefix = "AndroidStudio"
    productCode = "AI"
    applicationInfoModule = "intellij.android.adt.branding"
    useSplash = true
    additionalIDEPropertiesFilePaths = listOf(home.resolve("build/conf/ideaCE.properties"))
    toolsJarRequired = true
    scrambleMainJar = false
    buildSourcesArchive = true
    buildCrossPlatformDistribution = true

    allLibraryLicenses += AndroidStudioLibraryLicenses.LICENSES_LIST
    includeIntoSourcesArchiveFilter = BiPredicate { _, _ -> true }
    customJvmMemoryOptions = customJvmMemoryOptions.putAll(arrayOf("-Xms" to "256m", "-Xmx" to "2048m"))
    additionalIdeJvmArguments = mutableListOf("-XX:FlightRecorderOptions=stackdepth=256")

    productLayout.productImplementationModules =
      listOf("intellij.idea.community.resources", "intellij.platform.duplicates.analysis", "intellij.platform.main", "intellij.platform.structuralSearch") -
      listOf("intellij.platform.jps.model.impl", "intellij.platform.jps.model.serialization")
    productLayout.addPlatformSpec { layout, _ ->
      layout.withModules(listOf("intellij.android.adt.branding").map { ModuleItem(moduleName = it, relativeOutputFile = "resources.jar", reason = null) })
      layout.withModules(listOf("intellij.cidr.common.testFramework.core").map { ModuleItem(moduleName = it, relativeOutputFile = "testFramework.jar", reason = null) })
    }

    val unknownExcludedPlugins = EXCLUDED_PLUGINS - INHERITED_PLUGINS
    check(unknownExcludedPlugins.isEmpty()) { "AndroidStudioProperties.EXCLUDED_PLUGINS contains nonexistent plugins: $unknownExcludedPlugins" }
    productLayout.bundledPluginModules.clear()
    productLayout.bundledPluginModules.addAll(INHERITED_PLUGINS + EXTRA_PLUGINS - EXCLUDED_PLUGINS)

    productLayout.mainModules = listOf("intellij.idea.community.main")
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false

    val inheritedPluginLayouts = COMMUNITY_REPOSITORY_PLUGINS.removeAll {
      // Remove plugin layouts that reference modules that do not exist in our fork.
      it.mainModule in EXCLUDED_PLUGINS || it.mainModule == "intellij.python.community.plugin"
    }
    productLayout.pluginLayouts = inheritedPluginLayouts.addAll(listOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin(),
      plugin("intellij.cidr.debugger.plugin") { spec ->
        spec.withModule("intellij.cidr.debugger", spec.mainJarName)
        spec.withModule("intellij.cidr.debugger.backend", spec.mainJarName)
        spec.withModule("intellij.cidr.debugger.commandInterpreterLang", spec.mainJarName)
        spec.withModule("intellij.cidr.core", spec.mainJarName)
        spec.withModule("intellij.cidr.util.execution", spec.mainJarName)
      },
      plugin("intellij.cidr.base.plugin") { spec ->
        spec.withModule("intellij.c.dfa", spec.mainJarName)
        spec.withModule("intellij.cidr.base", spec.mainJarName)
        spec.withModule("intellij.cidr.projectModel", spec.mainJarName)
        spec.withModule("intellij.cidr.workspaceModel", spec.mainJarName)
        spec.withModule("intellij.cidr.lang.base", spec.mainJarName)
        spec.withModule("intellij.cidr.execution", spec.mainJarName)
        spec.withModule("intellij.cidr.util", spec.mainJarName)
        spec.withModule("intellij.cidr.util.serializer", spec.mainJarName)
        spec.withModule("intellij.cidr.util.ui", spec.mainJarName)
        // Note the following are in CLionProperties.groovy but we don't include them since
        // they were never shipped with Android Studio before.
        //   * intellij.cidr.toolchains
        //   * intellij.platform.ssh.nio
        //   * intellij.apple.sdk
        // The following are not in CLionProperties.groovy for this plugin. Instead they
        // are put under plugin "intellij.clion" or IDE implementation. We put them under
        // this base plugin so that they will still be shipped.
        spec.withModule("intellij.cidr.psi.base", spec.mainJarName)
        spec.withModule("intellij.cidr.common", spec.mainJarName)
        spec.withModule("intellij.cidr.runner", spec.mainJarName)
        spec.withModule("intellij.cmake.psi", spec.mainJarName)
      },
      plugin("intellij.c.plugin") { spec ->
        spec.withModule("intellij.c", spec.mainJarName)
        spec.withModule("intellij.c.debugger", spec.mainJarName)
        spec.withModule("intellij.c.doxygen", spec.mainJarName)
        spec.withModule("intellij.c.testing", spec.mainJarName)
        spec.withModule("intellij.cidr.modulemap.language", spec.mainJarName)
      },
      PluginLayout.plugin("intellij.c.clangd"),
      PluginLayout.plugin("intellij.c.clangdBridge"),
      PluginLayout.plugin("intellij.cidr.clangConfig"),
      PluginLayout.plugin("intellij.cidr.clangFormat"),
    ))
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
    FileSet(context.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(Path.of(targetDirectory))
    FileSet(context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin"))
    FileSet(context.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/lldb/helpers"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin/lldb/helpers"))
    FileSet(context.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/helpers"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin/helpers"))

    // Android Studio: copy CIDR license to CIRR plugins
    FileSet(context.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/c-clangd/lib/LICENSE.txt"))
    FileSet(context.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/c-plugin/lib/LICENSE.txt"))
    FileSet(context.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/cidr-base-plugin/lib/LICENSE.txt"))

    return super.copyAdditionalFiles(context, targetDirectory)
  }

  override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer {
    return object : WindowsDistributionCustomizer() {
      init {
        icoPath = "$projectHome/adt-branding/src/artwork/androidstudio.ico"
        icoPathForEAP = "$projectHome/adt-branding/src/artwork/preview/androidstudio.ico"
        buildZipArchiveWithoutBundledJre = true
        installerImagesPath = "$projectHome/build/conf/ideaCE/win/images"
        fileAssociations = listOf(".java", ".groovy", ".kt")
      }

      override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = "Android Studio"

      override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String = "Android Studio"

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

      override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String {
        return "https://www.jetbrains.com/idea/uninstall/?edition=IC-${appInfo.majorVersion}.${appInfo.minorVersion}"
      }

      override fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/win/x64"))
          .includeAll()
          .copyToDir(targetDirectory.resolve("plugins/c-clangd/bin/clang/win/x64"))

        GameTools(context, OsFamily.WINDOWS, JvmArchitecture.x64).copyAdditionalFiles(targetDirectory.resolve("bin"))
      }
    }
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : LinuxDistributionCustomizer() {
      init {
        buildTarGzWithoutBundledRuntime = true
        buildOnlyBareTarGz = true
        iconPngPath = "$projectHome/adt-branding/src/artwork/icon_AS_128.png"
        iconPngPathForEAP = "$projectHome/adt-branding/src/artwork/preview/icon_AS_128.png"
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

      override fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/linux/x64"))
          .includeAll()
          .copyToDir(targetDir.resolve("plugins/c-clangd/bin/clang/linux/x64"))

        GameTools(context, OsFamily.LINUX, arch).copyAdditionalFiles(targetDir.resolve("bin"))
      }
    }
  }

  class StudioMacDistributionCustomizer(projectHome: String) : MacDistributionCustomizer() {
    init {
      urlSchemes = listOf("idea")
      associateIpr = true
      bundleIdentifier = "com.google.android.studio"
      dmgImagePath = "$projectHome/build/conf/ideaCE/mac/images/dmg_background.tiff"
      // For now we have all 3 platform icons checked in and we change
      // the icons manually. Fix this when the other platforms have the
      // same mechanisms for our .ico and .svg files
      icnsPath = "$projectHome/adt-branding/src/artwork/AndroidStudio.icns"
      icnsPathForEAP = "$projectHome/adt-branding/src/artwork/preview/AndroidStudio.icns"
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
      return if (appInfo.isEAP) "Android Studio Preview.app" else "Android Studio.app"
    }

    override fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: Path, arch: JvmArchitecture) {
      FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/mac"))
        .includeAll()
        .copyToDir(targetDirectory.resolve("plugins/c-clangd/bin/clang/mac"))
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer {
    return StudioMacDistributionCustomizer(projectHome)
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "AndroidStudio${if (appInfo.isEAP) "Preview" else ""}${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "studio"
}
