// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.core.CoreBundle
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.PluginInitializationContext.EnvironmentConfiguredModuleData
import com.intellij.ide.plugins.PluginManagerCore.CORE_ID
import com.intellij.ide.plugins.PluginManagerCore.JAVA_PLUGIN_ALIAS_ID
import com.intellij.ide.plugins.PluginManagerCore.getPluginNameAndVendor
import com.intellij.ide.plugins.PluginManagerCore.logger
import com.intellij.ide.plugins.ProductRulesImposedExclusion.ProductRulesImposedExclusionReason
import com.intellij.idea.AppMode
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.GraphicsEnvironment
import javax.swing.JOptionPane

/**
 * TODO: in the end, the way PluginInitContext is supposed to be used is the following:
 *   * an instance of PluginInitContext is immutable and it provides a total configuration against which the plugin subsystem state can be definitively computed
 *   * plugin subsystem state is a function of (initContext, a set of available plugin descriptors)
 *   * in order to change the plugin subsystem state, one of the inputs needs to change, i.e., any change in the settings (be it disabled plugins set change,
 *     or licensing subsystem detects that license has expired and the ultimate module needs to be unloaded) generates a new instance of PluginInitContext
 *   * Dynamic plugin subsystem reconfiguration procedure takes in the current state of the plugin subsystem, the new expected one, determines
 *     if a dynamic transition is possible without a restart and performs it
 *
 *     Right now an instance of ProductPluginInitContext is not immutable and it is instantiated in quite a few places
 */
@VisibleForTesting
@ApiStatus.Internal
class ProductPluginInitContext(
  private val buildNumberOverride: BuildNumber? = null,
  private val disabledPluginsOverride: Set<PluginId>? = null,
  private val expiredPluginsOverride: Set<PluginId>? = null,
  private val brokenPluginVersionsOverride: Map<PluginId, Set<String>>? = null,
) : PluginInitializationContext {
  override val essentialPlugins: Set<PluginId> by lazy {
    buildSet {
      add(CORE_ID)
      addAll(ApplicationInfoImpl.getShadowInstance().getEssentialPluginIds())
      if (AppMode.isRemoteDevHost()) {
        add(REMOTE_DEVELOPMENT_PLUGIN_ID)
      }
    }
  }
  private val disabledPlugins: Set<PluginId> get() = disabledPluginsOverride ?: DisabledPluginsState.getDisabledIds()
  private val expiredPlugins: Set<PluginId> get() = expiredPluginsOverride ?: ExpiredPluginsState.expiredPluginIds
  private val brokenPluginVersions: Map<PluginId, Set<String>> get() = brokenPluginVersionsOverride ?: getBrokenPluginVersions()

  override val productBuildNumber: BuildNumber
    get() = buildNumberOverride ?: PluginManagerCore.buildNumber

  override fun isPluginDisabled(id: PluginId): Boolean {
    return CORE_ID != id && disabledPlugins.contains(id)
  }

  override fun isPluginBroken(id: PluginId, version: String?): Boolean {
    val set = brokenPluginVersions[id] ?: return false
    return set.contains(version)
  }

  override val requirePlatformAliasDependencyForLegacyPlugins: Boolean
    get() = !PlatformUtils.isIntelliJ()

  override val checkEssentialPlugins: Boolean
    get() = !PluginManagerCore.isUnitTestMode

  override val explicitPluginSubsetToLoad: Set<PluginId>? by lazy {
    System.getProperty("idea.load.plugins.id")
      ?.splitToSequence(',')
      ?.filter { it.isNotEmpty() }
      ?.map(PluginId::getId)
      ?.toHashSet()
  }

  override val disablePluginLoadingCompletely: Boolean
    get() = !System.getProperty("idea.load.plugins", "true").toBoolean()

  override val pluginsPerProjectConfig: PluginsPerProjectConfig? by lazy {
    if (java.lang.Boolean.getBoolean("ide.per.project.instance")) {
      PluginsPerProjectConfig(
        isMainProcess = !PathManager.getPluginsDir().fileName.toString().startsWith("perProject_")
      )
    }
    else null
  }

  override val currentProductModeId: String
    get() = ProductLoadingStrategy.strategy.currentModeId

  override val environmentConfiguredModules: Map<PluginModuleId, EnvironmentConfiguredModuleData> by lazy {
    buildMap {
      configureProductModeModules(currentProductModeId)
    }
  }

  override fun provideCompatibilityDependencies(descriptor: IdeaPluginDescriptorImpl, pluginSet: UnambiguousPluginSet): Sequence<DependencyRef> =
    defaultProductCompatibilityDependenciesProvider(descriptor, pluginSet)

  override fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusionReason>> =
    defaultProductRulesImposedExclusions(pluginSet, expiredPlugins, thirdPartyPluginsWithoutConsentCheckResult)

  override fun provideCustomRuntimeModuleGroupAffiliation(module: PluginModuleDescriptor, pluginSet: UnambiguousPluginSet): PluginModuleDescriptor? =
    defaultRuntimeModuleGroupAffiliation(module, pluginSet)

  override fun shouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget: PluginMainDescriptor): Boolean =
    defaultShouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget)

  override fun runConfigurationDuringStartup(totalPluginSet: AmbiguousPluginSet) {
    thirdPartyPluginsWithoutConsentCheckResult = checkThirdPartyPluginsPrivacyConsent(totalPluginSet)
    thirdPartyPluginsWithoutConsentCheckResult?.let { result ->
      if (result.privacyNoteAccepted != null) {
        ThirdPartyPluginsPrivacyConsentState.setState(result.privacyNoteAccepted)
      }
    }
  }

  data class ThirdPartyPluginsWithoutConsentCheckResult(
    /** null if wasn't asked */
    val privacyNoteAccepted: Boolean?,
    val pluginsToExcludeFromLoading: List<PluginMainDescriptor>
  )

  private var thirdPartyPluginsWithoutConsentCheckResult: ThirdPartyPluginsWithoutConsentCheckResult? = null

  /**
   * Processes postponed consent check from the previous run (e.g., when the previous run was headless)
   * see usages of [ThirdPartyPluginsWithoutConsentFile.appendAliens].
   *
   * Invoked only during startup initialization.
   */
  private fun checkThirdPartyPluginsPrivacyConsent(pluginSet: AmbiguousPluginSet): ThirdPartyPluginsWithoutConsentCheckResult? {
    val aliens = ThirdPartyPluginsWithoutConsentFile.consumeAliensFile().mapNotNull { pluginSet.resolvePluginId(it).firstOrNull()?.getMainDescriptor() }
    if (aliens.isEmpty()) {
      return null
    }
    return checkThirdPartyPluginsPrivacyConsent(aliens)
  }

  /** This method mutates [DisabledPluginsState]! */
  private fun checkThirdPartyPluginsPrivacyConsent(aliens: List<PluginMainDescriptor>): ThirdPartyPluginsWithoutConsentCheckResult {
    if (GraphicsEnvironment.isHeadless()) {
      if (QODANA_PLUGINS_THIRD_PARTY_ACCEPT || FLEET_BACKEND_PLUGINS_THIRD_PARTY_ACCEPT) {
        return ThirdPartyPluginsWithoutConsentCheckResult(true, emptyList())
      }
      logger.info("3rd-party plugin privacy note not accepted yet; disabling plugins for this headless session")
      //write the list of third-party plugins back to ensure that the privacy note will be shown next time
      ThirdPartyPluginsWithoutConsentFile.appendAliens(aliens.map { it.pluginId })
      return ThirdPartyPluginsWithoutConsentCheckResult(null, aliens)
    }
    else if (AppMode.isRemoteDevHost()) {
      logger.warn("""
        |New third-party plugins were installed, they will be disabled because asking for consent to use third-party plugins during startup isn't supported in remote development mode:
        | ${aliens.joinToString(separator = "\n ") { it.name }} 
        |Use '--give-consent-to-use-third-party-plugins' option in 'installPlugins' option to approve installed third-party plugins automatically.
        |""".trimMargin())
      PluginEnabler.HEADLESS.disable(aliens)
      return ThirdPartyPluginsWithoutConsentCheckResult(null, aliens)
    }
    else if (!askThirdPartyPluginsPrivacyConsent(aliens)) {
      logger.info("3rd-party plugin privacy note declined; disabling plugins")
      PluginEnabler.HEADLESS.disable(aliens)
      return ThirdPartyPluginsWithoutConsentCheckResult(false, aliens)
    }
    else {
      return ThirdPartyPluginsWithoutConsentCheckResult(true, emptyList())
    }
  }

  private fun askThirdPartyPluginsPrivacyConsent(descriptors: List<IdeaPluginDescriptorImpl>): Boolean {
    val title = CoreBundle.message("third.party.plugins.privacy.note.title")
    val pluginList = descriptors.joinToString(separator = "<br>") { "&nbsp;&nbsp;&nbsp;${getPluginNameAndVendor(it)}" }
    val text = CoreBundle.message("third.party.plugins.privacy.note.text", pluginList, ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    val buttons = arrayOf(CoreBundle.message("third.party.plugins.privacy.note.accept"), CoreBundle.message("third.party.plugins.privacy.note.disable"))
    val icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.WarningDialog)
    val choice = JOptionPane.showOptionDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, icon, buttons, buttons.get(0))
    return choice == 0
  }

  companion object {
    @VisibleForTesting
    fun MutableMap<PluginModuleId, EnvironmentConfiguredModuleData>.configureProductModeModules(productModeId: String) {
      val frontendSplit = PluginModuleId("intellij.platform.frontend.split", PluginModuleId.JETBRAINS_NAMESPACE)
      val frontendSplitBase = PluginModuleId("intellij.platform.frontend.split.base", PluginModuleId.JETBRAINS_NAMESPACE)
      val platformSplit = PluginModuleId("intellij.platform.split", PluginModuleId.JETBRAINS_NAMESPACE)
      val platformSplitConnection = PluginModuleId("intellij.platform.split.connection", PluginModuleId.JETBRAINS_NAMESPACE)
      val rdClient = PluginModuleId("intellij.rd.client", PluginModuleId.JETBRAINS_NAMESPACE)
      val frontend = PluginModuleId("intellij.platform.frontend", PluginModuleId.JETBRAINS_NAMESPACE)
      val backend = PluginModuleId("intellij.platform.backend", PluginModuleId.JETBRAINS_NAMESPACE)
      val backendJps = PluginModuleId("intellij.platform.jps.build", PluginModuleId.JETBRAINS_NAMESPACE)
      val backendJpsGraph = PluginModuleId("intellij.platform.jps.build.dependencyGraph", PluginModuleId.JETBRAINS_NAMESPACE)

      for (moduleId in listOf(frontend, backend, frontendSplit, backendJps, backendJpsGraph, rdClient, platformSplit)) {
        val isAvailable = when (productModeId) {
          /** intellij.platform.backend.split is currently available in 'monolith' mode because it's used as a backend in CodeWithMe */
          "monolith" -> moduleId != frontendSplit && moduleId != frontendSplitBase
          "backend" -> moduleId != frontend && moduleId != frontendSplit && moduleId != frontendSplitBase
          "frontend" -> moduleId != backend && moduleId != backendJps && moduleId != backendJpsGraph
          "light" -> moduleId != backend && moduleId != frontendSplit && moduleId != backendJps && moduleId != backendJpsGraph
                     && moduleId != rdClient && moduleId != platformSplit && moduleId != platformSplitConnection
          "light_with_rd_connection" -> moduleId != backend && moduleId != frontendSplit && moduleId != backendJps && moduleId != backendJpsGraph
                                        && moduleId != rdClient && moduleId != platformSplit
          else -> true
        }
        val unavailabilityReason =
          if (isAvailable) null
          else UnsuitableProductModeModuleUnavailabilityReason(moduleId, productModeId)
        val replaced = put(moduleId, EnvironmentConfiguredModuleData(unavailabilityReason))
        check(replaced == null) { "${moduleId.displayName} is already registered as environment-configured module" }
      }
    }

    @VisibleForTesting
    fun defaultProductCompatibilityDependenciesProvider(descriptor: IdeaPluginDescriptorImpl, pluginSet: UnambiguousPluginSet): Sequence<DependencyRef> {
      suspend fun SequenceScope<DependencyRef>.yieldIfResolves(ref: DependencyRef) {
        if (pluginSet.resolveReference(ref) != null) {
          yield(ref)
        }
      }
      suspend fun SequenceScope<DependencyRef>.yieldPlatformAliasCompatibilityDependencies() {
        for (contentModuleId in contentModulesExtractedInCorePluginWhichCanBeUsedFromExternalPlugins) {
          yieldIfResolves(DependencyRef.of(contentModuleId))
        }
      }
      return sequence {
        if (descriptor.pluginId != CORE_ID) {
          yieldIfResolves(DependencyRef.of(CORE_ID))
        }
        if (descriptor is PluginModuleDescriptor && descriptor.pluginId != CORE_ID && isExternalNonBundledPlugin(descriptor)) {
          for (dependencyRef in externalNonBundledPluginCompatibilityDependencies) {
            yieldIfResolves(dependencyRef)
          }
        }

        // If a plugin does not include any module dependency tags in its plugin.xml, it's assumed to be a legacy plugin
        // and is loaded only in IntelliJ IDEA, so it may use classes from Java plugin.
        if (descriptor is PluginMainDescriptor &&
            pluginSet.resolvePluginId(PluginManagerCore.ALL_MODULES_MARKER) != null &&
            PluginCompatibilityUtils.isLegacyPluginWithoutPlatformAliasDependencies(descriptor)) {
          val java = pluginSet.resolvePluginId(JAVA_PLUGIN_ALIAS_ID)
          if (java != null && java !== descriptor) {
            yield(DependencyRef.of(JAVA_PLUGIN_ALIAS_ID))
            yieldIfResolves(DependencyRef.of(JAVA_BACKEND_MODULE_ID))
          }
        }

        if (descriptor.pluginId == CORE_ID && descriptor is ContentModuleDescriptor) {
          yieldIfResolves(DependencyRef.of(CORE_ID)) // all content modules of CORE are expected to be registered after its main module
        }

        // Check modules as well, for example, intellij.diagram.impl.vcs.
        // We are not yet ready to recommend adding a dependency on extracted VCS modules since the coordinates are not finalized.
        if ((descriptor is PluginMainDescriptor && descriptor.pluginId != CORE_ID) || descriptor is ContentModuleDescriptor) {
          val isExternalNonBundledDescriptor = isExternalNonBundledPlugin(descriptor)
          if (isExternalNonBundledDescriptor || doesDependOnPluginAlias(descriptor, VCS_ALIAS_ID)) {
            vcsApiContentModules.forEach { vcsModule ->
              yieldIfResolves(DependencyRef.of(vcsModule))
            }
          }
          if (isExternalNonBundledDescriptor) {
            if (System.getProperty("enable.implicit.json.dependency").toBoolean()) {
              yieldIfResolves(DependencyRef.of(JSON_ALIAS_ID))
              yieldIfResolves(DependencyRef.of(JSON_BACKEND_MODULE_ID))
            }
            if (doesDependOnPluginAlias(descriptor, JSON_ALIAS_ID)) {
              yieldIfResolves(DependencyRef.of(JSON_BACKEND_MODULE_ID))
            }
            if (doesDependOnPluginAlias(descriptor, CWM_PLUGIN_ID)) {
              yieldIfResolves(DependencyRef.of(REMOTE_DEVELOPMENT_MODULE_ID))
            }
            if (doesDependOnPluginAlias(descriptor, CWM_RIDER_PLUGIN_ID)) {
              yieldIfResolves(DependencyRef.of(REMOTE_DEVELOPMENT_RIDER_MODULE_ID))
            }
            if (doesDependOnPluginAlias(descriptor, XDEBUGGER_PLUGIN_ALIAS_ID)) {
              for (moduleId in XDEBUGGER_MODULE_IDS) {
                yieldIfResolves(DependencyRef.of(moduleId))
              }
            }
            if (doesDependOnPluginAlias(descriptor, GIT4IDEA_PLUGIN_ALIAS_ID)) {
              for (moduleId in GIT4IDEA_MODULE_IDS) {
                yieldIfResolves(DependencyRef.of(moduleId))
              }
            }
            yieldIfResolves(DependencyRef.of(COLLABORATION_TOOLS_MODULE_ID))
          }

          /* Compatibility Layer */

          if (doesDependOnPluginAlias(descriptor, JAVA_PLUGIN_ALIAS_ID)) {
            yieldIfResolves(DependencyRef.of(JAVA_BACKEND_MODULE_ID))
          }

          if (doesDependOnPluginAlias(descriptor, RIDER_ALIAS_ID)) {
            yieldIfResolves(DependencyRef.of(RIDER_MODULE_ID))
          }
          if (doesDependOnPluginAlias(descriptor, PluginId.getId("org.jetbrains.completion.full.line"))) {
            fullLineApiContentModules.forEach { fullLineModule ->
              yieldIfResolves(DependencyRef.of(fullLineModule))
            }
          }
        }

        if (descriptor !is PluginMainDescriptor || descriptor.pluginId != CORE_ID) { // FIXME violator: DesignedCorePlugin.xml which is xi:included from IdeaPlugin.xml
          for (depends in descriptor.pluginDependencies) {
            if (depends.subDescriptor != null) { // will be processed when invoked for the sub-descriptor
              continue
            }
            if ((depends.pluginId == PLATFORM_PLUGIN_ALIAS_ID || depends.pluginId == LANG_PLUGIN_ALIAS_ID) && pluginSet.resolvePluginId(depends.pluginId) != null) {
              yieldPlatformAliasCompatibilityDependencies()
            }
          }
        }

        if (descriptor is DependsSubDescriptor) {
          if ((descriptor.dependsTargetId == PLATFORM_PLUGIN_ALIAS_ID || descriptor.dependsTargetId == LANG_PLUGIN_ALIAS_ID) && pluginSet.resolvePluginId(descriptor.pluginId) != null) {
            yieldPlatformAliasCompatibilityDependencies()
          }
        }
      }
    }

    @VisibleForTesting
    fun defaultRuntimeModuleGroupAffiliation(module: PluginModuleDescriptor, pluginSet: UnambiguousPluginSet): PluginModuleDescriptor? {
      if (module is ContentModuleDescriptor && module.moduleId.name == "intellij.platform.backend") {
        return module.parent // FIXME this should not exist IJPL-201428
      }
      return null
    }

    @VisibleForTesting
    fun defaultProductRulesImposedExclusions(
      pluginSet: UnambiguousPluginSet,
      expiredPlugins: Set<PluginId>,
      thirdPartyPluginsWithoutConsentCheckResult: ThirdPartyPluginsWithoutConsentCheckResult?,
    ): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusionReason>> {
      return sequence {
        for (expiredPluginId in expiredPlugins) {
          val plugin = pluginSet.resolvePluginId(expiredPluginId)
                       ?: continue
          yield(plugin to PluginHasExpiredLicense())
        }
        thirdPartyPluginsWithoutConsentCheckResult?.let {
          for (plugin in it.pluginsToExcludeFromLoading) {
            yield(plugin to ThirdPartyPrivacyNoticeIsNotAccepted())
          }
        }
      }
    }

    @VisibleForTesting
    fun defaultShouldIncludeContentModulesForDependsEdgeTarget(target: PluginMainDescriptor): Boolean {
      return target.pluginId != CORE_ID // `com.intellij` is handled by compatibility dependencies provider
    }
  }
}

@ApiStatus.Internal
sealed interface IntellijImposedModuleExclusionReason : ProductRulesImposedExclusionReason

@ApiStatus.Internal
class PluginHasExpiredLicense : IntellijImposedModuleExclusionReason

@ApiStatus.Internal
class ThirdPartyPrivacyNoticeIsNotAccepted : IntellijImposedModuleExclusionReason

// alias in most cases points to Core plugin, so we cannot use computed dependencies to check
private fun doesDependOnPluginAlias(plugin: IdeaPluginDescriptorImpl, @Suppress("SameParameterValue") aliasId: PluginId): Boolean {
  return plugin.dependencies.any { it.pluginId == aliasId } || plugin.moduleDependencies.plugins.any { it == aliasId }
}

private fun isExternalNonBundledPlugin(plugin: IdeaPluginDescriptorImpl): Boolean {
  return !plugin.isBundled && !PluginManagerCore.isVendorJetBrains(plugin.vendor ?: "") ||
         plugin.pluginId.idString == "com.intellij.monorepo.devkit"
}

private val JAVA_BACKEND_MODULE_ID = PluginModuleId("intellij.java.backend", PluginModuleId.JETBRAINS_NAMESPACE)
private val VCS_ALIAS_ID = PluginId.getId("com.intellij.modules.vcs")
private val RIDER_ALIAS_ID = PluginId.getId("com.intellij.modules.rider")
private val RIDER_MODULE_ID = PluginModuleId("intellij.rider", PluginModuleId.JETBRAINS_NAMESPACE)
private val JSON_ALIAS_ID = PluginId.getId("com.intellij.modules.json")
private val CWM_PLUGIN_ID = PluginId.getId("com.jetbrains.codeWithMe")
private val CWM_RIDER_PLUGIN_ID = PluginId.getId("intellij.rider.plugins.cwm")
private val REMOTE_DEVELOPMENT_PLUGIN_ID: PluginId = PluginId.getId("com.jetbrains.remoteDevelopment")
private val REMOTE_DEVELOPMENT_RIDER_PLUGIN_ID: PluginId = PluginId.getId("intellij.rider.plugins.remoteDevelopment")
private val JSON_BACKEND_MODULE_ID = PluginModuleId("intellij.json.backend", PluginModuleId.JETBRAINS_NAMESPACE)
private val REMOTE_DEVELOPMENT_MODULE_ID = PluginModuleId("intellij.cwm", PluginModuleId.JETBRAINS_NAMESPACE)
private val REMOTE_DEVELOPMENT_RIDER_MODULE_ID = PluginModuleId("intellij.rider.plugins.cwm", PluginModuleId.JETBRAINS_NAMESPACE)
private val PLATFORM_PLUGIN_ALIAS_ID = PluginId.getId("com.intellij.modules.platform")
private val LANG_PLUGIN_ALIAS_ID = PluginId.getId("com.intellij.modules.lang")
private val XDEBUGGER_PLUGIN_ALIAS_ID = PluginId.getId("com.intellij.modules.xdebugger")
private val XDEBUGGER_MODULE_IDS = listOf(
  PluginModuleId("intellij.platform.debugger", PluginModuleId.JETBRAINS_NAMESPACE),
  PluginModuleId("intellij.platform.debugger.impl", PluginModuleId.JETBRAINS_NAMESPACE),
  PluginModuleId("intellij.platform.debugger.impl.shared", PluginModuleId.JETBRAINS_NAMESPACE),
  PluginModuleId("intellij.platform.debugger.impl.ui", PluginModuleId.JETBRAINS_NAMESPACE),
)
private val GIT4IDEA_PLUGIN_ALIAS_ID = PluginId.getId("Git4Idea")
private val GIT4IDEA_MODULE_IDS = listOf(
  PluginModuleId("intellij.vcs.git.backend", PluginModuleId.JETBRAINS_NAMESPACE),
  PluginModuleId("intellij.vcs.git.shared", PluginModuleId.JETBRAINS_NAMESPACE),
)
private val externalNonBundledPluginCompatibilityDependencies = listOf(
  "intellij.libraries.groovy",
  "intellij.platform.structureView",
  "intellij.platform.todo",
  "intellij.platform.bookmarks",
  "intellij.platform.smRunner",
).map { DependencyRef.of(PluginModuleId(it, PluginModuleId.JETBRAINS_NAMESPACE)) }

/**
 * List of content modules from the core plugin which should be automatically added as dependencies third-party plugins and plugins with dependency on `com.intellij.modules.vcs`
 * plugin alias for compatibility.
 */
private val vcsApiContentModules = arrayOf(
  "intellij.platform.vcs.impl",
  "intellij.platform.vcs.dvcs",
  "intellij.platform.vcs.dvcs.impl",
  "intellij.platform.vcs.log",
  "intellij.platform.vcs.log.graph",
  "intellij.platform.vcs.log.impl",
).map { PluginModuleId(it, PluginModuleId.JETBRAINS_NAMESPACE) }

private val COLLABORATION_TOOLS_MODULE_ID = PluginModuleId("intellij.platform.collaborationTools", PluginModuleId.JETBRAINS_NAMESPACE)

/**
 * List of content modules from the core plugin which should be automatically added as dependencies to all plugins with dependency on `org.jetbrains.completion.full.line` plugin
 * alias for compatibility.
 */
private val fullLineApiContentModules = arrayOf(
  "intellij.fullLine.core",
  "intellij.fullLine.local",
  "intellij.fullLine.core.impl",
).map { PluginModuleId(it, PluginModuleId.JETBRAINS_NAMESPACE) }

/**
 * Specifies the list of content modules which was recently extracted from the main module of the core plugin and may have external usages.
 * Since such modules were loaded by the core classloader before, it wasn't necessary to specify any dependencies to use classes from them.
 * To avoid breaking compatibility, dependencies on these modules are automatically added to plugins which define dependency on the platform using
 * `<depends>com.intellij.modules.platform</depends>` or `<depends>com.intellij.modules.lang</depends>` tags.
 * See [this article](https://youtrack.jetbrains.com/articles/IJPL-A-956#keep-compatibility-with-external-plugins) for more details.
 */
private val contentModulesExtractedInCorePluginWhichCanBeUsedFromExternalPlugins = arrayOf(
  "intellij.platform.collaborationTools.auth",
  "intellij.platform.collaborationTools.auth.base",
  "intellij.platform.tasks",
  "intellij.platform.tasks.impl",
  "intellij.platform.scriptDebugger.ui",
  "intellij.platform.scriptDebugger.backend",
  "intellij.platform.scriptDebugger.protocolReaderRuntime",
  "intellij.spellchecker.xml",
  "intellij.relaxng",
  "intellij.spellchecker",
  "intellij.platform.structuralSearch",
  "intellij.xml.emmet",
).map { PluginModuleId(it, PluginModuleId.JETBRAINS_NAMESPACE) }