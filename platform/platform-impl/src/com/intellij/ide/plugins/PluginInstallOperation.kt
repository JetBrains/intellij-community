// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ide.IdeBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector
import com.intellij.ide.plugins.marketplace.statistics.enums.InstallationSourceEnum
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUiModelAdapter
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.PluginDownloader
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.util.IdentityHashMap
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function

@ApiStatus.Internal
class PluginInstallOperation(
  private val myPluginsToInstall: List<PluginUiModel>,
  private val myCustomReposPlugins: Collection<PluginUiModel>,
  private val myIndicator: ProgressIndicator,
  private val myPluginEnabler: PluginEnabler,
) {
  private var mySuccess = true
  private val myDependant: MutableSet<PluginInstallCallbackData> = HashSet()
  private var myAllowInstallWithoutRestart = false
  private val myPendingDynamicPluginInstalls: MutableList<PendingDynamicPluginInstall> = ArrayList()
  private var myRestartRequired = false
  private var myShownErrors = false
  private val myLocalInstallCallbacks: MutableMap<PluginId, ActionCallback> = IdentityHashMap()
  private val myLocalWaitInstallCallbacks: MutableMap<PluginId, ActionCallback> = IdentityHashMap()

  constructor(
    pluginsToInstall: List<PluginNode>,
    customReposPlugins: Collection<PluginNode>,
    pluginEnabler: PluginEnabler,
    indicator: ProgressIndicator,
  ) : this(
    pluginsToInstall.map { PluginUiModelAdapter(it) },
    customReposPlugins.map { PluginUiModelAdapter(it) },
    indicator,
    pluginEnabler,
  )

  init {
    synchronized(ourInstallLock) {
      for (node in myPluginsToInstall) {
        val id = node.pluginId
        val callback = ourInstallCallbacks[id]
        if (callback == null) {
          createInstallCallback(id)
        }
        else {
          myLocalWaitInstallCallbacks[id] = callback
        }
      }
    }
  }

  private fun createInstallCallback(id: PluginId) {
    val callback = ActionCallback()
    ourInstallCallbacks[id] = callback
    myLocalInstallCallbacks[id] = callback
  }

  fun setAllowInstallWithoutRestart(allowInstallWithoutRestart: Boolean) {
    myAllowInstallWithoutRestart = allowInstallWithoutRestart
  }

  val pendingDynamicPluginInstalls: List<PendingDynamicPluginInstall>
    get() = myPendingDynamicPluginInstalls

  val isRestartRequired: Boolean
    get() = myRestartRequired

  @RequiresBackgroundThread
  fun run() {
    updateUrls()
    mySuccess = prepareToInstall(myPluginsToInstall, MutablePluginInstallationModel())
  }

  val isSuccess: Boolean
    get() = mySuccess

  val installedDependentPlugins: Set<PluginInstallCallbackData>
    get() = myDependant

  val isShownErrors: Boolean
    get() = myShownErrors

  private fun updateUrls() {
    var unknownNodes = false
    for (node in myPluginsToInstall) {
      if (Strings.areSameInstance(node.repositoryName, PluginInstaller.UNKNOWN_HOST_MARKER)) {
        unknownNodes = true
        break
      }
    }
    if (!unknownNodes) return

    val allPlugins: MutableMap<PluginId, PluginUiModel> = HashMap()
    for (host in RepositoryHelper.getCustomPluginRepositoryHosts()) {
      try {
        for (descriptor in RepositoryHelper.loadPluginModels(host, null, myIndicator)) {
          allPlugins[descriptor.pluginId] = descriptor
        }
      }
      catch (_: IOException) {
      }
    }

    for (node in myPluginsToInstall) {
      if (Strings.areSameInstance(node.repositoryName, PluginInstaller.UNKNOWN_HOST_MARKER)) {
        val descriptor = allPlugins[node.pluginId]
        node.repositoryName = descriptor?.repositoryName
        val oldUrl = node.downloadUrl
        if (descriptor != null) {
          node.downloadUrl = descriptor.downloadUrl
        }
        LOG.info("updateUrls for node: ${node.pluginId} | ${node.version} | $oldUrl to: ${node.repositoryName} | ${node.downloadUrl}")
      }
    }
  }

  @RequiresBackgroundThread
  private fun prepareToInstall(pluginsToInstall: List<PluginUiModel>, installModel: MutablePluginInstallationModel): Boolean {
    for (plugin in pluginsToInstall) {
      installModel.assumeInstalled(plugin)
    }

    var result = false
    for (pluginNode in pluginsToInstall) {
      myIndicator.setText(pluginNode.name)
      try {
        result = result or prepareToInstallWithCallback(pluginNode, installModel)
      }
      catch (e: IOException) {
        val title = IdeBundle.message("title.plugin.error")
        LOG.warn(e)
        val group: NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Plugin Error")
        Notifications.Bus.notify(group.createNotification(title, pluginNode.name + ": " + e.message, NotificationType.ERROR))
        return false
      }
    }
    return result
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  private fun prepareToInstallWithCallback(
    pluginNode: PluginUiModel,
    installModel: MutablePluginInstallationModel,
  ): Boolean {
    val id = pluginNode.pluginId
    val localCallback = myLocalInstallCallbacks.remove(id)

    if (localCallback == null) {
      val callback = myLocalWaitInstallCallbacks.remove(id)
      if (callback == null) {
        return prepareToInstall(pluginNode, installModel)
      }
      return callback.waitFor(-1) && callback.isDone
    }
    else {
      try {
        val result = prepareToInstall(pluginNode, installModel)
        removeInstallCallback(id, localCallback, result)
        return result
      }
      catch (e: IOException) {
        removeInstallCallback(id, localCallback, false)
        throw e
      }
      catch (e: RuntimeException) {
        removeInstallCallback(id, localCallback, false)
        throw e
      }
    }
  }

  @RequiresBackgroundThread
  @Throws(IOException::class)
  private fun prepareToInstall(
    pluginNode: PluginUiModel,
    installModel: MutablePluginInstallationModel,
  ): Boolean {
    if (!PluginManagementPolicy.getInstance().canInstallPlugin(pluginNode.getDescriptor())) {
      LOG.warn("The plugin ${pluginNode.pluginId} is not allowed to install for the organization")
      return false
    }
    val toDisable = checkDependenciesAndReplacements(pluginNode.getDescriptor())
    myShownErrors = false
    val downloader = PluginDownloader.createDownloader(pluginNode, pluginNode.repositoryName, null)
    val previousDescriptor = PluginManagerCore.getPlugin(pluginNode.pluginId)
    val previousVersion = previousDescriptor?.getVersion()
    PluginManagerUsageCollector.pluginInstallationStarted(
      pluginNode.getDescriptor(),
      if (downloader.isFromMarketplace()) InstallationSourceEnum.MARKETPLACE else InstallationSourceEnum.CUSTOM_REPOSITORY,
      previousVersion,
    )

    val prepared = downloader.prepareToInstall(myIndicator)
    if (prepared) {
      val descriptor = downloader.descriptor as PluginMainDescriptor
      if (!checkMissingDependencies(descriptor, installModel)) {
        return false
      }
      val newPluginVersions = buildList {
        add(descriptor)
        addAll(myPendingDynamicPluginInstalls.map { it.pluginDescriptor as PluginMainDescriptor })
      }
      val allowNoRestart = myAllowInstallWithoutRestart &&
                           runBlockingCancellable {
                             if (DynamicPluginsSupport.getInstance() != null) {
                               DynamicPlugins.checkCanReconfigureWithoutRestart(
                                 addNewCustomPlugins = newPluginVersions,
                                 forceRemovePlugins = emptyList(),
                                 extraStateValidator = DynamicPlugins.expectPluginsState(expectToLoad = newPluginVersions.map { it.pluginId }),
                                 pretendEnabled = emptyList(),
                                 pretendDisabled = emptyList(),
                               )
                             }
                             else {
                               DynamicPlugins.allowLoadUnloadWithoutRestart(
                                 descriptor, null,
                                 myPendingDynamicPluginInstalls.map { pluginInstall -> pluginInstall.pluginDescriptor },
                               )
                             }
                           }
      if (allowNoRestart) {
        myPendingDynamicPluginInstalls.add(PendingDynamicPluginInstall(downloader.getFilePath(), descriptor))
        val state = InstalledPluginsState.getInstanceIfLoaded()
        state?.onPluginInstall(downloader.descriptor, false, false)
      }
      else {
        myRestartRequired = true
        synchronized(PluginInstaller.ourLock) {
          downloader.install()
        }
      }
      myDependant.add(PluginInstallCallbackData(downloader.getFilePath(), descriptor, !allowNoRestart))
      val node = pluginNode.getDescriptor()
      if (node is PluginNode) {
        node.status = PluginNode.Status.DOWNLOADED
      }
      if (toDisable != null) {
        myPluginEnabler.disable(setOf(toDisable))
      }
      return true
    }
    else {
      myShownErrors = downloader.isShownErrors
      return false
    }
  }

  fun checkDependenciesAndReplacements(pluginNode: IdeaPluginDescriptor): IdeaPluginDescriptor? {
    val pluginReplacement = PluginReplacement.EP_NAME.extensionList.find {
      it.newPluginId == pluginNode.getPluginId().idString
    }
    if (pluginReplacement == null) {
      return null
    }
    val oldPluginId = pluginReplacement.oldPluginDescriptor.getPluginId()
    val oldPlugin = PluginManagerCore.getPlugin(oldPluginId)
    if (oldPlugin == null) {
      LOG.warn("Plugin with id '$oldPluginId' not found")
      return null
    }
    if (myPluginEnabler.isDisabled(oldPlugin.getPluginId())) {
      return null
    }
    val toDisable = AtomicBoolean()
    ApplicationManager.getApplication().invokeAndWait({
      val choice = MessageDialogBuilder.yesNo(pluginReplacement.getReplacementMessage(oldPlugin, pluginNode),
                                              IdeBundle.message("plugin.manager.obsolete.plugins.detected.title"))
        .yesText(IdeBundle.message("plugins.configurable.disable")).noText(Messages.getNoButton()).icon(Messages.getWarningIcon())
        .guessWindowAndAsk()
      toDisable.set(choice)
    }, ModalityState.any())
    return if (toDisable.get()) oldPlugin else null
  }

  internal fun checkMissingDependencies(
    pluginNode: PluginMainDescriptor,
    installModel: MutablePluginInstallationModel,
  ): Boolean {
    LOG.debug { "Checking missing dependencies for $pluginNode. Plugins being installed: $installModel" }
    installModel.assumeInstalled(pluginNode)

    val missingRequiredPlugins: MutableMap<PluginId, PluginUiModel> = HashMap()
    val missingOptionalPlugins: MutableMap<PluginId, PluginUiModel> = HashMap()

    for (dependency in pluginNode.getDependencies()) {
      LOG.debug { "Processing depends dependency: " + dependency.pluginId + " optional=" + dependency.isOptional }
      val dependencyId = dependency.pluginId
      val targetCollector = if (dependency.isOptional) missingOptionalPlugins else missingRequiredPlugins

      // pluginNode that comes from the Marketplace contains mixed dependencies on both plugins and modules
      val shouldSkip = Function<PluginId, Boolean> { pluginId ->
        val pluginIdAsModuleId = PluginModuleId.getId(pluginId.idString, PluginModuleId.JETBRAINS_NAMESPACE)
        installModel.isPluginIdAvailable(pluginId) ||
        installModel.isModuleIdAvailable(pluginIdAsModuleId) ||
        InstalledPluginsState.getInstance().wasInstalled(pluginId) ||
        InstalledPluginsState.getInstance().wasInstalledWithoutRestart(pluginId) ||
        targetCollector.containsKey(pluginId)
      }
      if (shouldSkip.apply(dependencyId)) {
        LOG.debug { "Dependency is already satisfied" }
        continue
      }
      var resolvedDependencyId = resolveModuleInMarketplaceWithCache(dependencyId.idString)
      if (resolvedDependencyId == null) {
        resolvedDependencyId = dependencyId
      }
      if (resolvedDependencyId != dependencyId) LOG.debug { "Dependency is resolved into $resolvedDependencyId" }
      if (shouldSkip.apply(resolvedDependencyId)) {
        LOG.debug { "Dependency is already satisfied" }
        continue
      }
      val depPluginDescriptor = findPluginInRepo(resolvedDependencyId)
      if (depPluginDescriptor != null) {
        LOG.debug { "Adding " + resolvedDependencyId + " to missing dependencies (optional=" + dependency.isOptional + ")" }
        targetCollector[resolvedDependencyId] = depPluginDescriptor
        if (targetCollector === missingRequiredPlugins) {
          installModel.assumeInstalled(depPluginDescriptor)
        }
      }
      else {
        LOG.debug { "Plugin $resolvedDependencyId is not found in the repository" }
      }
    }

    val processRequiredModuleDependencies = Function<PluginModuleDescriptor, Void?> { module ->
      for (dependencyPluginId in module.moduleDependencies.plugins) {
        LOG.debug {
          "Processing v2 plugin dependency: " + dependencyPluginId.idString + " in " +
          (if (module is ContentModuleDescriptor) "content module " + module.getModuleNameString() else "main descriptor")
        }
        val shouldSkip = Function<PluginId, Boolean> { pluginId ->
          installModel.isPluginIdAvailable(pluginId) ||
          InstalledPluginsState.getInstance().wasInstalled(pluginId) ||
          InstalledPluginsState.getInstance().wasInstalledWithoutRestart(pluginId) ||
          missingRequiredPlugins.containsKey(pluginId)
        }
        if (shouldSkip.apply(dependencyPluginId)) {
          LOG.debug { "Dependency is already satisfied" }
          continue
        }
        var resolvedDependencyId = resolveModuleInMarketplaceWithCache(dependencyPluginId.idString)
        if (resolvedDependencyId == null) {
          resolvedDependencyId = dependencyPluginId
        }
        if (resolvedDependencyId != dependencyPluginId) LOG.debug { "Dependency is resolved into $resolvedDependencyId" }
        if (shouldSkip.apply(resolvedDependencyId)) {
          LOG.debug { "Dependency is already satisfied" }
          continue
        }
        val depPluginDescriptor = findPluginInRepo(resolvedDependencyId)
        if (depPluginDescriptor != null) {
          LOG.debug { "Adding $resolvedDependencyId to missing dependencies" }
          missingRequiredPlugins[resolvedDependencyId] = depPluginDescriptor
          installModel.assumeInstalled(depPluginDescriptor)
        }
        else {
          LOG.debug { "Plugin $resolvedDependencyId is not found in the repository" }
        }
      }
      for (dependencyModuleId in module.moduleDependencies.modules) {
        LOG.debug {
          "Processing v2 module dependency: " + dependencyModuleId.name + " in " +
          (if (module is ContentModuleDescriptor) "content module " + module.getModuleNameString() else "main descriptor")
        }
        if (installModel.isModuleIdAvailable(dependencyModuleId)) {
          LOG.debug { "Dependency is already satisfied" }
          continue
        }
        val resolvedDependencyId = resolveModuleInMarketplaceWithCache(dependencyModuleId.name)
        if (resolvedDependencyId == null) {
          LOG.debug { "Dependency is not resolved" }
          continue
        }
        LOG.debug { "Dependency is resolved into $resolvedDependencyId" }
        if (installModel.isPluginIdAvailable(resolvedDependencyId) ||
            InstalledPluginsState.getInstance().wasInstalled(resolvedDependencyId) ||
            InstalledPluginsState.getInstance().wasInstalledWithoutRestart(resolvedDependencyId) ||
            missingRequiredPlugins.containsKey(resolvedDependencyId)) {
          LOG.debug { "Dependency is already satisfied" }
          continue
        }
        val depPluginDescriptor = findPluginInRepo(resolvedDependencyId)
        if (depPluginDescriptor != null) {
          LOG.debug { "Adding $resolvedDependencyId to missing dependencies" }
          missingRequiredPlugins[resolvedDependencyId] = depPluginDescriptor
          installModel.assumeInstalled(depPluginDescriptor)
        }
        else {
          LOG.debug { "Plugin $resolvedDependencyId is not found in the repository" }
        }
      }
      null
    }

    processRequiredModuleDependencies.apply(pluginNode)
    for (module in pluginNode.contentModules) {
      if (module.moduleLoadingRule.required) {
        processRequiredModuleDependencies.apply(module)
      }
    }
    // optional modules are skipped because they form a majority of content modules and the result is not really used, see comment below

    if (!prepareDependencies(
        pluginNode = pluginNode,
        dependencies = missingRequiredPlugins.values.toMutableList(),
        installModel = installModel,
        titleKey = "plugin.manager.dependencies.detected.title",
        messageKey = "plugin.manager.dependencies.detected.message",
        askConfirmation = false,
      )) {
      return false
    }
    if (Registry.`is`("ide.plugins.suggest.install.optional.dependencies") && // TODO only 2 users use this, let's drop it?
        !prepareDependencies(
          pluginNode = pluginNode,
          dependencies = missingOptionalPlugins.values.toMutableList(),
          installModel = installModel,
          titleKey = "plugin.manager.optional.dependencies.detected.title",
          messageKey = "plugin.manager.optional.dependencies.detected.message",
          askConfirmation = true,
        )) {
      return false
    }
    return true
  }

  @RequiresBackgroundThread
  private fun prepareDependencies(
    pluginNode: IdeaPluginDescriptor,
    dependencies: MutableList<PluginUiModel>,
    installModel: MutablePluginInstallationModel,
    @NonNls titleKey: String,
    @NonNls messageKey: String,
    askConfirmation: Boolean,
  ): Boolean {
    if (dependencies.isEmpty()) {
      return true
    }

    try {
      val result = Ref(false)

      ApplicationManager.getApplication().invokeAndWait({
        synchronized(ourInstallLock) {
          val pluginsState = InstalledPluginsState.getInstance()
          val dependenciesToShow: MutableSet<PluginId> = LinkedHashSet()
          val iterator = dependencies.iterator()
          while (iterator.hasNext()) {
            val pluginId = iterator.next().pluginId
            val callback = ourInstallCallbacks[pluginId]
            if (callback == null || callback.isRejected) {
              if (pluginsState.wasInstalled(pluginId) || pluginsState.wasInstalledWithoutRestart(pluginId)) {
                iterator.remove()
                continue
              }
              dependenciesToShow.add(pluginId)
            }
            else {
              myLocalWaitInstallCallbacks[pluginId] = callback
            }
          }

          if (dependenciesToShow.isEmpty()) {
            result.set(true)
            return@invokeAndWait
          }

          if (!askConfirmation) {
            for (dependency in dependenciesToShow) {
              createInstallCallback(dependency)
            }
            result.set(true)
          }
          else {
            val deps = getPluginsText(dependencies)
            val dialogResult =
              Messages.showYesNoDialog(IdeBundle.message(messageKey, pluginNode.getName(), deps),
                                       IdeBundle.message(titleKey),
                                       IdeBundle.message("plugins.configurable.install"),
                                       Messages.getCancelButton(),
                                       Messages.getWarningIcon())

            result.set(dialogResult == Messages.YES)
            if (result.get()) {
              for (dependency in dependenciesToShow) {
                createInstallCallback(dependency)
              }
            }
          }
        }
      }, ModalityState.any())

      return dependencies.isEmpty() ||
             result.get() && prepareToInstall(dependencies, installModel)
    }
    catch (_: Exception) {
      return false
    }
  }

  /**
   * Searches for a plugin with id 'depPluginId' in custom repos and Marketplace and then takes one with a bigger version number
   */
  private fun findPluginInRepo(depPluginId: PluginId): PluginUiModel? {
    val pluginFromCustomRepos = myCustomReposPlugins
      .firstOrNull { p -> p.pluginId == depPluginId }
    val pluginFromMarketplace = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdateModel(depPluginId)
    val fromCustomRepos = pluginFromMarketplace == null ||
                          pluginFromCustomRepos != null &&
                          PluginDownloader.compareVersionsSkipBrokenAndIncompatible(
                            newPluginVersion = pluginFromCustomRepos.version!!,
                            existingPlugin = pluginFromMarketplace.getDescriptor()
                          ) > 0
    return if (fromCustomRepos) pluginFromCustomRepos else pluginFromMarketplace
  }

  companion object {
    private val LOG = Logger.getInstance(PluginInstallOperation::class.java)

    private val ourModuleResolutionCache: Cache<String, Optional<PluginId>> = Caffeine
      .newBuilder()
      .expireAfterWrite(1, TimeUnit.HOURS)
      .build()

    private val ourInstallCallbacks: MutableMap<PluginId, ActionCallback> = IdentityHashMap()
    private val ourInstallLock = Any()

    private fun removeInstallCallback(id: PluginId, callback: ActionCallback, isDone: Boolean) {
      synchronized(ourInstallLock) {
        val oldValue = ourInstallCallbacks[id]
        if (oldValue === callback) {
          ourInstallCallbacks.remove(id)
        }
      }
      if (isDone) {
        callback.setDone()
      }
      else {
        callback.setRejected()
      }
    }

    private fun getPluginsText(nodes: List<PluginUiModel>): @Nls String {
      val pluginNames = nodes.map { node -> StringUtil.wrapWithDoubleQuote(node.name!!) }
      val size = pluginNames.size
      if (size == 1) {
        return pluginNames[0]
      }
      return NlsMessages.formatAndList(pluginNames)
    }

    /**
     * Beware: Marketplace treats both plugin ids and content module ids as "modules"
     */
    private fun resolveModuleInMarketplaceWithCache(moduleId: String): PluginId? {
      val cachedResult = ourModuleResolutionCache.getIfPresent(moduleId)
      if (cachedResult != null) {
        return cachedResult.orElse(null)
      }
      LOG.debug("Resolving module $moduleId in Marketplace")
      val result = MarketplaceRequests.getInstance().getCompatibleUpdateByModule(moduleId)
      ourModuleResolutionCache.put(moduleId, Optional.ofNullable(result))
      LOG.debug("Resolved module $moduleId in Marketplace: $result")
      return result
    }
  }
}

/**
 * Helps keep track of which ids would resolve when we intend to install multiple plugins and their dependencies.
 */
internal class MutablePluginInstallationModel() {
  val existingPluginSet = PluginManagerCore.getPluginSetOrNull()?.allPlugins.let { allPlugins ->
    AmbiguousPluginSet.build(allPlugins?.toList() ?: emptyList())
  }
  val assumePluginIdsInstalled = mutableSetOf<PluginId>()
  val assumeContentModuleIdsInstalled = mutableSetOf<PluginModuleId>()

  fun isPluginIdAvailable(pluginId: PluginId): Boolean {
    return existingPluginSet.resolvePluginId(pluginId).firstOrNull() != null || assumePluginIdsInstalled.contains(pluginId)
  }

  fun isModuleIdAvailable(moduleId: PluginModuleId): Boolean {
    return existingPluginSet.resolveContentModuleId(moduleId).firstOrNull() != null || assumeContentModuleIdsInstalled.contains(moduleId)
  }

  fun assumeInstalled(plugin: PluginMainDescriptor) {
    assumePluginIdsInstalled.add(plugin.pluginId)
    assumePluginIdsInstalled.addAll(plugin.pluginAliases)
    for (contentModule in plugin.contentModules) {
      assumePluginIdsInstalled.addAll(contentModule.pluginAliases)
      assumeContentModuleIdsInstalled.add(contentModule.moduleId)
    }
  }

  fun assumeInstalled(pluginModel: PluginUiModel) {
    assumePluginIdsInstalled.add(pluginModel.pluginId)
    // addedPluginIdsAfterInstallation.addAll(plugin.pluginAliases) // FIXME no aliases
    for (contentModule in pluginModel.contentModules) {
      // FIXME this won't work for third-party plugins
      assumeContentModuleIdsInstalled.add(PluginModuleId.getId(contentModule.moduleName, PluginModuleId.JETBRAINS_NAMESPACE))
      // FIXME no aliases
    }
  }

  override fun toString(): String {
    return "MutablePluginInstallationModel(" +
           "assumePluginIdsInstalled=${assumePluginIdsInstalled.joinToString(prefix = "[", postfix = "]")}, " +
           "assumeContentModuleIdsInstalled=${assumeContentModuleIdsInstalled.joinToString(prefix = "[", postfix = "]")}" +
           ")"
  }
}