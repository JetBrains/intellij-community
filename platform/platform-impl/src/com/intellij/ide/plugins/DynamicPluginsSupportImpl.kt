// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.clearCachedValues
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.clearCachesAfterUnload
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.registerDescriptors
import com.intellij.ide.plugins.DynamicPluginsLegacyImpl.unloadModuleDescriptorNotRecursively
import com.intellij.ide.plugins.DynamicPluginsValidators.IssueReporter
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupCanBeLoaded
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupCanBeUnloaded
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupConformsCommonDynamicConstraints
import com.intellij.ide.plugins.DynamicPluginsValidators.validateProductRulesPermitLoading
import com.intellij.ide.plugins.DynamicPluginsValidators.validateProductRulesPermitUnloading
import com.intellij.ide.plugins.cl.PluginAwareClassLoader.UNLOAD_IN_PROGRESS
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.actionSystem.impl.canUnloadActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointDeferredListenersNotification
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.pluginSystem.parser.impl.elements.ActionElement.ActionElementName
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.serviceContainer.proxiedServicesList
import com.intellij.serviceContainer.useProxiesForOpenServices
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.TransferredWriteActionService
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.WeakList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance(DynamicPluginsSupportImpl::class.java)
private val REPORT_ISSUES_COUNT_PROPERTY = "dynamic.plugins.report.issues.count"

internal class DynamicPluginsSupportImpl(
  val classloaderUnloadAwaitStrategy: AwaitClassloaderUnloadStrategy
) : DynamicPluginsSupport {
  private val rwLock = Mutex() // TODO replace with a proper suspending RW lock?

  override suspend fun validateDynamicReconfigurationPossible(targetState: PluginSet): DynamicPluginsReconfigurationResult.Invalid? {
    return rwLock.withLock {
      withContext(Dispatchers.Default) {
        if (LOG.isDebugEnabled) {
          LOG.debug("validating dynamic reconfiguration to $targetState (disabled plugins may appear as unresolved)")
          PluginInitializationDiagnosticUtils.logExclusionTree(
            LOG,
            targetState.resolvedPluginSet!!,
            emptyMap() // FIXME IJPL-246161 may cause "id is not resolved" messages instead of "is marked disabled"
          )
        }
        reportSequentialProgress { reporter ->
          val target = targetState.resolvedPluginSet ?: error("resolved plugin set is not set")
          val current = getCurrentlyLoadedPluginSet()
          val sequence = buildTransitionSequence(current, target).also { LOG.debug { it.getExplanationLogMessage() } }
          validateTransitionSequenceCanBePerformedDynamically(sequence, reporter)
            .also { issues -> if (LOG.isDebugEnabled) buildExplanationMessage(issues)?.let { LOG.debug(it) } }
            .firstOrNull()?.let(DynamicPluginsReconfigurationResult::Invalid)
        }
      }
    }
  }

  override suspend fun performDynamicReconfiguration(targetState: PluginSet): DynamicPluginsReconfigurationResult {
    return rwLock.withLock {
      withContext(Dispatchers.Default) {
        reportSequentialProgress { reporter ->
          val current = getCurrentlyLoadedPluginSet()
          val target = targetState.resolvedPluginSet ?: error("resolved plugin set is not set")
          LOG.info("performing dynamic reconfiguration to $targetState (disabled plugins may appear as unresolved)")
          PluginInitializationDiagnosticUtils.logExclusionTree(
            LOG,
            target,
            emptyMap() // FIXME IJPL-246161 may cause "id is not resolved" messages instead of "is marked disabled"
          )
          val sequence = buildTransitionSequence(current, target).also {
            LOG.info(it.getExplanationLogMessage())
          }

          val dynamicReconfigurationIsNotPossibleReason = validateTransitionSequenceCanBePerformedDynamically(sequence, reporter)
            .also { issues -> buildExplanationMessage(issues)?.let { msg -> LOG.warn(msg) } }
            .firstOrNull()
          if (dynamicReconfigurationIsNotPossibleReason != null) {
            return@withContext dynamicReconfigurationIsNotPossibleReason.let(DynamicPluginsReconfigurationResult::Invalid)
          }

          saveAllSettings() // TODO should be converted to pre-reconfiguration listener

          val unloadSteps = sequence.transitionSequence.takeWhile { it.action == RuntimeModuleGroupAction.UNLOAD }
          val loadSteps = sequence.transitionSequence.drop(unloadSteps.size).takeWhile { it.action == RuntimeModuleGroupAction.LOAD }
          check(unloadSteps.size + loadSteps.size == sequence.transitionSequence.size) { "All loading actions are expected to come after all unloading actions" }

          val pluginsToLoad =
            loadSteps.asSequence().flatMap { it.runtimeModuleGroup.sortedDescriptors }.filterIsInstance<PluginMainDescriptor>()
              .associateBy { it.pluginId }
          val (successfullyUnloaded, classloadersToUnload) = unloadGroups(
            groupsToUnload = unloadSteps.map { it.runtimeModuleGroup },
            pluginsToBeLoadedLater = pluginsToLoad,
            reporter = reporter,
          )
          if (!successfullyUnloaded) {
            // broken state, require restart
            InstalledPluginsState.getInstance().isRestartRequired = true
            return@withContext DynamicPluginsReconfigurationResult.Incomplete()
          }

          loadGroups(
            targetPluginSet = targetState,
            groups = loadSteps.map { it.runtimeModuleGroup },
            reusedGroups = sequence.exactRuntimeModuleGroupAlignment.values.toList(),
            reporter = reporter,
          )
          val trulyCollected = classloaderUnloadAwaitStrategy.awaitClassloadersUnloadedPostReconfiguration(classloadersToUnload)
          if (!trulyCollected) {
            InstalledPluginsState.getInstance().isRestartRequired = true
            return@withContext DynamicPluginsReconfigurationResult.Incomplete()
          }

          return@withContext DynamicPluginsReconfigurationResult.Success()
        }
      }
    }
  }

  private fun buildExplanationMessage(issues: List<DynamicReconfigurationIsNotPossibleReason>): String? {
    if (issues.isEmpty()) return null
    return buildString {
      append("Dynamic plugins reconfiguration is not possible")
      val count = System.getProperty(REPORT_ISSUES_COUNT_PROPERTY)
      if (count != null) {
        append(" ($REPORT_ISSUES_COUNT_PROPERTY=$count)")
      } else {
        append(" (use -D$REPORT_ISSUES_COUNT_PROPERTY=100 to see more issues right away)")
      }
      appendLine(":")
      append(issues.joinToString("\n") { it.logMessage })
    }
  }

  private suspend fun saveAllSettings() {
    withProgressText(IdeBundle.message("progress.text.dynamic.plugins.saving.settings")) {
      runInAutoSaveDisabledMode {
        FileDocumentManager.getInstance().saveAllDocuments()
        saveProjectsAndApp(true)
      }
    }
  }

  private suspend fun validateTransitionSequenceCanBePerformedDynamically(
    sequence: TransitionSequence,
    reporter: SequentialProgressReporter,
  ): List<DynamicReconfigurationIsNotPossibleReason> {
    if (skipDynamicPluginReconfigurationValidation) {
      return emptyList()
    }

    return reporter.indeterminateStep(IdeBundle.message("progress.text.validating.dynamic.reconfiguration")) {
      val issuesToReport = SystemProperties.getIntProperty(REPORT_ISSUES_COUNT_PROPERTY, 1).coerceAtLeast(1)
      val issues = LinkedHashMap<String, DynamicReconfigurationIsNotPossibleReason>()
      val reporter = IssueReporter { reason: DynamicReconfigurationIsNotPossibleReason ->
        issues.putIfAbsent(reason.logMessage, reason) // deduplicate messages
        if (issues.size >= issuesToReport) {
          throw DynamicPluginsValidators.AbortDynamicPluginIssuesComputation()
        }
      }
      try {
        reporter.computeValidationIssues(sequence)
      }
      catch (_: DynamicPluginsValidators.AbortDynamicPluginIssuesComputation) { }
      return@indeterminateStep issues.values.toList()
    }
  }

  private fun IssueReporter.computeValidationIssues(sequence: TransitionSequence) {
    val elementsModel = MutableAppElementsModel()
    for (group in sequence.currentState.runtimeModuleGroupGraph.sortedGroups) {
      elementsModel.register(group, this)
    }
    for (step in sequence.transitionSequence) {
      validateGroupConformsCommonDynamicConstraints(step.runtimeModuleGroup)
      when (step.action) {
        RuntimeModuleGroupAction.UNLOAD -> {
          validateProductRulesPermitUnloading(step.runtimeModuleGroup)
          validateGroupCanBeUnloaded(
            step.runtimeModuleGroup,
            elementsModel,
            allowDynamicServiceOverrides,
            allowUnloadingWhenRunFromSources
          )
          elementsModel.unregister(step.runtimeModuleGroup, this)
        }
        RuntimeModuleGroupAction.LOAD -> {
          validateProductRulesPermitLoading(step.runtimeModuleGroup)
          validateGroupCanBeLoaded(step.runtimeModuleGroup, elementsModel, allowDynamicServiceOverrides)
          elementsModel.register(step.runtimeModuleGroup, this)
        }
      }
    }
  }

  private suspend fun unloadGroups(
    groupsToUnload: List<RuntimeModuleGroup>,
    pluginsToBeLoadedLater: Map<PluginId, PluginMainDescriptor>,
    reporter: SequentialProgressReporter,
  ): Pair<Boolean, WeakList<PluginClassLoader>> {
    val classloadersToUnload = WeakList<PluginClassLoader>()
    if (groupsToUnload.isEmpty()) {
      return true to classloadersToUnload
    }
    return reporter.indeterminateStep(IdeBundle.message("progress.text.unloading.n.modules", groupsToUnload.size)) {
      val affectedPlugins = groupsToUnload.asSequence().flatMap { it.sortedDescriptors.asReversed() }.filterIsInstance<PluginMainDescriptor>()
      try {
        withContext(Dispatchers.EDT) {
          for (plugin in affectedPlugins) {
            val isUpdate = plugin.pluginId in pluginsToBeLoadedLater
            runSafe {
              application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginUnload(plugin, isUpdate)
            }
          }
        }

        classloadersToUnload.addAll(groupsToUnload.mapNotNull { getAssociatedClassloader(it) as? PluginClassLoader })
        withProgressText(IdeBundle.message("progress.text.finishing.plugin.tasks")) {
          cancelAndJoinPluginScopes(classloadersToUnload)
        }

        edtWriteAction {
          for (group in groupsToUnload) {
            for (descriptor in group.sortedDescriptors.asReversed()) {
              deregisterDescriptor(descriptor) // FIXME calls EDT-bound listeners inside
            }
          }
          detachClassLoaders(groupsToUnload)
          clearCachesAfterUnload(classloadersToUnload) // expects EDT
        }
      }
      catch (e: Exception) {
        LOG.error("Unloading failed", e)
        for (plugin in affectedPlugins) {
          DynamicPluginsUsagesCollector.logDescriptorUnload(plugin, success = false)
        }
        throw e
      }
      finally {
        withContext(Dispatchers.EDT) {
          for (plugin in affectedPlugins) {
            val isUpdate = plugin.pluginId in pluginsToBeLoadedLater
            runSafe {
              application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginUnloaded(plugin, isUpdate)
            }
          }
        }
      }

      val collected = classloaderUnloadAwaitStrategy.awaitClassloadersUnloadedBeforeLoad(classloadersToUnload)
      for (plugin in affectedPlugins) {
        DynamicPluginsUsagesCollector.logDescriptorUnload(plugin, success = collected)
      }
      return@indeterminateStep collected to classloadersToUnload
    }
  }

  private fun deregisterDescriptor(descriptor: IdeaPluginDescriptorImpl) {
    unloadModuleDescriptorNotRecursively(descriptor) // reuse as is for now
  }

  private fun detachClassLoaders(groups: List<RuntimeModuleGroup>) {
    for (group in groups) {
      for (descriptor in group.sortedDescriptors) {
        descriptor.pluginClassLoader = null
        descriptor.isMarkedForLoading = false  // FIXME it is here only because descriptor.isEnabled still refers to isMarkedForLoading
      }
    }
  }

  /**
   * Applies new state, must be called even if there is nothing to load after unload
   */
  private suspend fun loadGroups(
    targetPluginSet: PluginSet,
    groups: List<RuntimeModuleGroup>,
    reusedGroups: List<RuntimeModuleGroup>,
    reporter: SequentialProgressReporter,
  ) {
    if (groups.isEmpty()) {
      application.runWriteAction {
        PluginManagerCore.setPluginSet(targetPluginSet)
      }
      return
    }
    reporter.indeterminateStep(IdeBundle.message("progress.text.loading.n.modules", groups.size)) {
      val affectedPlugins = groups.asSequence().flatMap { it.sortedDescriptors }.filterIsInstance<PluginMainDescriptor>()
      withContext(Dispatchers.EDT) {
        runSafe { application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginsLoaded() }
        for (plugin in affectedPlugins) {
          runSafe { application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).beforePluginLoaded(plugin) }
        }
      }
      try {
        attachClassLoaders(targetPluginSet, groups, reusedGroups)
        val listenerCallbacks = mutableListOf<ExtensionPointDeferredListenersNotification>()
        application.runWriteAction {
          val descriptors = groups.flatMap { it.sortedDescriptors }
          registerDescriptors(application as ApplicationImpl, descriptors.asSequence(), listenerCallbacks)
          clearCachedValues()

          PluginManagerCore.setPluginSet(targetPluginSet)

          if (System.getProperty("revert.IJPL233642", "false") != "true") {
            listenerCallbacks.sortBy {
              // put all registryKey EP listeners before anything else FIXME IJPL-233642
              if (it.ep.name == "com.intellij.registryKey") -1 else 0
            }
          }
          application.service<TransferredWriteActionService>().runOnEdtWithTransferredWriteActionAndWait {
            listenerCallbacks.forEach {
              it.notify.run()
            }
          }
        }
      }
      finally {
        withContext(Dispatchers.EDT) {
          for (plugin in affectedPlugins) {
            runSafe { application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginLoaded(plugin) }
            DynamicPluginsUsagesCollector.logDescriptorLoad(plugin)
            PluginManagerCore.clearPluginNonLoadReasonFor(plugin.pluginId) // FIXME this should be implied from the new plugin set state
          }
          runSafe { application.messageBus.syncPublisher(DynamicPluginListener.TOPIC).pluginsLoaded() }
        }
      }
    }
  }

  private fun attachClassLoaders(pluginSet: PluginSet, groups: List<RuntimeModuleGroup>, reusedGroups: List<RuntimeModuleGroup>) {
    val configurator = ClassLoaderConfigurator(pluginSet)
    for (group in reusedGroups) {
      for (plugin in group.sortedDescriptors.filterIsInstance<PluginMainDescriptor>()) {
        configurator.keepClassLoaderOf(plugin)
      }
    }
    for (group in groups) {
      for (descriptor in group.sortedDescriptors) {
        descriptor.isMarkedForLoading = true // FIXME it is here only because descriptor.isEnabled still refers to isMarkedForLoading
      }
      for (descriptor in group.sortedDescriptors) {
        if (descriptor is PluginModuleDescriptor) {
          configurator.configureModule(descriptor)
        }
      }
    }
  }

  private fun getCurrentlyLoadedPluginSet(): ResolvedPluginSet {
    return PluginManagerCore.getPluginSet().resolvedPluginSet ?: error("ResolvedPluginSet is not set")
  }

  private val allowDynamicServiceOverrides: Boolean
    get() = Registry.`is`("ide.plugins.allow.dynamic.services.overrides", false)

  private val allowUnloadingWhenRunFromSources: Boolean
    get() = Registry.`is`("ide.plugins.allow.unload.from.sources", false)

  private val skipDynamicPluginReconfigurationValidation: Boolean
    get() = SystemProperties.getBooleanProperty("idea.plugins.skip.dynamic.plugin.reconfiguration.validation", false)

  private fun buildTransitionSequence(current: ResolvedPluginSet, target: ResolvedPluginSet): TransitionSequence {
    val currentGraph = current.runtimeModuleGroupGraph
    val targetGraph = target.runtimeModuleGroupGraph
    val exactAlignment = BidirectionalMap<RuntimeModuleGroup, RuntimeModuleGroup>() // current <-> target
    val steps = ArrayList<TransitionStep>()
    for (currentGroup in currentGraph.sortedGroups) {
      val representativeModule = currentGroup.representativeModule
      if (target.isResolved(representativeModule)) {
        val targetGroup = target.runtimeModuleGroupGraph.getRuntimeModuleGroup(representativeModule)
        if (validateRuntimeModuleGroupsAlignExactly(currentGraph, currentGroup, targetGraph, targetGroup, exactAlignment)) {
          LOG.trace { "Exact alignment found for group: ${targetGroup}" }
          exactAlignment[currentGroup] = targetGroup
          continue
        }
      } else {
        LOG.debug { "Alignment failed for ${currentGroup}: ${representativeModule.shortLogDescription} is not resolved in new module graph" }
      }
      // exact alignment failed
      LOG.debug { "Exact alignment failed for group: ${currentGroup}, scheduling unload" }
      steps.add(TransitionStep(currentGroup, RuntimeModuleGroupAction.UNLOAD))
    }
    steps.reverse()
    for (targetGroup in targetGraph.sortedGroups) {
      if (exactAlignment.containsValue(targetGroup)) {
        continue
      }
      LOG.trace { "Group ${targetGroup} wasn't aligned, scheduling load" }
      steps.add(TransitionStep(targetGroup, RuntimeModuleGroupAction.LOAD))
    }
    return TransitionSequence(current, exactAlignment, steps)
  }

  private fun TransitionSequence.getExplanationLogMessage(): String {
    return buildString {
      append("Dynamic plugins reconfiguration sequence:\n")
      append("- reuse ${exactRuntimeModuleGroupAlignment.size} module groups")
      if (transitionSequence.isNotEmpty()) {
        append("\n")
        append(transitionSequence.joinToString("\n") {
          when (it.action) {
            RuntimeModuleGroupAction.UNLOAD -> "- unload ${it.runtimeModuleGroup}"
            RuntimeModuleGroupAction.LOAD -> "- load ${it.runtimeModuleGroup}"
          }
        })
      }
    }
  }

  /**
   * @return true if [targetGroup] can be configured from existing [currentGroup] without any (un-)loading.
   */
  private fun validateRuntimeModuleGroupsAlignExactly(
    currentGraph: RuntimeModuleGroupGraph,
    currentGroup: RuntimeModuleGroup,
    targetGraph: RuntimeModuleGroupGraph,
    targetGroup: RuntimeModuleGroup,
    existingAlignment: Map<RuntimeModuleGroup, RuntimeModuleGroup>
  ): Boolean {
    // equality by instance identity is implied
    if (currentGroup.representativeModule != targetGroup.representativeModule) {
      LOG.debug { "Alignment failed for ${currentGroup}: target group has different representative module ${targetGroup.representativeModule.shortLogDescription}" }
      return false
    }
    if (currentGroup.sortedDescriptors != targetGroup.sortedDescriptors) {
      LOG.debug {
        buildString {
          append("Alignment failed for ${currentGroup}: target group has a different set of included descriptors: ")
          val addedDescriptors = targetGroup.sortedDescriptors - currentGroup.sortedDescriptors.toSet()
          val removedDescriptors = currentGroup.sortedDescriptors - targetGroup.sortedDescriptors.toSet()
          if (addedDescriptors.isEmpty() && removedDescriptors.isEmpty()) {
            append("same set, different order")
          } else {
            if (addedDescriptors.isNotEmpty()) append("added=${addedDescriptors.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription }} ")
            if (removedDescriptors.isNotEmpty()) append("removed=${removedDescriptors.joinToString(prefix = "[", postfix = "]") { it.shortLogDescription }}")
          }
        }
      }
      return false
    }
    // if there is no alignment, take the current module group, as it will certainly fail the comparison further and will simplify debug message calculation
    val currentAlignedDependencies = currentGraph.getDirectDependencies(currentGroup).map { existingAlignment[it] ?: it }
    val targetDependencies = targetGraph.getDirectDependencies(targetGroup)
    if (currentAlignedDependencies != targetDependencies) {
      LOG.debug {
        buildString {
          append("Alignment failed for ${currentGroup}: target dependency groups don't align with current dependency groups: ")
          @Suppress("UNCHECKED_CAST")
          val addedDependencies = targetDependencies - currentAlignedDependencies.toSet()
          val removedDependencies = currentAlignedDependencies - targetDependencies.toSet()
          if (addedDependencies.isEmpty() && removedDependencies.isEmpty()) {
            append("same set, different order")
          } else {
            if (addedDependencies.isNotEmpty()) append("added=${addedDependencies.joinToString(prefix = "[", postfix = "]") { it.representativeModule.shortLogDescription }} ")
            if (removedDependencies.isNotEmpty()) {
              append("removed=${removedDependencies.joinToString(prefix = "[", postfix = "]") { it.representativeModule.shortLogDescription }}")
            }
          }
        }
      }
      return false
    }
    return true
  }

  private enum class RuntimeModuleGroupAction {
    LOAD,
    UNLOAD
  }

  private class TransitionStep(val runtimeModuleGroup: RuntimeModuleGroup, val action: RuntimeModuleGroupAction) {
    override fun toString(): String = "${action.name} ${runtimeModuleGroup}"
  }

  private class TransitionSequence(
    val currentState: ResolvedPluginSet,
    val exactRuntimeModuleGroupAlignment: BidirectionalMap<RuntimeModuleGroup, RuntimeModuleGroup>,
    val transitionSequence: List<TransitionStep>
  ) {
    override fun toString(): String = "TransitionSequence(steps=${transitionSequence.size})"
  }

  // TODO this is bad, rewrite later, there should be a separate mapping RMG -> Classloader provided by ClassLoaderConfigurator
  private fun getAssociatedClassloader(group: RuntimeModuleGroup): ClassLoader {
    val classloaders = group.sortedDescriptors.map { it.pluginClassLoader }
    check(classloaders.all { it == classloaders[0] }) { "Runtime module group is expected to have a single classloader, but it has different: ${group.sortedDescriptors}"}
    return classloaders[0] ?: error("Runtime module group is expected to have an assigned classloader: ${group.sortedDescriptors}")
  }

  private suspend fun cancelAndJoinPluginScopes(classLoaders: WeakList<PluginClassLoader>) {
    for (classLoader in classLoaders) {
      classLoader.state = UNLOAD_IN_PROGRESS // triggers plugin scope cancellation, but just in case it changes in the future we do that here ourselves
      classLoader.pluginCoroutineScope.cancel()
    }
    val delayDuration = 10.seconds
    coroutineScope {
      for (classLoader in classLoaders) {
        val scopeDescription = "Cancellation of ${classLoader.pluginId} plugin's scope"
        launch(CoroutineName(scopeDescription)) {
          val scope = classLoader.pluginCoroutineScope
          val dumpJob = launch {
            delay(delayDuration)
            LOG.warn("$scopeDescription: scope was not completed in $delayDuration.\n${dumpCoroutines(scope = scope, stripDump = false)}")
          }
          try {
            scope.coroutineContext[Job]?.cancelAndJoin()
          }
          finally {
            dumpJob.cancel()
          }
          LOG.trace { "$scopeDescription: scope was completed" }
        }
      }
    }
  }
}

private object DynamicPluginsValidators {
  fun interface IssueReporter {
    /**
     * may throw [AbortDynamicPluginIssuesComputation] to stop the computation (I know this is a smelly thing, but it's the cheapest option right now)
     */
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
      val canUnload = elementName == ActionElementName.action ||
                      elementName == ActionElementName.reference ||
                      (elementName == ActionElementName.group && canUnloadActionGroup(element))
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
private class MutableAppElementsModel {
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

private fun <R> runSafe(body: () -> R): R? {
  try {
    return body()
  }
  catch (e: Throwable) {
    if (e !is CancellationException) LOG.error(e)
    return null
  }
}