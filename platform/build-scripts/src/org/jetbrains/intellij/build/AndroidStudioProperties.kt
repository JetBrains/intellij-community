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
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.plugin
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Configures the Android Studio distribution by specifying bundled plugins, JVM args, extra files, and more.
 * See also: BaseIdeaProperties, IdeaCommunityProperties, CLionProperties.
 */
class AndroidStudioProperties(home: Path) : BaseIdeaProperties() {

  companion object {
    private val INHERITED_PLUGINS = IDEA_BUNDLED_PLUGINS

    private val EXTRA_PLUGINS = listOf(
      // We bundle DevKit for ASwB development purposes (b/308477340).
      "intellij.devkit",
      // Android Studio: package CIDR plugins. This list is based on what we have been shipping in Android Studio
      // and the structure of CIDR plugins.
      "intellij.c.clangd.plugin",
      "intellij.c.clangdBridge.plugin",
      "intellij.c.plugin",
      "intellij.cidr.debugger.plugin",
      "intellij.cidr.base.plugin",
      "intellij.cidr.clangConfig.plugin",
      "intellij.cidr.clangFormat.plugin",
      "intellij.rml.dfa.ide",
    )

    private val EXCLUDED_PLUGINS = listOf(
      "intellij.settingsSync", // Not supported yet in Studio (b/267070185).
      "intellij.android.gradle.dsl",
      "intellij.eclipse",
      "intellij.featuresTrainer",
      "intellij.gradle.analysis",
      "intellij.gradle.dependencyUpdater",
      "intellij.gradle.java.maven",
      "intellij.grazie",
      "intellij.java.byteCodeViewer",
      "intellij.java.guiForms.designer",
      "intellij.maven",
      "intellij.platform.tracing.ide",
      "intellij.searchEverywhereMl",
      "intellij.statsCollector",
    )
  }

  override val baseFileName: String = "studio"

  init {
    platformPrefix = "AndroidStudio"
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
    additionalIdeJvmArguments = mutableListOf(
      "-XX:FlightRecorderOptions=stackdepth=256", // Reduces the chance of truncated JFR stacks (ag/I16b829882).
      "--add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED", // Required by instantapps-api.jar (ag/I55803b347).
      "-Didea.required.plugins.id=org.jetbrains.kotlin", // Stopgap solution to ensure the Kotlin plugin stays enabled (b/202048599).
      "-Djava.security.manager=allow", // Enable use of the deprecated SecurityManager (b/302171264).
    )

    embeddedJetBrainsClientMainModule = null // Overrides org.jetbrains.intellij.build.configureJetBrainsProduct().
    productLayout.productImplementationModules = listOf(
      // From IdeaCommunityProperties:
      "intellij.platform.main",
      "intellij.idea.customization.base",
      "intellij.idea.community.customization",
    )
    productLayout.addPlatformSpec { layout, _ ->
      // From IdeaCommunityProperties:
      layout.withModule("intellij.platform.duplicates.analysis")
      layout.withModule("intellij.platform.structuralSearch")

      layout.withModule("intellij.android.adt.branding", "resources.jar")
      layout.withModule("intellij.cidr.common.testFramework.core", TEST_FRAMEWORK_JAR)
      layout.withProjectLibrary("assertJ", TEST_FRAMEWORK_JAR) // Used by the CIDR test framework (b/295336541).
      layout.withProjectLibrary("hamcrest", TEST_FRAMEWORK_JAR) // Used by the CIDR test framework (b/295336541).

      layout.withPatch { patcher, context ->
        // Patch AndroidStudioProperties.xml: set the platform API version to match the 3-component
        // IntelliJ IDEA build number. At runtime, it will be used by ApplicationInfo.getApiVersion()
        // and PluginManagerCore.getBuildNumber() for plugin compatibility purposes.
        val apiVersion = context.buildNumber.split('.').take(3).joinToString(".")
        val appInfoPath = "idea/AndroidStudioApplicationInfo.xml"
        val moduleOutDir = context.getModuleOutputDir(context.findApplicationInfoModule())
        val original = Files.readString(moduleOutDir.resolve(appInfoPath))
        val patched = original.replace(Regex("<build (.*?)/>"), "<build $1 apiVersion=\"$apiVersion\"/>")
        patcher.patchModuleOutput(applicationInfoModule, appInfoPath, patched, overwrite = true)
      }
    }

    val unknownExcludedPlugins = EXCLUDED_PLUGINS - INHERITED_PLUGINS
    check(unknownExcludedPlugins.isEmpty()) { "AndroidStudioProperties.EXCLUDED_PLUGINS contains nonexistent plugins: $unknownExcludedPlugins" }
    val bundledPlugins = INHERITED_PLUGINS + EXTRA_PLUGINS - EXCLUDED_PLUGINS.toSet()
    productLayout.bundledPluginModules = bundledPlugins.toMutableList()

    productLayout.mainModules = listOf("intellij.idea.community.main")
    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false

    val inheritedPluginLayouts = COMMUNITY_REPOSITORY_PLUGINS.removeAll { it.mainModule !in bundledPlugins }
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
        spec.withModule("intellij.cidr.base", spec.mainJarName)
        spec.withModule("intellij.cidr.projectModel", spec.mainJarName)
        spec.withModule("intellij.cidr.workspaceModel", spec.mainJarName)
        spec.withModule("intellij.cidr.lang.base", spec.mainJarName)
        spec.withModule("intellij.cidr.execution", spec.mainJarName)
        spec.withModule("intellij.cidr.util", spec.mainJarName)
        spec.withModule("intellij.cidr.util.serializer", spec.mainJarName)
        spec.withModule("intellij.cidr.util.ui", spec.mainJarName)
        spec.withModule("intellij.cidr.asm", spec.mainJarName)
        spec.withModule("intellij.cidr.asm.debugger", spec.mainJarName)
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
      plugin("intellij.c.clangd.plugin") { spec ->
        spec.withModule("intellij.c.clangd")
      },
      plugin("intellij.c.clangdBridge.plugin") { spec ->
        spec.withModule("intellij.c.clangdBridge")
      },
      plugin("intellij.cidr.clangConfig.plugin") { spec ->
        spec.withModule("intellij.cidr.clangConfig")
      },
      plugin("intellij.cidr.clangFormat.plugin") { spec ->
        spec.withModule("intellij.cidr.clangFormat")
      },
      plugin("intellij.rml.dfa.ide") { spec ->
        spec.withModule("intellij.rml.dfa")
      },
    ))

    // IntelliJ normally excludes the DevKit plugin from public builds, but we need it for ASwB development purposes (b/308477340).
    val devkitPluginLayout = productLayout.pluginLayouts.first { it.mainModule == "intellij.devkit" }
    devkitPluginLayout.bundlingRestrictions.includeInDistribution = PluginDistribution.ALL
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

    // Copy CIDR license to CIDR plugins.
    FileSet(context.paths.communityHomeDir)
      .include("CIDR_LICENSE.txt")
      .copyToDir(Path.of(targetDirectory, "plugins/c-clangd-plugin/lib/LICENSE.txt"))
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
        buildZipArchiveWithBundledJre = false
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

      override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/win/x64"))
          .includeAll()
          .copyToDir(targetDir.resolve("plugins/c-clangd-plugin/bin/clang/win/x64"))

        GameTools(context, OsFamily.WINDOWS, JvmArchitecture.x64).copyAdditionalFiles(targetDir.resolve("bin"))
      }
    }
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : LinuxDistributionCustomizer() {
      init {
        buildArtifactWithoutRuntime = true
        iconPngPath = "$projectHome/adt-branding/src/artwork/icon_AS_128.png"
        iconPngPathForEAP = "$projectHome/adt-branding/src/artwork/preview/icon_AS_128.png"
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

      override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/linux/x64"))
          .includeAll()
          .copyToDir(targetDir.resolve("plugins/c-clangd-plugin/bin/clang/linux/x64"))

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

    override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
      FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/mac"))
        .includeAll()
        .copyToDir(targetDir.resolve("plugins/c-clangd-plugin/bin/clang/mac"))
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer {
    return StudioMacDistributionCustomizer(projectHome)
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "_ANDROID_STUDIO_SYSTEM_SELECTOR_${if (appInfo.isEAP) "Preview" else ""}${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "studio"
}
