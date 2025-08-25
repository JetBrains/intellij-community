// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.getActiveFrameOrWelcomeScreen
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.newui.PluginLogo.getDefault
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable.TopComponentController
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.progress.jobToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SystemProperties
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Window
import java.util.*
import java.util.function.Consumer
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
open class MyPluginModel(project: Project?) : InstalledPluginsTableModel(project), PluginEnabler {
  private var myInstalledPanel: PluginsGroupComponent? = null
  var downloadedGroup: PluginsGroup? = null
    private set
  private var myInstalling: PluginsGroup? = null
  private var myTopController: TopComponentController? = null
  private var myVendors: SortedSet<String>? = null
  private var myTags: SortedSet<String>? = null

  var needRestart: Boolean = false

  @JvmField
  var createShutdownCallback: Boolean = true

  private val myStatusBar: StatusBarEx?

  private var myPluginUpdatesService: PluginUpdatesService? = null

  private var myInvalidFixCallback: Runnable? = null
  private var myCancelInstallCallback: ((PluginUiModel?) -> Unit)? = null

  private val myRequiredPluginsForProject: MutableMap<PluginId, Boolean> = HashMap()
  private val myUninstalled: MutableSet<PluginId> = HashSet()
  private val myPluginManagerCustomizer: PluginManagerCustomizer?

  private var myInstallSource: FUSEventSource? = null

  @ApiStatus.Internal
  fun setInstallSource(source: FUSEventSource?) {
    this.myInstallSource = source
  }

  override fun isModified(): Boolean {
    return needRestart || myInstallingInfos.isNotEmpty() || super.isModified()
  }

  /**
   * @return true if changes were applied without a restart
   */
  @Suppress("RAW_RUN_BLOCKING")
  @Deprecated("Use applyAsync() instead")
  @Throws(ConfigurationException::class)
  fun apply(parent: JComponent?): Boolean {
    return runBlocking {
      applyAsync(parent)
    }
  }

  fun applyWithCallback(parent: JComponent?, callback: Consumer<Boolean>) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      val installedWithoutRestart = applyAsync(parent)
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        callback.accept(installedWithoutRestart)
      }
    }
  }

  suspend fun applyAsync(parent: JComponent?): Boolean {
    val applyResult = withContext(Dispatchers.IO) { UiPluginManager.getInstance().applySession(mySessionId.toString(), parent, project) }
    val error = applyResult.error
    if (error != null) {
      throw ConfigurationException(XmlStringUtil.wrapInHtml(error)).withHtmlMessage()
    }
    applyResult.pluginsToEnable.forEach { id -> super.setEnabled(id, PluginEnabledState.ENABLED) }
    myUninstalled.clear()
    updateButtons()
    myPluginManagerCustomizer?.updateAfterModification { }
    return !applyResult.needRestart
  }

  fun clear(parentComponent: JComponent?) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), false,
                                               parentComponent) {
      applyChangedStates(it)
      updateAfterEnableDisable()
      null
    }
  }

  fun cancel(parentComponent: JComponent?, removeSession: Boolean) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), removeSession,
                                               parentComponent) {
      applyChangedStates(it)
      null
    }
  }

  fun pluginInstalledFromDisk(callbackData: PluginInstallCallbackData, errors: MutableList<HtmlChunk>) {
    val descriptor = callbackData.pluginDescriptor
    coroutineScope.launch {
      appendOrUpdateDescriptor(PluginUiModelAdapter(descriptor), callbackData.restartNeeded, errors)
    }
  }

  fun addComponent(component: ListPluginComponent) {
    val descriptor = component.pluginModel
    val pluginId = descriptor.pluginId
    if (!component.isMarketplace) {
      if (installingPlugins.contains(descriptor) &&
          (myInstalling == null || myInstalling!!.ui == null || myInstalling!!.ui.findComponent(pluginId) == null)
      ) {
        return
      }

      myInstalledPluginComponents.add(component)

      val components = myInstalledPluginComponentMap.computeIfAbsent(pluginId) { ArrayList<ListPluginComponent>() }
      components.add(component)
    }
    else {
      val components = myMarketplacePluginComponentMap.computeIfAbsent(pluginId) { ArrayList<ListPluginComponent>() }
      components.add(component)
    }
  }

  fun removeComponent(component: ListPluginComponent) {
    val pluginId = component.pluginDescriptor.getPluginId()
    if (!component.isMarketplace) {
      myInstalledPluginComponents.remove(component)

      val components = myInstalledPluginComponentMap[pluginId]
      if (components != null) {
        components.remove(component)
        if (components.isEmpty()) {
          myInstalledPluginComponentMap.remove(pluginId)
        }
      }
    }
    else {
      val components = myMarketplacePluginComponentMap[pluginId]
      if (components != null) {
        components.remove(component)
        if (components.isEmpty()) {
          myMarketplacePluginComponentMap.remove(pluginId)
        }
      }
    }
  }

  fun setTopController(topController: TopComponentController) {
    myTopController = topController
    topController.showProject(false)

    for (info in myInstallingInfos.values) {
      info.fromBackground(this)
    }
    if (!myInstallingInfos.isEmpty()) {
      topController.showProgress(true)
    }
  }

  var pluginUpdatesService: PluginUpdatesService
    get() = myPluginUpdatesService!!
    set(service) {
      myPluginUpdatesService = service
    }

  val sessionId: String
    get() = mySessionId.toString()

  suspend fun installOrUpdatePlugin(
    parentComponent: JComponent?,
    descriptor: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    modalityState: ModalityState,
    controller: UiPluginManagerController,
  ): InstallPluginResult? {
    return withContext(Dispatchers.EDT + modalityState.asContextElement()) {
      val actionDescriptor: PluginUiModel = updateDescriptor ?: descriptor
      if (!PluginManagerMain.checkThirdPartyPluginsAllowed(listOf(actionDescriptor.getDescriptor()))) {
        return@withContext null
      }
      val bgProgressIndicator = BgProgressIndicator()
      val projectNotNull = tryToFindProject() ?: return@withContext null
      val (installResult, info) = withContext(Dispatchers.IO) {
        withBackgroundProgress(projectNotNull, IdeBundle.message("progress.title.loading.plugin.details")) {
          jobToIndicator(coroutineContext.job, bgProgressIndicator) {
            val installPluginInfo = InstallPluginInfo(bgProgressIndicator, descriptor, this@MyPluginModel, updateDescriptor == null)
            return@jobToIndicator runBlockingCancellable {
              prepareToInstall(installPluginInfo)
              val result = controller.installOrUpdatePlugin(sessionId, projectNotNull, parentComponent, descriptor, updateDescriptor, myInstallSource, modalityState, null)
              if (result.disabledPlugins.isEmpty() || result.disabledDependants.isEmpty()) {
                return@runBlockingCancellable result
              }
              val enableDependencies = PluginManagerMain.askToEnableDependencies(1, result.disabledPlugins, result.disabledDependants)
              return@runBlockingCancellable controller.continueInstallation(sessionId, actionDescriptor.pluginId, projectNotNull, enableDependencies, result.allowInstallWithoutRestart, null, modalityState, parentComponent)
            } to installPluginInfo
          }
        }
      }
      applyInstallResult(installResult, info, actionDescriptor, controller)
    }
  }

  suspend fun applyInstallResult(result: InstallPluginResult, info: InstallPluginInfo, descriptor: PluginUiModel, controller: UiPluginManagerController): InstallPluginResult {
    val installedDescriptor = result.installedDescriptor
    if (result.success) {
      descriptor.addInstalledSource(controller.getTarget())
      if (installedDescriptor != null) {
        installedDescriptor.installSource = descriptor.installSource
        info.setInstalledModel(installedDescriptor)
      }
    }
    val changedStates = mutableMapOf<PluginId, Boolean>()
    result.pluginsToDisable.forEach { id -> changedStates[id] = false }
    result.pluginsToEnable.forEach { id -> changedStates[id] = true }
    applyChangedStates(changedStates)
    if (myPluginManagerCustomizer != null) {
      myPluginManagerCustomizer.updateAfterModificationAsync {
        info.finish(result.success, result.cancel, result.showErrors, result.restartRequired, getErrors(result))
        null
      }
    }
    else {
      info.finish(result.success, result.cancel, result.showErrors, result.restartRequired, getErrors(result))
    }
    return result
  }

  fun getErrors(result: InstallPluginResult): Map<PluginId, List<HtmlChunk>> {
    return result.errors.mapValues { getErrors(it.value) }
  }


  fun toBackground(): Boolean {
    for (info in myInstallingInfos.values) {
      info.toBackground(myStatusBar)
    }

    if (FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI) {
      return myInstallingInfos.isNotEmpty()
    }
    else {
      // FIXME(vadim.salavatov) idk what that does and it's not clear from the surrounding code :(
      val result: Boolean = !myInstallingInfos.isEmpty()
      if (result) {
        InstallPluginInfo.showRestart()
      }
      return result
    }
  }

  private fun tryToFindProject(): Project? {
    return project ?: ProjectManager.getInstance().openProjects.firstOrNull()
  }

  private fun prepareToInstall(info: InstallPluginInfo) {
    val descriptor = info.descriptor
    val pluginId = descriptor.pluginId
    myInstallingInfos[pluginId] = info

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController!!.showProgress(true)
    }
    myInstallingWithUpdatesPlugins.add(pluginId)
    if (info.install) {
      installingPlugins.add(descriptor)
    }

    if (info.install && myInstalling != null) {
      if (myInstalling!!.ui == null) {
        myInstalling!!.addModel(descriptor)
        myInstalledPanel!!.addGroup(myInstalling!!, 0)
      }
      else {
        myInstalledPanel!!.addToGroup(myInstalling!!, descriptor)
      }

      myInstalling!!.titleWithCount()
      myInstalledPanel!!.doLayout()
    }

    val gridComponents = myMarketplacePluginComponentMap[pluginId]
    if (gridComponents != null) {
      for (gridComponent in gridComponents) {
        gridComponent.showProgress()
      }
    }
    val listComponents = myInstalledPluginComponentMap[pluginId]
    if (listComponents != null) {
      for (listComponent in listComponents) {
        listComponent.showProgress()
      }
    }
    for (panel in myDetailPanels) {
      if (panel.descriptorForActions === descriptor) {
        panel.showInstallProgress()
      }
    }
  }

  /**
   * @param descriptor          Descriptor on which the installation was requested (can be a PluginNode or an IdeaPluginDescriptorImpl)
   * @param installedDescriptor If the plugin was loaded synchronously, the descriptor which has actually been installed; otherwise null.
   */
  suspend fun finishInstall(
    descriptor: PluginUiModel,
    installedDescriptor: PluginUiModel?,
    errors: Map<PluginId, List<HtmlChunk>>,
    success: Boolean,
    showErrors: Boolean,
    restartRequired: Boolean,
  ) {
    val info: InstallPluginInfo = finishInstall(descriptor)

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController!!.showProgress(false)
    }

    val pluginId = descriptor.pluginId
    val marketplaceComponents = myMarketplacePluginComponentMap[pluginId]
    val errorList = errors[pluginId] ?: emptyList()
    if (marketplaceComponents != null) {
      for (gridComponent in marketplaceComponents) {
        if (installedDescriptor != null) {
          gridComponent.pluginModel = installedDescriptor
        }
        gridComponent.hideProgress(success, restartRequired, installedDescriptor)
        if (gridComponent.myInstalledDescriptorForMarketplace != null) {
          gridComponent.updateErrors(errorList)
        }
      }
    }
    val installedComponents = myInstalledPluginComponentMap[pluginId]
    if (installedComponents != null) {
      for (listComponent in installedComponents) {
        if (installedDescriptor != null) {
          listComponent.pluginModel = installedDescriptor
        }
        listComponent.hideProgress(success, restartRequired, installedDescriptor)
        listComponent.updateErrors(errorList)
      }
    }
    for (panel in myDetailPanels) {
      if (panel.isShowingPlugin(descriptor.pluginId)) {
        panel.setPlugin(installedDescriptor)
        panel.hideProgress(success, restartRequired, installedDescriptor)
      }
    }

    val installing = myInstalling
    if (info.install) {
      if (installing != null && installing.ui != null) {
        clearInstallingProgress(descriptor)
        if (installingPlugins.isEmpty()) {
          myInstalledPanel!!.removeGroup(installing)
        }
        else {
          myInstalledPanel!!.removeFromGroup(installing, descriptor)
          installing.titleWithCount()
        }
        myInstalledPanel!!.doLayout()
      }
      if (success) {
        appendOrUpdateDescriptor(installedDescriptor ?: descriptor, restartRequired, errorList)
        appendDependsAfterInstall(success, restartRequired, errors, installedDescriptor)
        if (installedDescriptor == null && descriptor.isFromMarketplace && this.downloadedGroup != null && downloadedGroup!!.ui != null) {
          val component = downloadedGroup!!.ui.findComponent(descriptor.pluginId)
          component?.setInstalledPluginMarketplaceModel(descriptor)
        }
      }
      myCancelInstallCallback?.invoke(descriptor)
    }
    else if (success) {
      if (this.downloadedGroup != null && downloadedGroup!!.ui != null && restartRequired) {
        val component = downloadedGroup!!.ui.findComponent(pluginId)
        component?.enableRestart()
      }
    }
    else {
      myPluginUpdatesService!!.finishUpdate()
    }

    info.indicator.cancel()

    if (AccessibleAnnouncerUtil.isAnnouncingAvailable()) {
      val frame = WindowManager.getInstance().findVisibleFrame()
      val key = if (success) "plugins.configurable.plugin.installing.success" else "plugins.configurable.plugin.installing.failed"
      val message = IdeBundle.message(key, descriptor.name)
      AccessibleAnnouncerUtil.announce(frame, message, true)
    }

    if (success) {
      needRestart = needRestart or restartRequired
    }
    else if (showErrors) {
      Messages.showErrorDialog(project, IdeBundle.message("plugins.configurable.plugin.installing.failed", descriptor.name),
                               IdeBundle.message("action.download.and.install.plugin"))
    }
  }

  private fun clearInstallingProgress(descriptor: PluginUiModel) {
    if (installingPlugins.isEmpty()) {
      for (listComponent in myInstalling!!.ui.plugins) {
        listComponent.clearProgress()
      }
    }
    else {
      for (listComponent in myInstalling!!.ui.plugins) {
        if (listComponent.pluginModel === descriptor) {
          listComponent.clearProgress()
          return
        }
      }
    }
  }

  fun addEnabledGroup(group: PluginsGroup) {
    myEnabledGroups.add(group)
  }

  fun setDownloadedGroup(
    panel: PluginsGroupComponent,
    downloaded: PluginsGroup,
    installing: PluginsGroup,
  ) {
    myInstalledPanel = panel
    this.downloadedGroup = downloaded
    myInstalling = installing
  }

  private suspend fun appendDependsAfterInstall(
    success: Boolean,
    restartRequired: Boolean,
    errors: Map<PluginId, List<HtmlChunk>>,
    installedDescriptor: PluginUiModel?,
  ) {
    if (this.downloadedGroup == null || downloadedGroup!!.ui == null) {
      return
    }
    for (descriptor in InstalledPluginsState.getInstance().installedPlugins) {
      val pluginId = descriptor.getPluginId()
      if (downloadedGroup!!.ui.findComponent(pluginId) != null) {
        continue
      }

      val pluginErrors = errors[pluginId] ?: emptyList()
      appendOrUpdateDescriptor(PluginUiModelAdapter(descriptor), restartRequired, pluginErrors)

      val id = pluginId.idString

      for (entry in myMarketplacePluginComponentMap.entries) {
        if (id == entry.key.idString) {
          for (component in entry.value) {
            component.hideProgress(success, restartRequired, installedDescriptor)
          }
          break
        }
      }
    }
  }

  fun addDetailPanel(detailPanel: PluginDetailsPageComponent) {
    myDetailPanels.add(detailPanel)
  }

  private fun appendOrUpdateDescriptor(descriptor: PluginUiModel) {
    val index = view.indexOf(descriptor)
    if (index < 0) {
      view.add(descriptor)
    }
    else {
      view[index] = descriptor
    }
  }

  suspend fun appendOrUpdateDescriptor(descriptor: PluginUiModel, restartNeeded: Boolean, errors: List<HtmlChunk>) {
    val id = descriptor.pluginId
    val pluginManager = UiPluginManager.getInstance()
    if (!pluginManager.isPluginInstalled(id)) {
      appendOrUpdateDescriptor(descriptor)
      setEnabled(id, PluginEnabledState.ENABLED)
    }

    needRestart = needRestart or restartNeeded

    if (this.downloadedGroup == null) {
      return
    }

    myVendors = null
    myTags = null

    if (downloadedGroup!!.ui == null) {
      downloadedGroup!!.addModel(descriptor)
      downloadedGroup!!.titleWithEnabled(PluginModelFacade(this))

      myInstalledPanel!!.addGroup(this.downloadedGroup!!, if (myInstalling == null || myInstalling!!.ui == null) 0 else 1)
      myInstalledPanel!!.setSelection(downloadedGroup!!.ui.plugins[0])
      myInstalledPanel!!.doLayout()

      addEnabledGroup(this.downloadedGroup!!)
    }
    else {
      val component = downloadedGroup!!.ui.findComponent(id)
      if (component != null) {
        if (restartNeeded) {
          myInstalledPanel!!.setSelection(component)
          component.enableRestart()
        }
        return
      }
      downloadedGroup!!.preloadedModel.setErrors(descriptor.pluginId, errors)
      val pluginInstallationState = pluginManager.getPluginInstallationState(descriptor.pluginId)
      downloadedGroup!!.preloadedModel.setPluginInstallationState(descriptor.pluginId, pluginInstallationState)
      myInstalledPanel!!.addToGroup(this.downloadedGroup!!, descriptor)
      downloadedGroup!!.titleWithEnabled(PluginModelFacade(this))
      myInstalledPanel!!.doLayout()
    }
  }

  val vendors: SortedSet<String?>
    get() {
      if (myVendors.isNullOrEmpty()) {
        val vendorsCount = getVendorsCount(installedDescriptors)
        myVendors = TreeSet { v1, v2 ->
          val result = vendorsCount[v2]!! - vendorsCount[v1]!!
          if (result == 0) v2.compareTo(v1, ignoreCase = true) else result
        }
        myVendors!!.addAll(vendorsCount.keys)
      }
      return myVendors?.let { Collections.unmodifiableSortedSet(it) } ?: TreeSet()
    }

  val tags: SortedSet<String?>
    get() {
      if (myTags.isNullOrEmpty()) {
        myTags = TreeSet(String.CASE_INSENSITIVE_ORDER)
        val sessionId = this.sessionId

        for (descriptor in this.installedDescriptors) {
          myTags!!.addAll(descriptor.calculateTags(sessionId))
        }
      }
      return myTags?.let { Collections.unmodifiableSortedSet(it) } ?: TreeSet()
    }

  val installedDescriptors: MutableList<PluginUiModel>
    get() {
      checkNotNull(myInstalledPanel)

      return myInstalledPanel!!
        .groups
        .filterNot { it.excluded }
        .flatMap { it.plugins }
        .map { it.pluginModel }
        .toMutableList()
    }

  fun isEnabled(descriptor: IdeaPluginDescriptor): Boolean {
    return !isDisabled(descriptor.getPluginId())
  }

  fun getState(descriptor: IdeaPluginDescriptor): PluginEnabledState {
    return getState(descriptor.getPluginId())
  }

  /**
   * @see .isEnabled
   */
  fun getState(pluginId: PluginId): PluginEnabledState {
    return enabledMap[pluginId] ?: PluginEnabledState.ENABLED
  }

  fun isRequiredPluginForProject(pluginId: PluginId): Boolean {
    val project = project
    return project != null &&
           myRequiredPluginsForProject
             .computeIfAbsent(pluginId) { id ->
               getDependenciesOnPlugins(project).any { it == id.idString }
             }
  }

  fun isUninstalled(pluginId: PluginId): Boolean {
    return myUninstalled.contains(pluginId)
  }

  fun addUninstalled(pluginId: PluginId) {
    myUninstalled.add(pluginId)
  }

  override fun setEnabled(pluginId: PluginId, enabled: PluginEnabledState?) {
    super.setEnabled(pluginId, enabled)
    val isEnabled = enabled == null || enabled.isEnabled
    UiPluginManager.getInstance().setPluginStatus(mySessionId.toString(), listOf(pluginId), isEnabled)
  }

  fun setEnabledState(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
  ): Boolean {
    val pluginIds = descriptors.map { it.pluginId }
    val result =
      UiPluginManager.getInstance().enablePlugins(mySessionId.toString(), pluginIds, action.isEnable, project)
    if (result.pluginNamesToSwitch.isEmpty()) {
      applyChangedStates(result.changedStates)
      updateEnabledStateInUi()
    }
    else {
      askToUpdateDependencies(action, result.pluginNamesToSwitch, result.pluginsIdsToSwitch)
    }
    return true
  }

  fun setEnabledStateAsync(
    descriptors: Collection<IdeaPluginDescriptor>,
    action: PluginEnableDisableAction,
  ): Boolean {
    val pluginIds = descriptors.map { it.pluginId }
    PluginModelAsyncOperationsExecutor.enablePlugins(coroutineScope, mySessionId.toString(), pluginIds, action.isEnable,
                                                     project) {
      if (it.pluginNamesToSwitch.isEmpty()) {
        applyChangedStates(it.changedStates)
        updateEnabledStateInUi()
      }
      else {
        askToUpdateDependencies(action, it.pluginNamesToSwitch, it.pluginsIdsToSwitch)
      }
      null
    }
    return true
  }

  private fun askToUpdateDependencies(
    action: PluginEnableDisableAction,
    pluginNames: Set<String>,
    pluginIds: Set<PluginId>,
  ) {
    if (!createUpdateDependenciesDialog(pluginNames, action)) {
      return
    }
    val result =
      UiPluginManager.getInstance().setEnableStateForDependencies(mySessionId.toString(), pluginIds, action.isEnable)
    if (result.changedStates.isNotEmpty()) {
      applyChangedStates(result.changedStates)
      updateEnabledStateInUi()
    }
  }

  private fun createUpdateDependenciesDialog(
    dependencies: Collection<String>,
    action: PluginEnableDisableAction,
  ): Boolean {
    val size = dependencies.size
    if (size == 0) {
      return true
    }
    val hasOnlyOneDependency = size == 1

    val key = when (action) {
      PluginEnableDisableAction.ENABLE_GLOBALLY -> if (hasOnlyOneDependency) "dialog.message.enable.required.plugin" else "dialog.message.enable.required.plugins"
      PluginEnableDisableAction.DISABLE_GLOBALLY -> if (hasOnlyOneDependency) "dialog.message.disable.dependent.plugin" else "dialog.message.disable.dependent.plugins"
    }

    val dependenciesText = if (hasOnlyOneDependency) dependencies.iterator().next()
    else dependencies.joinToString("<br>") { "&nbsp;".repeat(5) + it }

    val enabled = action.isEnable
    return okCancel(IdeBundle.message(if (enabled) "dialog.title.enable.required.plugins" else "dialog.title.disable.dependent.plugins"),
                    IdeBundle.message(key, dependenciesText))
      .yesText(IdeBundle.message(if (enabled) "plugins.configurable.enable" else "plugins.configurable.disable"))
      .noText(Messages.getCancelButton())
      .ask(project)
  }


  private fun updateEnabledStateInUi() {
    updateAfterEnableDisable()
    for (group in myEnabledGroups) {
      group.titleWithEnabled(PluginModelFacade(this))
    }
    runInvalidFixCallback()
    PluginUpdatesService.reapplyFilter()
  }

  override fun isDisabled(pluginId: PluginId): Boolean {
    return !isEnabled(pluginId, enabledMap)
  }

  override fun enable(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
    return setEnabledState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY)
  }

  override fun disable(descriptors: Collection<IdeaPluginDescriptor>): Boolean {
    return setEnabledState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY)
  }

  suspend fun enableRequiredPlugins(descriptor: IdeaPluginDescriptor) {
    val pluginsToEnable = UiPluginManager.getInstance().enableRequiredPlugins(mySessionId.toString(),
                                                                              descriptor.pluginId)
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      setStatesByIds(pluginsToEnable, true)
    }
  }

  private fun runInvalidFixCallback() {
    if (myInvalidFixCallback != null) {
      ApplicationManager.getApplication().invokeLater(myInvalidFixCallback!!, ModalityState.any())
    }
  }

  fun setInvalidFixCallback(invalidFixCallback: Runnable?) {
    myInvalidFixCallback = invalidFixCallback
  }

  fun setCancelInstallCallback(callback: (PluginUiModel?) -> Unit) {
    myCancelInstallCallback = callback
  }

  private fun updateButtons() {
    PluginModelAsyncOperationsExecutor.updateButtons(coroutineScope,
                                                     myInstalledPluginComponents,
                                                     myMarketplacePluginComponentMap,
                                                     myDetailPanels)
  }

  private fun applyChangedStates(changedStates: Map<PluginId, Boolean>) {
    changedStates.forEach { (pluginId: PluginId, enabled: Boolean) ->
      super.setEnabled(pluginId, if (enabled) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED)
    }
  }

  open fun runRestartButton(component: Component) {
    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(component).asContextElement()) {
      if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
        needRestart = true
        createShutdownCallback = false

        val settings = DialogWrapper.findInstance(component)
        if (settings is SettingsDialog) {
          settings.applyAndClose(false /* will be saved on app exit */)
        }
        else if (isModified()) {
          try {
            applyAsync(null)
          }
          catch (e: ConfigurationException) {
            LOG.error(e)
          }
        }
        ApplicationManagerEx.getApplicationEx().restart(true)
      }
    }
  }

  suspend fun uninstallAndUpdateUi(descriptor: PluginUiModel) {
    uninstallAndUpdateUi(descriptor, UiPluginManager.getInstance().getController())
  }

  @ApiStatus.Internal
  suspend fun uninstallAndUpdateUi(descriptor: PluginUiModel, controller: UiPluginManagerController) {
    uninstallAndUpdateUi(descriptor, controller, null)
  }

  @ApiStatus.Internal
  suspend fun uninstallAndUpdateUi(
    descriptor: PluginUiModel,
    controller: UiPluginManagerController,
    callback: Runnable?,
  ) {
    val scope = coroutineScope.childScope(javaClass.name, Dispatchers.IO, true)
    myTopController!!.showProgress(true)
    for (panel in myDetailPanels) {
      if (panel.descriptorForActions === descriptor) {
        panel.showUninstallProgress(scope)
      }
    }
    try {
      val needRestartForUninstall = controller.performUninstall(sessionId, descriptor.pluginId)
      descriptor.isDeleted = true
      PluginManagerCustomizer.getInstance()?.onPluginDeleted(descriptor, controller.getTarget())
      val errorCheckResult = UiPluginManager.getInstance().loadErrors(sessionId)
      needRestart = needRestart or (descriptor.isEnabled && needRestartForUninstall)
      val errors = getErrors(errorCheckResult)
      if (myPluginManagerCustomizer != null) {
        myPluginManagerCustomizer.updateAfterModificationAsync {
          updateUiAfterUninstall(descriptor, needRestartForUninstall, errors)
          callback?.run()
        }
      }
      else {
        updateUiAfterUninstall(descriptor, needRestartForUninstall, errors)
        callback?.run()
      }
    }
    finally {
      for (panel in myDetailPanels) {
        if (panel.descriptorForActions === descriptor) {
          panel.hideProgress()
        }
      }
    }
  }

  private suspend fun updateUiAfterUninstall(
    descriptor: PluginUiModel, needRestartForUninstall: Boolean,
    errors: Map<PluginId, List<HtmlChunk>>,
  ) {
    val pluginId = descriptor.pluginId
    myTopController!!.showProgress(false)
    val installationState = withContext(Dispatchers.IO) { UiPluginManager.getInstance().getPluginInstallationState(pluginId) }
    val listComponents = myInstalledPluginComponentMap[pluginId]
    if (listComponents != null) {
      for (listComponent in listComponents) {
        listComponent.updateAfterUninstall(needRestartForUninstall, installationState)
      }
    }

    val marketplaceComponents = myMarketplacePluginComponentMap[pluginId]
    if (marketplaceComponents != null) {
      for (component in marketplaceComponents) {
        if (component.myInstalledDescriptorForMarketplace != null) {
          component.updateAfterUninstall(needRestartForUninstall, installationState)
        }
      }
    }
    for (component in myInstalledPluginComponents) {
      component.updateErrors(errors[component.pluginModel.pluginId] ?: emptyList())
    }
    for (plugins in myMarketplacePluginComponentMap.values) {
      for (plugin in plugins) {
        if (plugin.myInstalledDescriptorForMarketplace != null) {
          plugin.updateErrors(errors[plugin.pluginModel.pluginId] ?: emptyList())
        }
      }
    }

    for (panel in myDetailPanels) {
      if (panel.descriptorForActions === descriptor) {
        panel.updateAfterUninstall(needRestartForUninstall)
      }
    }
  }

  suspend fun hasErrors(descriptor: IdeaPluginDescriptor): Boolean {
    return getErrors(descriptor).isNotEmpty()
  }

  suspend fun getErrors(descriptor: IdeaPluginDescriptor): List<HtmlChunk> {
    val pluginId = descriptor.getPluginId()
    if (isDeleted(descriptor)) {
      return emptyList()
    }
    val response = UiPluginManager.getInstance().getErrors(mySessionId.toString(), pluginId)
    return getErrors(response)
  }

  fun getErrorsSync(descriptor: IdeaPluginDescriptor): List<HtmlChunk> {
    val pluginId = descriptor.getPluginId()
    if (isDeleted(descriptor)) {
      return emptyList()
    }
    val response = UiPluginManager.getInstance().getErrorsSync(mySessionId.toString(), pluginId)
    return getErrors(response)
  }

  protected open val customRepoPlugins: MutableCollection<PluginUiModel?>
    get() = CustomPluginRepositoryService.getInstance().getCustomRepositoryPlugins()

  private val myIcons: MutableMap<String?, Icon?> = HashMap<String?, Icon?>() // local cache for PluginLogo WeakValueMap

  init {
    val window = getActiveFrameOrWelcomeScreen()
    val statusBar: StatusBarEx? = getStatusBar(window)
    myStatusBar = if (statusBar != null || window == null) statusBar else getStatusBar(window.owner)
    myPluginManagerCustomizer = PluginManagerCustomizer.getInstance()
  }

  fun getIcon(descriptor: IdeaPluginDescriptor, big: Boolean, error: Boolean, disabled: Boolean): Icon {
    val key = descriptor.getPluginId().idString + big + error + disabled
    var icon = myIcons[key]
    if (icon == null) {
      icon = PluginLogo.getIcon(descriptor, big, error, disabled)
      if (icon !== getDefault().getIcon(big, error, disabled)) {
        myIcons[key] = icon
      }
    }
    return icon
  }

  companion object {
    private val LOG = Logger.getInstance(MyPluginModel::class.java)
    val FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI: Boolean = SystemProperties.getBooleanProperty(
      "plugins.finish-dynamic-plugin-installation-without-ui", true)

    @JvmStatic
    val installingPlugins: MutableSet<PluginUiModel> = mutableSetOf()
    private val myInstallingWithUpdatesPlugins: MutableSet<PluginId?> = HashSet<PluginId?>()

    @JvmField
    internal val myInstallingInfos: MutableMap<PluginId, InstallPluginInfo> = mutableMapOf()

    private fun getStatusBar(frame: Window?): StatusBarEx? {
      return if (frame is IdeFrame && frame !is WelcomeFrame) (frame as IdeFrame).getStatusBar() as StatusBarEx? else null
    }

    fun isInstallingOrUpdate(pluginId: PluginId?): Boolean {
      return myInstallingWithUpdatesPlugins.contains(pluginId)
    }

    @JvmStatic
    private fun finishInstall(descriptor: PluginUiModel): InstallPluginInfo {
      val info = myInstallingInfos.remove(descriptor.pluginId)!!
      info.close()
      myInstallingWithUpdatesPlugins.remove(descriptor.pluginId)
      if (info.install) {
        installingPlugins.remove(descriptor)
      }
      return info
    }

    //overload to avoid exposing InstallPluginInfo and to allow Java code to use it
    @JvmStatic
    fun finishInstallation(descriptor: PluginUiModel) {
      finishInstall(descriptor)
    }

    fun addProgress(descriptor: IdeaPluginDescriptor, indicator: ProgressIndicatorEx) {
      val info = myInstallingInfos[descriptor.pluginId]
      if (info == null) return
      info.indicator.addStateDelegate(indicator)
    }

    fun removeProgress(descriptor: IdeaPluginDescriptor, indicator: ProgressIndicatorEx) {
      val info = myInstallingInfos[descriptor.pluginId]
      if (info == null) return
      info.indicator.removeStateDelegate(indicator)
    }

    private fun getVendorsCount(descriptors: Collection<PluginUiModel>): Map<String, Int> {
      val vendors = mutableMapOf<String, Int>()

      for (descriptor in descriptors) {
        val vendor = StringUtil.trim(descriptor.vendor)
        if (!vendor.isNullOrBlank()) {
          vendors[vendor] = (vendors[vendor] ?: 0) + 1
        }
      }

      return vendors
    }

    @JvmStatic
    fun isVendor(descriptor: PluginUiModel, vendors: Set<String>): Boolean {
      val vendor = StringUtil.trim(descriptor.vendor)
      if (StringUtil.isEmpty(vendor)) {
        return false
      }

      for (vendorToFind in vendors) {
        if (vendor.equals(vendorToFind, ignoreCase = true) || StringUtil.containsIgnoreCase(vendor!!, vendorToFind)) {
          return true
        }
      }

      return false
    }

    fun getErrors(errorCheckResults: Map<PluginId, CheckErrorsResult>): Map<PluginId, List<HtmlChunk>> {
      return errorCheckResults.mapValues { (_, checkResult) -> getErrors(checkResult) }
    }

    fun getErrors(checkErrorsResult: CheckErrorsResult): List<HtmlChunk> {
      if (checkErrorsResult.isDisabledDependencyError) {
        val loadingError = checkErrorsResult.loadingError
        return if (loadingError != null) listOf<HtmlChunk>(createTextChunk(loadingError)) else emptyList()
      }

      val errors = mutableListOf<HtmlChunk>()

      val requiredPluginNames = checkErrorsResult.requiredPluginNames
      if (requiredPluginNames.isEmpty()) {
        return errors
      }
      val message = IdeBundle.message("new.plugin.manager.incompatible.deps.tooltip",
                                      requiredPluginNames.size,
                                      joinPluginNamesOrIds(requiredPluginNames))
      errors.add(createTextChunk(message))

      if (checkErrorsResult.suggestToEnableRequiredPlugins) {
        val action = IdeBundle.message("new.plugin.manager.incompatible.deps.action", requiredPluginNames.size)
        errors.add(HtmlChunk.link("link", action))
      }

      return errors.toList()
    }

    @JvmStatic
    fun getPluginNames(descriptors: Collection<IdeaPluginDescriptor?>): Set<String> {
      return descriptors.mapNotNull { it?.name }.toSet()
    }

    @JvmStatic
    fun joinPluginNamesOrIds(pluginNames: Set<String?>): String {
      return pluginNames.filterNotNull().joinToString(", ") { StringUtil.wrapWithDoubleQuote(it) }
    }

    private fun getDependenciesOnPlugins(project: Project): List<String> {
      return ExternalDependenciesManager.getInstance(project)
        .getDependencies(DependencyOnPlugin::class.java)
        .map { it.pluginId }
    }

    private fun createTextChunk(message: @Nls String): HtmlChunk.Element {
      return HtmlChunk.span().addText(message)
    }
  }
}
