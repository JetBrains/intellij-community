// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.BuildContextImpl
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.intellij.build.productLayout.*
import java.nio.file.Path

val MAVEN_ARTIFACTS_ADDITIONAL_MODULES: PersistentList<String> = persistentListOf(
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
) + JewelMavenArtifacts.STANDALONE.keys

internal suspend fun createCommunityBuildContext(
  options: BuildOptions,
  projectHome: Path = COMMUNITY_ROOT.communityRoot,
): BuildContext = BuildContextImpl.createContext(
  projectHome = projectHome,
  productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
  setupTracer = true,
  options = options,
)

open class IdeaCommunityProperties(private val communityHomeDir: Path) : JetBrainsProductProperties() {
  init {
    configurePropertiesForAllEditionsOfIntelliJIdea(this)
    platformPrefix = "Idea"
    applicationInfoModule = "intellij.idea.community.customization"
    scrambleMainJar = false
    useSplash = true
    buildCrossPlatformDistribution = true
    buildSourcesArchive = true

    productLayout.productImplementationModules = listOf(
      "intellij.platform.starter",
      "intellij.idea.community.customization",
    )
    productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS + sequenceOf(
      "intellij.javaFX.community"
    )

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = true
    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS + persistentListOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.androidPlugin(allPlatforms = true),
      CommunityRepositoryModules.groovyPlugin(),
    )

    productLayout.skipUnresolvedContentModules = true

    mavenArtifacts.forIdeModules = true
    mavenArtifacts.additionalModules += MAVEN_ARTIFACTS_ADDITIONAL_MODULES
    mavenArtifacts.squashedModules += persistentListOf(
      "intellij.platform.util.base",
      "intellij.platform.util.base.multiplatform",
      "intellij.platform.util.zip",
    )
    mavenArtifacts.validateForMavenCentralPublication = { module ->
      JewelMavenArtifacts.isPublishedJewelModule(module)
    }
    mavenArtifacts.patchCoordinates = { module, coordinates ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.patchCoordinates(module, coordinates)
        else -> coordinates
      }
    }
    mavenArtifacts.patchDependencies = { module, dependencies ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.patchDependencies(module, dependencies)
        else -> dependencies
      }
    }
    mavenArtifacts.addPomMetadata = { module, model ->
      when {
        JewelMavenArtifacts.isPublishedJewelModule(module) -> JewelMavenArtifacts.addPomMetadata(module, model)
      }
    }
    mavenArtifacts.isJavadocJarRequired = {
      JewelMavenArtifacts.isPublishedJewelModule(it) && it.name != "intellij.platform.jewel.intUi.decoratedWindow"
    }
    mavenArtifacts.validate = { context, artifacts ->
      JewelMavenArtifacts.validate(context, artifacts)
    }

    versionCheckerConfig = CE_CLASS_VERSIONS
    baseDownloadUrl = "https://download.jetbrains.com/idea/"
    buildDocAuthoringAssets = true

    @Suppress("SpellCheckingInspection")
    qodanaProductProperties = QodanaProductProperties("QDJVMC", "Qodana Community for JVM")
    additionalVmOptions = persistentListOf("-Dllm.show.ai.promotion.window.on.start=false")
  }

  override val moduleSetsProviders: List<ModuleSetProvider>
    get() = listOf(CommunityModuleSets)

  override val baseFileName: String
    get() = "idea"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment())
    include(communityExtensionsFragment())
  }

  override suspend fun copyAdditionalFiles(targetDir: Path, context: BuildContext) {
    super.copyAdditionalFiles(targetDir, context)

    copyFileToDir(context.paths.communityHomeDir.resolve("LICENSE.txt"), targetDir)
    copyFileToDir(context.paths.communityHomeDir.resolve("NOTICE.txt"), targetDir)

    copyDir(
      sourceDir = context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"),
      targetDir = targetDir.resolve("bin"),
    )

    bundleExternalPlugins(context, targetDir)
  }

  protected open suspend fun bundleExternalPlugins(context: BuildContext, targetDirectory: Path) {}

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer = CommunityWindowsDistributionCustomizer()

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer = CommunityLinuxDistributionCustomizer()

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer = CommunityMacDistributionCustomizer()

  protected open inner class CommunityWindowsDistributionCustomizer : WindowsDistributionCustomizer() {
    init {
      icoPath = communityHomeDir.resolve("build/conf/ideaCE/win/images/idea_CE.ico")
      icoPathForEAP = communityHomeDir.resolve("build/conf/ideaCE/win/images/idea_CE_EAP.ico")
      installerImagesPath = communityHomeDir.resolve("build/conf/ideaCE/win/images")
    }

    override val fileAssociations: List<String>
      get() = listOf("java", "gradle", "groovy", "kt", "kts", "pom")

    override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties): String = "IntelliJ IDEA Community Edition"

    override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties): String = "IntelliJ IDEA Community Edition"

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

    override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "idea-IC-$buildNumber"

    override fun generateExecutableFilesPatterns(
      includeRuntime: Boolean,
      arch: JvmArchitecture,
      targetLibcImpl: LibcImpl,
      context: BuildContext,
    ): Sequence<String> {
      return super.generateExecutableFilesPatterns(includeRuntime, arch, targetLibcImpl, context)
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
      if (appInfo.isEAP) {
        return "IntelliJ IDEA ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} CE EAP.app"
      }
      else {
        return "IntelliJ IDEA CE.app"
      }
    }

    override fun generateExecutableFilesPatterns(includeRuntime: Boolean, arch: JvmArchitecture, context: BuildContext): Sequence<String> {
      return super.generateExecutableFilesPatterns(includeRuntime, arch, context)
        .plus(KotlinBinaries.kotlinCompilerExecutables)
        .filterNot { it == "plugins/**/*.sh" }
    }
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "IdeaIC${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "ideaIC-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "idea-ce"
}

/**
 * Base IntelliJ Community content fragment.
 * This fragment is composable - subclasses can include this and optionally add community extensions.
 */
fun intellijCommunityBaseFragment(): ProductModulesContentSpec = productModules {
  alias("com.intellij.modules.idea")
  alias("com.intellij.modules.idea.community")
  alias("com.intellij.modules.java-capable")
  alias("com.intellij.modules.python-core-capable")
  alias("com.intellij.modules.python-in-non-pycharm-ide-capable")
  alias("com.intellij.platform.ide.provisioner")

  include(CommunityProductFragments.javaIdeBaseFragment())
  deprecatedInclude("intellij.idea.community.customization", "META-INF/tips-intellij-idea-community.xml")

  moduleSet(CommunityModuleSets.debuggerStreams())

  module("intellij.platform.coverage")
  module("intellij.platform.coverage.agent")
  module("intellij.xml.xmlbeans")
  module("intellij.platform.ide.newUiOnboarding")
  module("intellij.platform.ide.newUsersOnboarding")
  module("intellij.ide.startup.importSettings")
  module("intellij.platform.customization.min")
  module("intellij.idea.customization.base")
  module("intellij.idea.customization.backend")
  module("intellij.platform.tips")

  moduleSet(CommunityModuleSets.ideCommon())
  moduleSet(CommunityModuleSets.rdCommon())

  deprecatedInclude("intellij.idea.community.customization", "META-INF/community-customization.xml")
}

/**
 * Community extensions fragment for Ultimate builds.
 * This fragment is composable - subclasses can choose to include or exclude it.
 */
fun communityExtensionsFragment(): ProductModulesContentSpec = productModules {
  deprecatedInclude("intellij.platform.extended.community.impl", "META-INF/community-extensions.xml", ultimateOnly = true)
}