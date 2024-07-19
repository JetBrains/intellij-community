// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import java.nio.file.Path

internal suspend fun createCommunityBuildContext(
  options: BuildOptions = BuildOptions(),
  projectHome: Path = COMMUNITY_ROOT.communityRoot,
): BuildContext {
  return BuildContextImpl.createContext(projectHome = projectHome,
                                        productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
                                        setupTracer = true,
                                        options = options)
}

open class IdeaCommunityProperties(private val communityHomeDir: Path) : BaseIdeaProperties() {
  companion object {
    val MAVEN_ARTIFACTS_ADDITIONAL_MODULES = persistentListOf(
      "intellij.tools.jps.build.standalone",
      "intellij.devkit.runtimeModuleRepository.jps",
      "intellij.devkit.jps",
      "intellij.idea.community.build.tasks",
      "intellij.platform.debugger.testFramework",
      "intellij.platform.vcs.testFramework",
      "intellij.platform.externalSystem.testFramework",
      "intellij.maven.testFramework",
      "intellij.tools.reproducibleBuilds.diff",
      "intellij.space.java.jps",
    )
  }

  override val baseFileName: String
    get() = "idea"

  init {
    platformPrefix = "Idea"
    applicationInfoModule = "intellij.idea.community.customization"
    additionalIDEPropertiesFilePaths = persistentListOf(communityHomeDir.resolve("build/conf/ideaCE.properties"))
    toolsJarRequired = true
    scrambleMainJar = false
    useSplash = true
    buildCrossPlatformDistribution = true

    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.idea.community.customization",
    )
    productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS
      .addAll(listOf("intellij.javaFX.community", "intellij.vcs.github.community"))
      .toMutableList()

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false
    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.addAll(listOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.androidPlugin(allPlatforms = true),
      CommunityRepositoryModules.groovyPlugin(),
    ))

    productLayout.addPlatformSpec { layout, _ ->
      layout.withModule("intellij.platform.duplicates.analysis")
      layout.withModule("intellij.platform.structuralSearch")
    }

    mavenArtifacts.forIdeModules = true
    mavenArtifacts.additionalModules = mavenArtifacts.additionalModules.addAll(MAVEN_ARTIFACTS_ADDITIONAL_MODULES)
    mavenArtifacts.squashedModules = mavenArtifacts.squashedModules.addAll(persistentListOf(
      "intellij.platform.util.base",
      "intellij.platform.util.zip",
    ))

    versionCheckerConfig = CE_CLASS_VERSIONS
    baseDownloadUrl = "https://download.jetbrains.com/idea/"
    buildDocAuthoringAssets = true

    qodanaProductProperties = QodanaProductProperties("QDJVMC", "Qodana Community for JVM")
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
    super.copyAdditionalFiles(context, targetDir)

    copyFileToDir(context.paths.communityHomeDir.resolve("LICENSE.txt"), targetDir)
    copyFileToDir(context.paths.communityHomeDir.resolve("NOTICE.txt"), targetDir)

    copyDir(
      sourceDir = context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"),
      targetDir = targetDir.resolve("bin"),
    )
    bundleExternalPlugins(context, targetDir)
  }

  protected open fun bundleExternalPlugins(context: BuildContext, targetDirectory: Path) {
    //temporary unbundle VulnerabilitySearch
    //ExternalPluginBundler.bundle('VulnerabilitySearch',
    //                             "$buildContext.paths.communityHome/build/dependencies",
    //                             buildContext, targetDirectory)
  }

  override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer = CommunityWindowsDistributionCustomizer()
  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer = CommunityLinuxDistributionCustomizer()
  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer = CommunityMacDistributionCustomizer()

  protected open inner class CommunityWindowsDistributionCustomizer : WindowsDistributionCustomizer() {
    init {
      icoPath = "${communityHomeDir}/build/conf/ideaCE/win/images/idea_CE.ico"
      icoPathForEAP = "${communityHomeDir}/build/conf/ideaCE/win/images/idea_CE_EAP.ico"
      installerImagesPath = "${communityHomeDir}/build/conf/ideaCE/win/images"
      fileAssociations = listOf("java", "gradle", "groovy", "kt", "kts", "pom")
    }

    override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties) = "IntelliJ IDEA Community Edition"

    override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties) = "IntelliJ IDEA Community Edition"

    override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String {
      return "https://www.jetbrains.com/idea/uninstall/?edition=IC-${appInfo.majorVersion}.${appInfo.minorVersion}"
    }
  }

  protected open inner class CommunityLinuxDistributionCustomizer : LinuxDistributionCustomizer() {
    init {
      iconPngPath = "${communityHomeDir}/build/conf/ideaCE/linux/images/icon_CE_128.png"
      iconPngPathForEAP = "${communityHomeDir}/build/conf/ideaCE/linux/images/icon_CE_EAP_128.png"
      snapName = "intellij-idea-community"
      snapDescription =
        "The most intelligent Java IDE. Every aspect of IntelliJ IDEA is specifically designed to maximize developer productivity. " +
        "Together, powerful static code analysis and ergonomic design make development not only productive but also an enjoyable experience."
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String) = "idea-IC-$buildNumber"

    override fun generateExecutableFilesPatterns(context: BuildContext, includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
      return super.generateExecutableFilesPatterns(context, includeRuntime, arch)
        .plus(KotlinBinaries.kotlinCompilerExecutables)
        .filterNot { it == "plugins/**/*.sh" }
    }
  }

  protected open inner class CommunityMacDistributionCustomizer : MacDistributionCustomizer() {
    init {
      icnsPath = "${communityHomeDir}/build/conf/ideaCE/mac/images/idea.icns"
      icnsPathForEAP = "${communityHomeDir}/build/conf/ideaCE/mac/images/communityEAP.icns"
      urlSchemes = listOf("idea")
      associateIpr = true
      fileAssociations = FileAssociation.from("java", "groovy", "kt", "kts")
      bundleIdentifier = "com.jetbrains.intellij.ce"
      dmgImagePath = "${communityHomeDir}/build/conf/ideaCE/mac/images/dmg_background.tiff"
    }

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
      return if (appInfo.isEAP) {
        "IntelliJ IDEA ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} CE EAP.app"
      }
      else {
        "IntelliJ IDEA CE.app"
      }
    }

    override fun generateExecutableFilesPatterns(context: BuildContext, includeRuntime: Boolean, arch: JvmArchitecture): List<String> {
      return super.generateExecutableFilesPatterns(context, includeRuntime, arch).asSequence()
        .plus(KotlinBinaries.kotlinCompilerExecutables)
        .filterNot { it == "plugins/**/*.sh" }
        .toList()
    }
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "IdeaIC${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String) = "ideaIC-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties) = "idea-ce"
}
