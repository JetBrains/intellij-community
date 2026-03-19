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

import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS
import org.jetbrains.intellij.build.impl.PatchOverwriteMode
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.impl.PlatformJarNames.TEST_FRAMEWORK_JAR
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.pluginAuto
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.CommunityProductFragments
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
import java.nio.file.Path
import java.util.function.BiPredicate
import kotlin.io.path.copyTo
import kotlin.io.path.createParentDirectories

/**
 * Configures the Android Studio distribution by specifying bundled plugins, JVM args, extra files, and more.
 * See also: BaseIdeaProperties, IdeaCommunityProperties, CLionProperties.
 */
class AndroidStudioProperties : ProductProperties() {

  companion object {
    private val INHERITED_PLUGINS = IDEA_BUNDLED_PLUGINS

    private val EXTRA_PLUGINS = listOf(
      // Bundle Mercurial support for use in ASwB.
      "intellij.vcs.hg",
      // Bundle the ML completion ranking plugins, just like IntelliJ IDEA does (b/456525685).
      "intellij.completionMlRanking",
      "intellij.turboComplete",
      // Android Studio: package CIDR plugins.
      "intellij.cidr.clangd",
      "intellij.c",
      "intellij.cidr.debugger",
      "intellij.cidr.base",
    )

    private val EXCLUDED_PLUGINS = listOf(
      "intellij.android.gradle.dsl",
      "intellij.android.gradle.declarative.lang.ide",
      "intellij.eclipse",
      "intellij.featuresTrainer",
      "intellij.java.byteCodeViewer",
      "intellij.maven",
      "intellij.mcpserver",
    )
  }

  override val baseFileName: String = "studio"

  init {
    configurePropertiesForAllEditionsOfIntelliJIdea(this)
    platformPrefix = "AndroidStudio"
    mainClassName = "com.android.tools.idea.MainWrapper"
    applicationInfoModule = "intellij.android.adt.branding"
    useSplash = true
    scrambleMainJar = false
    buildSourcesArchive = true

    allLibraryLicenses += AndroidStudioLibraryLicenses.LICENSES_LIST
    includeIntoSourcesArchiveFilter = BiPredicate { _, _ -> true }
    customJvmMemoryOptions = customJvmMemoryOptions.plus(arrayOf("-Xms" to "256m", "-Xmx" to "2048m"))
    additionalIdeJvmArguments = mutableListOf(
      // Reduces the chance of truncated JFR stacks (ag/I16b829882).
      "-XX:FlightRecorderOptions=stackdepth=256",
      // Required by instantapps-api.jar (ag/I55803b347).
      "--add-opens=java.base/sun.net.www.protocol.https=ALL-UNNAMED",
      // Enable use of the deprecated SecurityManager (b/302171264).
      "-Djava.security.manager=allow",
      // Configure the feedback URL displayed for IDE startup failures. This system property should match
      // StartupErrorReporter.STARTUP_ERROR_REPORTING_URL_PROPERTY. Eventually we may want a better landing page (b/295896403).
      "-Dij.startup.error.report.url=https://issuetracker.google.com/issues/new?component=192708",
      // Workaround for C2 crashes b/377324522
      "-XX:CompileCommand=exclude,org.jetbrains.kotlin.serialization.deserialization.TypeDeserializer::simpleType",
      "-XX:CompileCommand=exclude,org.jetbrains.kotlin.serialization.deserialization.TypeDeserializer::toAttributes",
      )

    productLayout.productImplementationModules = listOf(
      // From IdeaCommunityProperties:
      "intellij.platform.starter",
      "intellij.idea.community.customization",
    )
    productLayout.addPlatformSpec { layout, _ ->
      // From JetBrainsProductProperties.
      layout.withModule(IJENT_BOOT_CLASSPATH_MODULE, PLATFORM_CORE_NIO_FS)

      layout.withModule("intellij.android.adt.branding", "resources.jar")
      layout.withModule("intellij.cidr.common.testFramework.core", TEST_FRAMEWORK_JAR)
      layout.withModule("intellij.cidr.common.testFramework.core.nolang", TEST_FRAMEWORK_JAR)

      // used for compose and jewel related testing in the Android plugin
      layout.withModule("intellij.platform.jewel.intUi.standalone", TEST_FRAMEWORK_JAR)
      layout.withModule("intellij.platform.jewel.markdown.intUiStandaloneStyling", TEST_FRAMEWORK_JAR)
      layout.withModuleLibrary("org.jetbrains.compose.ui.ui.test.junit4.desktop", "intellij.libraries.compose.foundation.desktop.junit", TEST_FRAMEWORK_JAR)

      layout.withProjectLibrary("assertJ", TEST_FRAMEWORK_JAR) // Used by the CIDR test framework (b/295336541).
      layout.withProjectLibrary("hamcrest", TEST_FRAMEWORK_JAR) // Used by the CIDR test framework (b/295336541).

      // Move kotlinx-coroutines-guava to core, making it accessible to the Android plugin.
      // Note: we could bundle kotlinx-coroutines-guava in the Android plugin separately, but that's risky because (1) the library
      // version should be consistent with the coroutines library in the platform, and (2) kotlinx-coroutines-guava is used in some
      // platform modules, leading to duplicated classes on the runtime classpath in some situations (depending on classloader configuration).
      // For precedent, see IntelliJ commit https://github.com/JetBrains/intellij-community/commit/bb6d3cf0ac.
      layout.withProjectLibrary("kotlinx-coroutines-guava")

      // b/376902207: JetBrains apparently converted rml.dfa into a product module in CIDR commit 0f8319e82a.
      // Note that there is an associated V2 module rml.dfa.impl referenced from AndroidStudioPlugin.xml.
      layout.withModule("intellij.rml.dfa")

      layout.withPatch { patcher, context ->
        // Patch AndroidStudioProperties.xml: set the platform API version to match the 3-component
        // IntelliJ IDEA build number. At runtime, it will be used by ApplicationInfo.getApiVersion()
        // and PluginManagerCore.getBuildNumber() for plugin compatibility purposes.
        val apiVersion = context.buildNumber.split('.').take(3).joinToString(".")
        val appInfoPath = "idea/AndroidStudioApplicationInfo.xml"
        val appInfoModule = context.findApplicationInfoModule()
        val original = checkNotNull(context.outputProvider.readFileContentFromModuleOutput(appInfoModule, appInfoPath)).toString(Charsets.UTF_8)
        val patched = original.replace(Regex("<build (.*?)/>"), "<build $1 apiVersion=\"$apiVersion\"/>")
        patcher.patchModuleOutput(applicationInfoModule, appInfoPath, patched, PatchOverwriteMode.TRUE)
      }
    }

    // skip unresolved content modules similar to IdeaCommunityProperties, otherwise unresolved modules e.g.
    // intellij.yaml.frontend.split as of 2025.1 will fail the build (b/398025574)
    productLayout.skipUnresolvedContentModules = true

    val unknownExcludedPlugins = EXCLUDED_PLUGINS - INHERITED_PLUGINS
    check(unknownExcludedPlugins.isEmpty()) { "AndroidStudioProperties.EXCLUDED_PLUGINS contains nonexistent plugins: $unknownExcludedPlugins" }

    val duplicateExtraPlugins = EXTRA_PLUGINS.intersect(INHERITED_PLUGINS)
    check(duplicateExtraPlugins.isEmpty()) { "AndroidStudioProperties.EXTRA_PLUGINS contains plugins already inherited from IntelliJ: $duplicateExtraPlugins" }

    val bundledPlugins = (INHERITED_PLUGINS + EXTRA_PLUGINS - EXCLUDED_PLUGINS.toSet()).toPersistentList()
    productLayout.bundledPluginModules = bundledPlugins

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false

    val inheritedPluginLayouts = COMMUNITY_REPOSITORY_PLUGINS.removeAll { it.mainModule !in bundledPlugins || it.mainModule == "intellij.performanceTesting" }
    productLayout.pluginLayouts = inheritedPluginLayouts.addAll(listOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.groovyPlugin(),
      // CIDR plugins migrated to v2 layouts, thus the included modules are computed
      // mostly automatically based on the <content> tag in plugin.xml. Additional
      // customization is mostly inspired by CidrPluginLayouts.kt.
      pluginAuto("intellij.cidr.clangd") { spec ->
        copyCidrLicense(spec)
      },
      pluginAuto("intellij.c") { spec ->
        copyCidrLicense(spec)
        spec.excludeProjectLibrary("Kryo")
        spec.excludeProjectLibrary("Objenesis")
      },
      pluginAuto("intellij.cidr.debugger") { spec ->
        copyCidrLicense(spec)
        spec.withProjectLibrary("antlr4-runtime")
        spec.withModule("intellij.nativeDebug") // For NativeDebugPlugin.xml.
      },
      pluginAuto("intellij.cidr.base") { spec ->
        copyCidrLicense(spec)
      },
      pluginAuto("intellij.performanceTesting") { spec ->
        spec.withProjectLibrary("assertJ")
      }
    ))

    // Fill in the remaining plugin layouts (including "trivial-layout" plugins)
    // so we can correctly patch all plugin layouts below.
    productLayout.pluginLayouts = getPluginLayoutsByJpsModuleNames(bundledPlugins, productLayout).toPersistentList()

    // Patch plugin.xml files to ensure plugins are non-updatable. We want platform
    // plugins to always come from our own IntelliJ fork (which may have patches, for example).
    // Note: this logic is validated by an assertion in check_plugin.py in our Bazel build.
    for (pluginLayout in productLayout.pluginLayouts) {
      val delegatePatcher = pluginLayout.pluginXmlPatcher
      pluginLayout.pluginXmlPatcher = { pluginXml, ctx ->
        delegatePatcher(pluginXml, ctx).replace("allow-bundled-update=\"true\"", "allow-bundled-update=\"false\"")
      }
    }
  }

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    // This is loosely based on IdeaCommunityProperties but tailored for Android Studio.
    alias("com.intellij.modules.androidstudio")
    alias("com.intellij.modules.java-capable")
    alias("com.intellij.modules.python-core-capable") // The Python plugin can be installed.
    alias("com.intellij.modules.python-in-non-pycharm-ide-capable") // Enable Non-Pycharm-IDE support in the Python plugin.

    include(CommunityProductFragments.javaIdeBaseFragment())
    moduleSet(CommunityModuleSets.ideCommon())
    moduleSet(CommunityModuleSets.debuggerStreams())
    module("intellij.platform.coverage")
    module("intellij.platform.coverage.agent")
    module("intellij.platform.customization.min")

    module("intellij.rml.dfa.impl") // For CIDR.
  }

  private fun copyCidrLicense(spec: PluginLayout.PluginLayoutBuilder) {
    spec.withGeneratedResources { pluginDir, context ->
      val source = context.paths.communityHomeDir.resolve("CIDR_LICENSE.txt")
      val target = pluginDir.resolve("lib/LICENSE.txt")
      target.createParentDirectories()
      source.copyTo(target)
    }
  }

  private fun clangdPluginDirName(): String {
    val clangdPlugin = productLayout.pluginLayouts.single { p -> p.mainModule == "intellij.cidr.clangd" }
    return clangdPlugin.directoryName
  }

  override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    FileSet(context.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(targetDir)
    FileSet(context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"))
      .includeAll()
      .copyToDir(targetDir.resolve("bin"))
    FileSet(context.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/lldb/helpers"))
      .includeAll()
      .copyToDir(targetDir.resolve("bin/lldb/helpers"))
    FileSet(context.paths.communityHomeDir.resolve("../../tools/vendor/intellij/cidr/cidr-debugger/bin/helpers"))
      .includeAll()
      .copyToDir(targetDir.resolve("bin/helpers"))
    return super.copyAdditionalFiles(targetDir, context)
  }

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer? {
    return object : WindowsDistributionCustomizer() {
      init {
        icoPath = projectHome.resolve("adt-branding/resources/artwork/androidstudio.ico")
        icoPathForEAP = projectHome.resolve("adt-branding/resources/artwork/preview/androidstudio.ico")
        buildZipArchiveWithBundledJre = false
        buildZipArchiveWithoutBundledJre = true
        installerImagesPath = projectHome.resolve("build/conf/ideaCE/win/images")
      }

      override val fileAssociations: List<String> = listOf(".java", ".groovy", ".kt")

      override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = "Android Studio"

      override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String = "Android Studio"

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

      override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String {
        return "https://www.jetbrains.com/idea/uninstall/?edition=IC-${appInfo.majorVersion}.${appInfo.minorVersion}"
      }

      override suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/win/x64"))
          .includeAll()
          .copyToDir(targetDir.resolve("plugins/${clangdPluginDirName()}/bin/clang/win/x64/bin"))

        GameTools(context, OsFamily.WINDOWS, JvmArchitecture.x64).copyAdditionalFiles(targetDir.resolve("bin"))
      }
    }
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : LinuxDistributionCustomizer() {
      init {
        buildArtifactWithoutRuntime = true
        iconPngPath = Path.of("$projectHome/adt-branding/resources/artwork/icon_AS_128.png")
        iconPngPathForEAP = Path.of("$projectHome/adt-branding/resources/artwork/preview/icon_AS_128.png")
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

      override suspend fun copyAdditionalFiles(targetDir: Path, arch: JvmArchitecture, context: BuildContext) {
        FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/linux/x64"))
          .includeAll()
          .copyToDir(targetDir.resolve("plugins/${clangdPluginDirName()}/bin/clang/linux/x64/bin"))

        GameTools(context, OsFamily.LINUX, arch).copyAdditionalFiles(targetDir.resolve("bin"))
      }

      override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, targetLibcImpl: LibcImpl, context: BuildContext): Sequence<String> {
        return super.generateExecutableFilesPatterns(includeRuntime, arch, targetLibcImpl, context)
          .plus(KotlinBinaries.kotlinCompilerExecutables)
          .filterNot { it == "plugins/**/*.sh" }
      }
    }
  }

  inner class StudioMacDistributionCustomizer(projectHome: Path) : MacDistributionCustomizer() {
    init {
      urlSchemes = listOf("idea")
      associateIpr = true
      bundleIdentifier = "com.google.android.studio"
      dmgImagePath = projectHome.resolve("build/conf/ideaCE/mac/images/dmg_background.tiff")
      // For now we have all 3 platform icons checked in and we change
      // the icons manually. Fix this when the other platforms have the
      // same mechanisms for our .ico and .svg files
      icnsPath = projectHome.resolve("adt-branding/resources/artwork/AndroidStudio.icns")
      icnsPathForEAP = projectHome.resolve("adt-branding/resources/artwork/preview/AndroidStudio.icns")
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio"

    override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path, arch: JvmArchitecture) {
      val archDir = when (arch) {
        JvmArchitecture.x64 -> "x64"
        JvmArchitecture.aarch64 -> "aarch64"
      }
      FileSet(context.paths.communityHomeDir.resolve("../../prebuilts/tools/clion/bin/clang/mac/$archDir"))
        .includeAll()
        .copyToDir(targetDir.resolve("plugins/${clangdPluginDirName()}/bin/clang/mac/$archDir/bin"))
    }

    override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, context: BuildContext): Sequence<String> {
      return super.generateExecutableFilesPatterns(includeRuntime, arch, context)
        .plus(KotlinBinaries.kotlinCompilerExecutables)
        .filterNot { it == "plugins/**/*.sh" }
    }
  }

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer? {
    return StudioMacDistributionCustomizer(projectHome)
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "_ANDROID_STUDIO_SYSTEM_SELECTOR_"

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "android-studio-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "studio"
}
