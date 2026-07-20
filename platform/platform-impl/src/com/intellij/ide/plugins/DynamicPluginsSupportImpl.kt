// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.configurationStore.runInAutoSaveDisabledMode
import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.diagnostic.dumpCoroutines
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.DynamicPluginsCachesCleanup.clearCachedValues
import com.intellij.ide.plugins.DynamicPluginsCachesCleanup.clearCachesAfterUnload
import com.intellij.ide.plugins.DynamicPluginsValidators.IssueReporter
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupCanBeLoaded
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupCanBeUnloaded
import com.intellij.ide.plugins.DynamicPluginsValidators.validateGroupConformsCommonDynamicConstraints
import com.intellij.ide.plugins.DynamicPluginsValidators.validateProductRulesPermitLoading
import com.intellij.ide.plugins.DynamicPluginsValidators.validateProductRulesPermitUnloading
import com.intellij.ide.plugins.cl.PluginAwareClassLoader.UNLOAD_IN_PROGRESS
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.ExtensionPointDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointDeferredListenersNotification
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.platform.util.progress.withProgressText
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.TransferredWriteActionService
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.containers.WeakList
import com.intellij.util.messages.impl.MessageBusEx
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
import java.util.function.Predicate
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
            targetState.resolvedPluginSet,
            emptyMap() // FIXME IJPL-246161 may cause "id is not resolved" messages instead of "is marked disabled"
          )
        }
        reportSequentialProgress { reporter ->
          val target = targetState.resolvedPluginSet
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
          val target = targetState.resolvedPluginSet
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
          }
          for (group in groups) {
            for (descriptor in group.sortedDescriptors) {
              PluginManagerCore.clearPluginNonLoadReasonFor(descriptor.pluginId) // FIXME this should be implied from the new plugin set state
            }
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
        if (descriptor is PluginModuleDescriptor) {
          configurator.configureModule(descriptor)
        }
      }
    }
  }

  private fun getCurrentlyLoadedPluginSet(): ResolvedPluginSet {
    return PluginManagerCore.getPluginSet().resolvedPluginSet
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

  internal fun unloadModuleDescriptorNotRecursively(module: IdeaPluginDescriptorImpl) {
    val app = ApplicationManager.getApplication() as ApplicationImpl
    (ActionManager.getInstance() as ActionManagerImpl).unloadActions(module)

    val openedProjects = ProjectUtil.getOpenProjects().toMutableList()
    @Suppress("TestOnlyProblems")
    if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
      openedProjects.add(ProjectManagerEx.getInstanceEx().defaultProject)
    }

    val appExtensionArea = app.extensionArea
    val priorityUnloadListeners = mutableListOf<Runnable>()
    val unloadListeners = mutableListOf<Runnable>()
    unregisterExtensions(
      extensionMap = module.extensions,
      pluginDescriptor = module,
      appExtensionArea = appExtensionArea,
      openedProjects = openedProjects,
      priorityUnloadListeners = priorityUnloadListeners,
      unloadListeners = unloadListeners
    )

    for (priorityUnloadListener in priorityUnloadListeners) {
      priorityUnloadListener.run()
    }
    for (unloadListener in unloadListeners) {
      unloadListener.run()
    }

    // first, reset all plugin extension points before unregistering, so that listeners don't see plugin in semi-torn-down state
    processExtensionPoints(module, openedProjects) { points, area ->
      area.resetExtensionPoints(points, module)
    }
    // unregister plugin extension points
    processExtensionPoints(module, openedProjects) { points, area ->
      area.unregisterExtensionPoints(points, module)
    }

    val appMessageBus = app.messageBus as MessageBusEx
    app.unloadServices(module, module.appContainerDescriptor.services)
    appMessageBus.unsubscribeLazyListeners(module, module.appContainerDescriptor.listeners)

    for (project in openedProjects) {
      (project as ComponentManagerEx).unloadServices(module, module.projectContainerDescriptor.services)
      (project.messageBus as MessageBusEx).unsubscribeLazyListeners(module, module.projectContainerDescriptor.listeners)

      val moduleServices = module.moduleContainerDescriptor.services
      for (ideaModule in ModuleManager.getInstance(project).modules) {
        (ideaModule as ComponentManagerEx).unloadServices(module, moduleServices)
        createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ideaModule, it) }
      }

      createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(project, it) }
    }

    appMessageBus.disconnectPluginConnections(Predicate { aClass ->
      (aClass.classLoader as? PluginClassLoader)?.pluginDescriptor === module
    })

    createDisposeTreePredicate(module)?.let { Disposer.disposeChildren(ApplicationManager.getApplication(), it) }

    val pluginClassLoader = module.pluginClassLoader as? PluginClassLoader
    if (pluginClassLoader != null) {
      Language.unregisterAllLanguagesIn(pluginClassLoader, module)
    }
  }

  internal fun unregisterExtensions(
    extensionMap: Map<String, List<ExtensionDescriptor>>,
    pluginDescriptor: IdeaPluginDescriptorImpl,
    appExtensionArea: ExtensionsAreaImpl,
    openedProjects: List<Project>,
    priorityUnloadListeners: MutableList<Runnable>,
    unloadListeners: MutableList<Runnable>,
  ) {
    for (epName in extensionMap.keys) {
      val isAppLevelEp = appExtensionArea.unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners,
                                                               unloadListeners)
      if (isAppLevelEp) {
        continue
      }

      for (project in openedProjects) {
        val isProjectLevelEp = (project.extensionArea as ExtensionsAreaImpl)
          .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
        if (!isProjectLevelEp) {
          for (module in ModuleManager.getInstance(project).modules) {
            (module.extensionArea as ExtensionsAreaImpl)
              .unregisterExtensions(epName, pluginDescriptor, priorityUnloadListeners, unloadListeners)
          }
        }
      }
    }
  }

  internal fun processExtensionPoints(
    pluginDescriptor: IdeaPluginDescriptorImpl,
    projects: List<Project>,
    processor: (points: List<ExtensionPointDescriptor>, area: ExtensionsAreaImpl) -> Unit,
  ) {
    pluginDescriptor.appContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let {
      processor(it, ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
    }
    pluginDescriptor.projectContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let { extensionPoints ->
      for (project in projects) {
        processor(extensionPoints, project.extensionArea as ExtensionsAreaImpl)
      }
    }
    pluginDescriptor.moduleContainerDescriptor.extensionPoints.takeIf { it.isNotEmpty() }?.let { extensionPoints ->
      for (project in projects) {
        for (module in ModuleManager.getInstance(project).modules) {
          processor(extensionPoints, module.extensionArea as ExtensionsAreaImpl)
        }
      }
    }
  }

  internal fun registerDescriptors(
    app: ApplicationImpl,
    descriptors: Sequence<IdeaPluginDescriptorImpl>,
    listenerCallbacks: MutableList<ExtensionPointDeferredListenersNotification>,
  ) {
    app.registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)

    val openedProjects = ProjectUtil.getOpenProjects().toMutableList()
    @Suppress("TestOnlyProblems")
    if (ProjectManagerEx.getInstanceEx().isDefaultProjectInitialized) {
      openedProjects.add(ProjectManagerEx.getInstanceEx().defaultProject)
    }

    for (openProject in openedProjects) {
      openProject.getComponentManagerImpl().registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)

      for (module in ModuleManager.getInstance(openProject).modules) {
        module.getComponentManagerImpl().registerComponents(descriptors = descriptors, app = app, listenerCallbacks = listenerCallbacks)
      }
    }

    (ActionManager.getInstance() as ActionManagerImpl).registerActions(descriptors)
  }

  private fun createDisposeTreePredicate(pluginDescriptor: IdeaPluginDescriptorImpl): Predicate<Disposable>? {
    val classLoader = pluginDescriptor.pluginClassLoader as? PluginClassLoader ?: return null
    return Predicate {
      it::class.java.classLoader === classLoader
    }
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
}