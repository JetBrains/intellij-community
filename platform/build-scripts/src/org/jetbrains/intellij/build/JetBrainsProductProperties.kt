// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.jetbrains.plugin.structure.base.plugin.PluginCreationResult
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.InvalidDescriptorProblem
import com.jetbrains.plugin.structure.base.problems.InvalidPluginIDProblem
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.problems.ForbiddenPluginIdPrefix
import com.jetbrains.plugin.structure.intellij.problems.NoDependencies
import com.jetbrains.plugin.structure.intellij.problems.ProhibitedModuleExposed
import com.jetbrains.plugin.structure.intellij.problems.ReleaseDateInFuture
import com.jetbrains.plugin.structure.intellij.problems.ReleaseVersionAndPluginVersionMismatch
import com.jetbrains.plugin.structure.intellij.problems.ServiceExtensionPointPreloadNotSupported
import com.jetbrains.plugin.structure.intellij.problems.TemplateWordInPluginId
import com.jetbrains.plugin.structure.intellij.problems.TemplateWordInPluginName
import com.jetbrains.plugin.structure.intellij.verifiers.DEFAULT_ILLEGAL_PREFIXES
import com.jetbrains.plugin.structure.intellij.verifiers.PRODUCT_ID_RESTRICTED_WORDS
import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.SoftwareBillOfMaterials.Companion.Suppliers
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import java.nio.file.Path
import java.util.function.BiPredicate

/**
 * Describes a distribution of an IntelliJ-based IDE hosted in the IntelliJ repository.
 */
abstract class JetBrainsProductProperties : ProductProperties() {
  init {
    scrambleMainJar = true
    includeIntoSourcesArchiveFilter = BiPredicate(::isCommunityModule)
    sbomOptions.creator = "Organization: ${Suppliers.JETBRAINS}"
    sbomOptions.license = SoftwareBillOfMaterials.Options.DistributionLicense.JETBRAINS

    productLayout.addPlatformSpec { layout, _ -> layout.withModule(IJENT_BOOT_CLASSPATH_MODULE, PLATFORM_CORE_NIO_FS) }
  }

  protected fun isCommunityModule(module: JpsModule, context: BuildContext): Boolean =
    module.contentRootsList.urls.all { url ->
      Path.of(JpsPathUtil.urlToPath(url)).startsWith(context.paths.communityHomeDir)
    }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) { }

  private class InvalidPluginDescriptorError(message: String) : InvalidDescriptorProblem(detailedMessage = message, descriptorPath = "") {
    override val level = Level.ERROR
  }

  final override fun validatePlugin(pluginId: String?, result: PluginCreationResult<IdePlugin>, context: BuildContext): List<PluginProblem> {
    return buildList {
      addAll(super.validatePlugin(pluginId, result, context).filterNot {
        it.isIntentionallyIgnored(pluginId) || it.isApplicableToThirdPartyPluginsOnly()
      })
      if (result is PluginCreationSuccess) {
        if (result.plugin.vendor?.contains("JetBrains") != true) {
          add(InvalidPluginDescriptorError("${result.plugin.pluginId} is published not by JetBrains: ${result.plugin.vendor}"))
        }
      }
    }
  }

  private fun PluginProblem.isIntentionallyIgnored(pluginId: String?): Boolean {
    return when (this) {
      is ServiceExtensionPointPreloadNotSupported ->
        // FIXME IDEA-356970
        pluginId == "com.intellij.plugins.projectFragments" ||
        // FIXME IJPL-169105
        pluginId == "com.jetbrains.codeWithMe" ||
        // FIXME IJPL-159498
        pluginId == "org.jetbrains.plugins.docker.gateway" ||
        // for intellij.build.minimal
        pluginId == "com.intellij.java" || pluginId == "com.intellij.java.ide" ||
        // it's an internal plugin that should be compatible with older IDEA versions as well,
        // so it's ok to have preloading there
        pluginId == "com.intellij.monorepo.devkit"
      is NoDependencies ->
        // FIXME PY-74322
        pluginId == "com.intellij.python.frontend" ||
        // FIXME AE-121
        pluginId == "com.jetbrains.personalization"
      is InvalidPluginIDProblem ->
        // those plugins are already published, the value cannot be changed now
        pluginId == "CFML Support" ||
        pluginId == "Error-prone plugin" ||
        pluginId == "Lombook Plugin"
      /**
       * According to https://plugins.jetbrains.com/docs/marketplace/add-required-parameters.html:
       * > Please make sure the `release-version` and the `version` parameters match.
       * > They should have similar integers at the beginning, like `release-version=20241` and `version=2024.1.1`.
       *
       * For JetBrains own plugins they never match,
       * `release-version` is [ApplicationInfoProperties.releaseVersionForLicensing]
       * and `version` is [BuildContext.pluginBuildNumber] by default
       */
      is ReleaseVersionAndPluginVersionMismatch -> true
      /**
       * The `release-date` attribute for a plugin.xml is pre-filled with an [ApplicationInfoProperties.majorReleaseDate]
       * which may be just an estimation and set in the future.
       * See IJI-673.
       */
      is ReleaseDateInFuture -> true
      else -> false
    }
  }

  private fun PluginProblem.isApplicableToThirdPartyPluginsOnly(): Boolean {
    return when (this) {
      is ForbiddenPluginIdPrefix -> DEFAULT_ILLEGAL_PREFIXES.any {
        message.contains("has a prefix '$it' that is not allowed", ignoreCase = true)
      }
      is TemplateWordInPluginId -> PRODUCT_ID_RESTRICTED_WORDS.any {
        message.contains("should not include the word '$it'", ignoreCase = true)
      }
      /**
       * Copied from [com.jetbrains.plugin.structure.intellij.plugin.PLUGIN_NAME_RESTRICTED_WORDS]
       */
      is TemplateWordInPluginName -> sequenceOf(
        "plugin", "JetBrains", "IDEA", "PyCharm", "CLion", "AppCode", "DataGrip", "Fleet", "GoLand", "PhpStorm",
        "WebStorm", "Rider", "ReSharper", "TeamCity", "YouTrack", "RubyMine", "IntelliJ"
      ).any {
        message.contains("should not include the word '$it'", ignoreCase = true)
      }
      /**
       * Copied from [com.jetbrains.plugin.structure.intellij.verifiers.jetBrainsModuleCommonPrefixes]
       */
      is ProhibitedModuleExposed -> sequenceOf("com.intellij", "org.jetbrains", "intellij").any {
        message.contains("has prefix '$it'", ignoreCase = true)
      }
      else -> false
    }
  }

  /**
   * See KTIJ-30761, `org.jetbrains.intellij.build.sharedIndexes.PreSharedIndexesGenerator`.
   */
  protected fun enableKotlinPluginK2ByDefault() {
    additionalVmOptions += "-Didea.kotlin.plugin.use.k2=true"
  }
}
