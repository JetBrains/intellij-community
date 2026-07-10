// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.DynamicBundle.LanguageBundleEP
import com.intellij.ide.plugins.DynamicPluginsValidators.IssueReporter
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.ui.UIThemeProvider
import com.intellij.openapi.actionSystem.impl.canUnloadActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.impl.BundledColorSchemeEPName
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.keymap.impl.BundledKeymapBean
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement
import com.intellij.serviceContainer.proxiedServicesList
import com.intellij.serviceContainer.useProxiesForOpenServices
import com.intellij.util.application
import kotlinx.coroutines.CancellationException

private val LOG get() = Logger.getInstance(DynamicPluginsValidators::class.java)

internal object DynamicPluginsValidators {
  private val VETOER_EP_NAME = ExtensionPointName<DynamicPluginVetoer>("com.intellij.ide.dynamicPluginVetoer")

  /**
   * may throw [AbortDynamicPluginIssuesComputation] to stop the computation (I know this is a smelly thing, but it's the cheapest option right now)
   */
  fun interface IssueReporter {
    fun reportIssue(reason: DynamicReconfigurationIsNotPossibleReason)
  }

  class AbortDynamicPluginIssuesComputation : Exception("", null, false, false)

  fun IssueReporter.validateGroupConformsCommonDynamicConstraints(group: RuntimeModuleGroup) {
    for (descriptor in group.sortedDescriptors) {
      validateDescriptorDoesNotRequireRestart(descriptor)
      validateDescriptorHasNoComponents(descriptor)
    }
  }

  fun IssueReporter.validateGroupCanBeLoaded(
    group: RuntimeModuleGroup,
    elementsModel: MutableAppElementsModel,
    allowServiceOverridesUnloading: Boolean,
  ) {
    for (descriptor in group.sortedDescriptors) {
      if (!allowServiceOverridesUnloading) {
        validateDescriptorHasNoServiceOverrides(descriptor)
      }
    }
    validateModuleGroupHasAllExtensionsFromDynamicEPs(group, elementsModel)
  }

  fun IssueReporter.validateGroupCanBeUnloaded(
    group: RuntimeModuleGroup,
    elementsModel: MutableAppElementsModel,
    allowServiceOverridesUnloading: Boolean,
    allowUnloadingWhenRunFromSources: Boolean,
  ) {
    for (descriptor in group.sortedDescriptors.asReversed()) {
      validateActionsCanBeUnloaded(descriptor)
      if (!allowServiceOverridesUnloading) {
        validateDescriptorHasNoServiceOverrides(descriptor)
      }
      if (!allowUnloadingWhenRunFromSources) {
        validateDescriptorUsesPluginClassloader(descriptor)
      }
    }
    validateModuleGroupHasAllExtensionsFromDynamicEPs(group, elementsModel)
  }

  fun IssueReporter.validateProductRulesPermitUnloading(group: RuntimeModuleGroup) {
    validateProductRulesPermitDynamicLoadOrUnload(group)
    if (!RegistryManager.getInstance().`is`("ide.plugins.allow.unload")) {
      // TODO in previous impl, there was a check for (!allowLoadUnloadSynchronously(module)) which basically checks that the plugin
      //  affected only UI, this is not the case anymore (bad public contract otherwise)
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "Dynamic unloading of plugins is disabled by a registry option 'ide.plugins.allow.unload'",
        null
      ))
    }
    for (descriptor in group.sortedDescriptors) {
      if (descriptor is PluginMainDescriptor) {
        validatePluginUnloadingIsNotVetoed(descriptor)
      }
    }
  }

  fun IssueReporter.validateProductRulesPermitLoading(group: RuntimeModuleGroup) {
    validateProductRulesPermitDynamicLoadOrUnload(group)
    if (!RegistryManager.getInstance().`is`("ide.plugins.allow.load")) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "Dynamic loading of plugins is disabled by a registry option 'ide.plugins.allow.load'",
        null
      ))
    }
    for (descriptor in group.sortedDescriptors) {
      if (descriptor is PluginMainDescriptor) {
        validatePluginLoadingIsNotVetoed(descriptor)
      }
    }
  }

  private fun IssueReporter.validateProductRulesPermitDynamicLoadOrUnload(group: RuntimeModuleGroup) {
    if (InstalledPluginsState.getInstance().isRestartRequired) { // TODO maybe drop this flag eventually, should not exist (or at least shouldn't be used by platform stuff)
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of("There are pending changes that require restart", null))
    }
    for (descriptor in group.sortedDescriptors) {
      if (descriptor.productCode != null && !descriptor.isBundled && !PluginManagerCore.isDevelopedByJetBrains(descriptor)) {
        reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
          "${descriptor.shortLogDescription} is a paid plugin, dynamic loading/unloading is not supported",
          descriptor.getMainDescriptor()
        ))
      }
    }
  }

  fun IssueReporter.validatePluginLoadingIsNotVetoed(descriptor: PluginMainDescriptor) {
    var reason: DynamicReconfigurationIsNotPossibleReason? = null
    VETOER_EP_NAME.processWithPluginDescriptor { vetoer, vetoerDescriptor ->
      try {
        if (vetoer.vetoPluginLoad(descriptor)) {
          reason = DynamicReconfigurationIsNotPossibleReason.of(
            "Dynamic loading of ${descriptor.shortLogDescription} was vetoed by ${vetoer.javaClass.name} from ${(vetoerDescriptor as? IdeaPluginDescriptorImpl)?.shortLogDescription}",
            descriptor.getMainDescriptor(),
          )
        }
      }
      catch (_: CancellationException) {
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
    if (reason != null) {
      reportIssue(reason)
    }
  }

  fun IssueReporter.validatePluginUnloadingIsNotVetoed(descriptor: PluginMainDescriptor) {
    val vetoMessage = VETOER_EP_NAME.computeSafeIfAny {
      it.vetoPluginUnload(descriptor)
    }
    if (vetoMessage != null) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(vetoMessage, descriptor))
    }
  }

  fun IssueReporter.validateDescriptorDoesNotRequireRestart(descriptor: IdeaPluginDescriptorImpl) {
    if (descriptor.isRequireRestart) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "${descriptor.shortLogDescription} explicitly requires restart to be loaded/unloaded",
        descriptor.getMainDescriptor()
      ))
    }
  }

  fun IssueReporter.validateDescriptorHasNoComponents(descriptor: IdeaPluginDescriptorImpl) {
    validateInAllScopes(descriptor) { container ->
      if (container.components.isNotEmpty()) {
        reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
          "${descriptor.shortLogDescription} cannot be dynamically loaded/unloaded because it declares components: ${container.components.first()}",
          descriptor.getMainDescriptor(),
        ))
      }
    }
  }

  fun IssueReporter.validateActionsCanBeUnloaded(descriptor: IdeaPluginDescriptorImpl) {
    for (action in descriptor.actions) {
      val element = action.element
      val elementName = action.name
      val canUnload = elementName == ActionElement.ActionElementName.action ||
                      elementName == ActionElement.ActionElementName.reference ||
                      (elementName == ActionElement.ActionElementName.group && canUnloadActionGroup(element))
      if (!canUnload) {
        reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
          "${descriptor.shortLogDescription} cannot be dynamically unloaded because of the action element $action",
          descriptor.getMainDescriptor(),
        )
        )
      }
    }
  }

  fun IssueReporter.validateDescriptorHasNoServiceOverrides(descriptor: IdeaPluginDescriptorImpl) {
    validateInAllScopes(descriptor) { container ->
      for (service in container.services) {
        if (service.overrides) {
          if (useProxiesForOpenServices && proxiedServicesList.contains(service.serviceInterface)) {
            continue
          }

          reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
            "${descriptor.shortLogDescription} cannot be dynamically loaded/unloaded because it declares service override: ${service}",
            descriptor.getMainDescriptor()
          ))
        }
      }
    }
  }

  fun IssueReporter.validateDescriptorUsesPluginClassloader(descriptor: IdeaPluginDescriptorImpl) {
    val classloader = descriptor.pluginClassLoader
    if (classloader != null && classloader !is PluginClassLoader && !descriptor.useIdeaClassLoader && !application.isUnitTestMode) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "${descriptor.shortLogDescription} cannot be unloaded dynamically because it is configured to use $classloader, and not PluginClassLoader. " +
        "This may happen if the IDE is started from sources.",
        descriptor.getMainDescriptor()
      ))
    }
  }

  fun IssueReporter.validateModuleGroupHasAllExtensionsFromDynamicEPs(
    group: RuntimeModuleGroup,
    elementsModel: MutableAppElementsModel,
  ) {
    val ownElementsModel by lazy {
      MutableAppElementsModel().apply {
        register(group, this@validateModuleGroupHasAllExtensionsFromDynamicEPs)
      }
    }
    for (descriptor in group.sortedDescriptors) {
      for (epFqn in descriptor.extensions.keys) {
        // TODO there were these hard-coded exclusions in the previous impl, let's try to live without them for now
        //// special case Kotlin EPs registered via code in Kotlin compiler
        //if (epName.startsWith("org.jetbrains.kotlin") && descriptor.pluginId.idString == "org.jetbrains.kotlin") {
        //  continue
        //}
        //// Workaround until SID-207 fixed
        //if (epName.startsWith("Pythonid.template") && descriptor.pluginId.idString in listOf("com.intellij.python.django", "org.jetbrains.dbt")) {
        //  continue
        //}
        val epResult = elementsModel.getExtensionPoint(epFqn) ?: ownElementsModel.getExtensionPoint(epFqn)
        if (epResult == null) {
          reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
            "${descriptor.shortLogDescription} cannot be loaded/unloaded dynamically because it uses extension point '$epFqn' which was not found.",
            descriptor.getMainDescriptor()
          ))
        }
        else {
          val (source, ep) = epResult
          if (!ep.isDynamic) {
            reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
              "${descriptor.shortLogDescription} cannot be loaded/unloaded dynamically because it uses non-dynamic extension point '$epFqn' from ${source.shortLogDescription}.",
              descriptor.getMainDescriptor()
            ))
          }
        }
      }
    }
  }

  fun IssueReporter.validatePluginIsUIOnlyAndDynamic(plugin: PluginMainDescriptor) {
    validateDescriptorDoesNotRequireRestart(plugin)
    if (plugin.contentModules.isNotEmpty()) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "${plugin.shortLogDescription} is not UI-only because it declares content modules", plugin
      ))
    }
    if (plugin.actions.isNotEmpty()) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "${plugin.shortLogDescription} is not UI-only because it declares actions", plugin
      ))
    }
    validateDescriptorHasNoComponents(plugin)
    validateDescriptorHasNoServiceOverrides(plugin)
    // TODO: should we also check listeners in scoped containers?
    if (plugin.extensions.isEmpty()) {
      reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
        "${plugin.shortLogDescription} is not UI-only because it does not declare any extensions", plugin
      ))
    }
    validateOnlyUIBoundExtensionsAreUsed(plugin)
  }

  private fun IssueReporter.validateOnlyUIBoundExtensionsAreUsed(descriptor: IdeaPluginDescriptorImpl) {
    for ((extensionFqn, _) in descriptor.extensions) {
      val isUIOnly = extensionFqn == UIThemeProvider.EP_NAME.name ||
                     extensionFqn == BundledKeymapBean.EP_NAME.name ||
                     extensionFqn == LanguageBundleEP.EP_NAME.name ||
                     extensionFqn == BundledColorSchemeEPName.name
      if (!isUIOnly) {
        reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
          "${descriptor.shortLogDescription} uses non-UI extension: ${extensionFqn}",
          descriptor.getMainDescriptor()
        ))
      }
    }
  }

  private fun <T> IdeaPluginDescriptorImpl.lookupInAllScopes(body: (ContainerDescriptor) -> T?): T? {
    body(appContainerDescriptor)?.let { return it }
    body(projectContainerDescriptor)?.let { return it }
    body(moduleContainerDescriptor)?.let { return it }
    return null
  }

  fun IssueReporter.validateInAllScopes(
    descriptor: IdeaPluginDescriptorImpl,
    validateScope: IssueReporter.(ContainerDescriptor) -> Unit,
  ) {
    validateScope(descriptor.appContainerDescriptor)
    validateScope(descriptor.projectContainerDescriptor)
    validateScope(descriptor.moduleContainerDescriptor)
  }
}

/**
 * TODO Ideally, this shouldn't exist, and the model from the real elements registration should be reused,
 *      but it's kinda too much hassle to refactor it right now, so this should suffice for the time being...
 */
internal class MutableAppElementsModel {
  private val appScope = ScopedContainer(hashMapOf())
  private val projectScope = ScopedContainer(hashMapOf())
  private val moduleScope = ScopedContainer(hashMapOf())

  fun register(group: RuntimeModuleGroup, reporter: IssueReporter) {
    for (descriptor in group.sortedDescriptors) {
      reporter.register(descriptor)
    }
  }

  fun unregister(group: RuntimeModuleGroup, reporter: IssueReporter) {
    for (descriptor in group.sortedDescriptors.asReversed()) {
      reporter.unregister(descriptor)
    }
  }

  private fun IssueReporter.register(descriptor: IdeaPluginDescriptorImpl) {
    runInEveryScope(descriptor) { container, scope ->
      for (ep in container.extensionPoints) {
        val existing = scope.extensionPoints.putIfAbsent(ep.getQualifiedName(descriptor), descriptor to ep)
        if (existing != null) {
          reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
            "Extension point ${ep.getQualifiedName(descriptor)} from ${descriptor.shortLogDescription}" +
            " was previously registered by ${existing.first.shortLogDescription}",
            descriptor.getMainDescriptor()
          ))
        }
      }
    }
  }

  private fun IssueReporter.unregister(descriptor: IdeaPluginDescriptorImpl) {
    runInEveryScope(descriptor) { container, scope ->
      for (ep in container.extensionPoints) {
        val existing = scope.extensionPoints.remove(ep.getQualifiedName(descriptor))
        if (existing == null) {
          reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
            "Extension point ${ep.getQualifiedName(descriptor)} from ${descriptor.shortLogDescription}" +
            " was expected to be registered, but was not found",
            descriptor.getMainDescriptor()
          ))
        }
        else if (existing.first != descriptor) {
          reportIssue(DynamicReconfigurationIsNotPossibleReason.of(
            "Extension point ${ep.getQualifiedName(descriptor)} from ${descriptor.shortLogDescription}" +
            " was expected to be registered, but was found associated with a different source: ${existing.first.shortLogDescription}",
            descriptor.getMainDescriptor()
          ))
        }
      }
    }
  }

  fun getExtensionPoint(fqn: String): Pair<IdeaPluginDescriptorImpl, ExtensionPointDescriptor>? {
    return lookupInEveryScope { it.extensionPoints[fqn] }
  }

  private fun IssueReporter.runInEveryScope(
    descriptor: IdeaPluginDescriptorImpl,
    body: IssueReporter.(ContainerDescriptor, ScopedContainer) -> Unit,
  ) {
    body(descriptor.appContainerDescriptor, appScope)
    body(descriptor.projectContainerDescriptor, projectScope)
    body(descriptor.moduleContainerDescriptor, moduleScope)
  }

  private fun <R> lookupInEveryScope(body: (ScopedContainer) -> R?): R? {
    body(appScope)?.let { return it }
    body(projectScope)?.let { return it }
    body(moduleScope)?.let { return it }
    return null
  }

  private class ScopedContainer(
    val extensionPoints: MutableMap<String, Pair<IdeaPluginDescriptorImpl, ExtensionPointDescriptor>> = HashMap()
  )
}