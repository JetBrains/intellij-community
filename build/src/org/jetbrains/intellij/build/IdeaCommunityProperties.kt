// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.impl.BaseLayout
import org.jetbrains.intellij.build.impl.BuildContextImpl

import java.nio.file.Path

internal fun createCommunityBuildContext(
  communityHome: BuildDependenciesCommunityRoot,
  options: BuildOptions = BuildOptions(),
  projectHome: Path = communityHome.communityRoot,
): BuildContext {
  return BuildContextImpl.createContextBlocking(communityHome = communityHome,
                                                projectHome = projectHome,
                                                productProperties = IdeaCommunityProperties(communityHome.communityRoot),
                                                options = options)
}

open class IdeaCommunityProperties(private val communityHomeDir: Path) : BaseIdeaProperties() {
  companion object {
    val MAVEN_ARTIFACTS_ADDITIONAL_MODULES = persistentListOf(
      "intellij.tools.jps.build.standalone",
      "intellij.platform.debugger.testFramework",
      "intellij.platform.vcs.testFramework",
      "intellij.platform.externalSystem.testFramework",
      "intellij.maven.testFramework"
    )
  }

  override val baseFileName: String
    get() = "idea"

  init {
    platformPrefix = "Idea"
    applicationInfoModule = "intellij.idea.community.resources"
    additionalIDEPropertiesFilePaths = persistentListOf(communityHomeDir.resolve("build/conf/ideaCE.properties"))
    toolsJarRequired = true
    scrambleMainJar = false
    useSplash = true
    buildCrossPlatformDistribution = true

    productLayout.productImplementationModules = listOf("intellij.platform.main")
    productLayout.withAdditionalPlatformJar(BaseLayout.APP_JAR, "intellij.idea.community.resources")
    productLayout.bundledPluginModules = IDEA_BUNDLED_PLUGINS
      .add("intellij.javaFX.community")
      .toMutableList()

    productLayout.prepareCustomPluginRepositoryForPublishedPlugins = false
    productLayout.buildAllCompatiblePlugins = false
    productLayout.pluginLayouts = CommunityRepositoryModules.COMMUNITY_REPOSITORY_PLUGINS.addAll(listOf(
      JavaPluginLayout.javaPlugin(),
      CommunityRepositoryModules.androidPlugin(emptyMap()),
      CommunityRepositoryModules.groovyPlugin()
    ))

    productLayout.addPlatformCustomizer { layout, _ ->
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
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDirectory: String) {
    super.copyAdditionalFiles(context, targetDirectory)
    FileSet(context.paths.communityHomeDir)
      .include("LICENSE.txt")
      .include("NOTICE.txt")
      .copyToDir(Path.of(targetDirectory))
     FileSet(context.paths.communityHomeDir.resolve("build/conf/ideaCE/common/bin"))
      .includeAll()
      .copyToDir(Path.of(targetDirectory, "bin"))
    bundleExternalPlugins(context, targetDirectory)
  }

  protected open fun bundleExternalPlugins(context: BuildContext, targetDirectory: String) {
    //temporary unbundle VulnerabilitySearch
    //ExternalPluginBundler.bundle('VulnerabilitySearch',
    //                             "$buildContext.paths.communityHome/build/dependencies",
    //                             buildContext, targetDirectory)
  }

  override fun createWindowsCustomizer(projectHome: String): WindowsDistributionCustomizer {
    return object : WindowsDistributionCustomizer() {
      init {
        icoPath = "${communityHomeDir}/platform/icons/src/idea_CE.ico"
        icoPathForEAP = "${communityHomeDir}/build/conf/ideaCE/win/images/idea_CE_EAP.ico"
        installerImagesPath = "${communityHomeDir}/build/conf/ideaCE/win/images"
        fileAssociations = listOf("java", "groovy", "kt", "kts")
      }

      override fun getFullNameIncludingEdition(appInfo: ApplicationInfoProperties) = "IntelliJ IDEA Community Edition"

      override fun getFullNameIncludingEditionAndVendor(appInfo: ApplicationInfoProperties) = "IntelliJ IDEA Community Edition"

      override fun getUninstallFeedbackPageUrl(appInfo: ApplicationInfoProperties): String {
        return "https://www.jetbrains.com/idea/uninstall/?edition=IC-${appInfo.majorVersion}.${appInfo.minorVersion}"
      }
    }
  }

  override fun createLinuxCustomizer(projectHome: String): LinuxDistributionCustomizer {
    return object : LinuxDistributionCustomizer() {
      init {
        iconPngPath = "${communityHomeDir}/build/conf/ideaCE/linux/images/icon_CE_128.png"
        iconPngPathForEAP = "${communityHomeDir}/build/conf/ideaCE/linux/images/icon_CE_EAP_128.png"
        snapName = "intellij-idea-community"
        snapDescription =
          "The most intelligent Java IDE. Every aspect of IntelliJ IDEA is specifically designed to maximize developer productivity. " +
          "Together, powerful static code analysis and ergonomic design make development not only productive but also an enjoyable experience."
        extraExecutables = persistentListOf(
          "plugins/Kotlin/kotlinc/bin/kotlin",
          "plugins/Kotlin/kotlinc/bin/kotlinc",
          "plugins/Kotlin/kotlinc/bin/kotlinc-js",
          "plugins/Kotlin/kotlinc/bin/kotlinc-jvm",
          "plugins/Kotlin/kotlinc/bin/kotlin-dce-js"
        )
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String) = "idea-IC-$buildNumber"
    }
  }

  override fun createMacCustomizer(projectHome: String): MacDistributionCustomizer {
    return object : MacDistributionCustomizer() {
      init {
        icnsPath = "${communityHomeDir}/build/conf/ideaCE/mac/images/idea.icns"
        urlSchemes = listOf("idea")
        associateIpr = true
        fileAssociations = FileAssociation.from("java", "groovy", "kt", "kts")
        bundleIdentifier = "com.jetbrains.intellij.ce"
        dmgImagePath = "${communityHomeDir}/build/conf/ideaCE/mac/images/dmg_background.tiff"
        icnsPathForEAP = "${communityHomeDir}/build/conf/ideaCE/mac/images/communityEAP.icns"
      }

      override fun getRootDirectoryName(appInfo: ApplicationInfoProperties, buildNumber: String): String {
        return if (appInfo.isEAP) {
          "IntelliJ IDEA ${appInfo.majorVersion}.${appInfo.minorVersionMainPart} CE EAP.app"
        }
        else {
          "IntelliJ IDEA CE.app"
        }
      }
    }
  }

  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String {
    return "IdeaIC${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String) =  "ideaIC-$buildNumber"

  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties) = "idea-ce"
}