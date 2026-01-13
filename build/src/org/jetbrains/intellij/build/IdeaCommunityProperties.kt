// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.createBuildContext
import org.jetbrains.intellij.build.impl.qodana.QodanaProductProperties
import org.jetbrains.intellij.build.io.copyDir
import org.jetbrains.intellij.build.io.copyFileToDir
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.CommunityProductFragments
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
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
): BuildContext {
  return createBuildContext(
    projectHome = projectHome,
    productProperties = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot),
    setupTracer = true,
    options = options,
  )
}

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

  override val baseFileName: String
    get() = "idea"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment(platformPrefix))
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

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer = communityWindowsCustomizer(communityHomeDir)

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer = communityLinuxCustomizer(communityHomeDir)

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer = communityMacCustomizer(communityHomeDir)

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "IdeaIC${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "ideaIC-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "idea-ce"
}

@Suppress("unused")
open class AndroidStudioProperties(communityHomeDir: Path) : IdeaCommunityProperties(communityHomeDir) {
  init {
    platformPrefix = "AndroidStudio"
    applicationInfoModule = "intellij.idea.android.customization"

    productLayout.productImplementationModules += "intellij.idea.android.customization"

    val defaultBundledPlugins = IDEA_BUNDLED_PLUGINS
      .remove("intellij.mcpserver")
      .remove("intellij.featuresTrainer")

    productLayout.bundledPluginModules = defaultBundledPlugins + persistentListOf(
      "intellij.android.compose-ide-plugin",
      "intellij.android.design-plugin.descriptor",
      "intellij.android.plugin.descriptor",
      "intellij.android.smali",
    )
  }

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    include(intellijCommunityBaseFragment(platformPrefix))
    // no community extensions
  }
}

/**
 * Base IntelliJ Community content fragment.
 * This fragment is composable - subclasses can include this and optionally add community extensions.
 */
fun intellijCommunityBaseFragment(platformPrefix: String? = null): ProductModulesContentSpec = productModules {
  if (platformPrefix == "AndroidStudio") {
    alias("com.intellij.modules.androidstudio")
  }
  else {
    alias("com.intellij.modules.idea")
    alias("com.intellij.modules.idea.community")
  }

  alias("com.intellij.modules.java-capable")
  alias("com.intellij.modules.python-core-capable")
  alias("com.intellij.modules.python-in-non-pycharm-ide-capable")

  if (platformPrefix != "AndroidStudio") {
    alias("com.intellij.platform.ide.provisioner")
    alias("com.intellij.modules.jcef")
  }

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

  if (System.getProperty("idea.platform.prefix") == "AndroidStudio") {
    module("intellij.idea.android.customization")
  }

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