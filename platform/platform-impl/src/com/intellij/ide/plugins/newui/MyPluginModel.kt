// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.externalDependencies.DependencyOnPlugin
import com.intellij.externalDependencies.ExternalDependenciesManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil.getActiveFrameOrWelcomeScreen
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.api.PluginDto.Companion.fromModel
import com.intellij.ide.plugins.marketplace.CheckErrorsResult
import com.intellij.ide.plugins.marketplace.InstallPluginResult
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.SetEnabledStateResult
import com.intellij.ide.plugins.newui.PluginInstallationCustomization.Companion.findPluginInstallationCustomization
import com.intellij.ide.plugins.newui.PluginLogo.getDefault
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable.TopComponentController
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.newEditor.SettingsDialog
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Unmodifiable
import java.awt.Component
import java.awt.Window
import java.util.*
import java.util.List
import java.util.Map
import java.util.function.Consumer
import java.util.function.Function
import java.util.stream.Collectors
import javax.swing.Icon
import javax.swing.JComponent

@ApiStatus.Internal
open class MyPluginModel(project: Project?) : InstalledPluginsTableModel(project), PluginEnabler {
  private var myInstalledPanel: PluginsGroupComponent? = null
  var downloadedGroup: PluginsGroup? = null
    private set
  private var myInstalling: PluginsGroup? = null
  private var myTopController: TopComponentController? = null
  private var myVendors: SortedSet<String?>? = null
  private var myTags: SortedSet<String?>? = null

  var needRestart: Boolean = false
  @JvmField
  var createShutdownCallback: Boolean = true

  private val myStatusBar: StatusBarEx?

  private var myPluginUpdatesService: PluginUpdatesService? = null

  private var myInvalidFixCallback: Runnable? = null
  private var myCancelInstallCallback: Consumer<PluginUiModel?>? = null

  private val myRequiredPluginsForProject: MutableMap<PluginId?, Boolean?> = HashMap<PluginId?, Boolean?>()
  private val myUninstalled: MutableSet<PluginId?> = HashSet<PluginId?>()
  private val myPluginManagerCustomizer: PluginManagerCustomizer?

  private var myInstallSource: FUSEventSource? = null

  @ApiStatus.Internal
  fun setInstallSource(source: FUSEventSource?) {
    this.myInstallSource = source
  }

  public override fun isModified(): Boolean {
    return needRestart || !myInstallingInfos.isEmpty() || super.isModified()
  }

  /**
   * @return true if changes were applied without a restart
   */
  @Throws(ConfigurationException::class)
  fun apply(parent: JComponent?): Boolean {
    val applyResult = UiPluginManager.getInstance().applySession(mySessionId.toString(), parent, project)
    val error = applyResult.error
    if (error != null) {
      throw ConfigurationException(XmlStringUtil.wrapInHtml(error)).withHtmlMessage()
    }
    applyResult.pluginsToEnable.forEach(Consumer { id: PluginId? -> super.setEnabled(id!!, PluginEnabledState.ENABLED) })
    myUninstalled.clear()
    updateButtons()
    return !applyResult.needRestart
  }

  fun clear(parentComponent: JComponent?) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), false,
                                               parentComponent) { newState: MutableMap<PluginId?, Boolean?>? ->
      applyChangedStates(newState!!)
      updateAfterEnableDisable()
      null
    }
  }

  fun cancel(parentComponent: JComponent?, removeSession: Boolean) {
    UiPluginManager.getInstance().resetSession(mySessionId.toString(), removeSession,
                                               parentComponent) { newState: MutableMap<PluginId?, Boolean?>? ->
      applyChangedStates(newState!!)
      null
    }
  }

  fun pluginInstalledFromDisk(callbackData: PluginInstallCallbackData, errors: MutableList<HtmlChunk?>?) {
    val descriptor = callbackData.pluginDescriptor
    appendOrUpdateDescriptor(PluginUiModelAdapter(descriptor), callbackData.restartNeeded, errors)
  }

  fun addComponent(component: ListPluginComponent) {
    val descriptor = component.getPluginModel()
    val pluginId = descriptor.pluginId
    if (!component.isMarketplace()) {
      if (installingPlugins.contains(descriptor) &&
          (myInstalling == null || myInstalling!!.ui == null || myInstalling!!.ui.findComponent(pluginId) == null)
      ) {
        return
      }

      myInstalledPluginComponents.add(component)

      val components: MutableList<ListPluginComponent?> =
        myInstalledPluginComponentMap.computeIfAbsent(pluginId) { `__`: PluginId? -> ArrayList<ListPluginComponent?>() }
      components.add(component)
    }
    else {
      val components: MutableList<ListPluginComponent?> =
        myMarketplacePluginComponentMap.computeIfAbsent(pluginId) { `__`: PluginId? -> ArrayList<ListPluginComponent?>() }
      components.add(component)
    }
  }

  fun removeComponent(component: ListPluginComponent) {
    val pluginId = component.getPluginDescriptor().getPluginId()
    if (!component.isMarketplace()) {
      myInstalledPluginComponents.remove(component)

      val components: MutableList<ListPluginComponent?>? = myInstalledPluginComponentMap.get(pluginId)
      if (components != null) {
        components.remove(component)
        if (components.isEmpty()) {
          myInstalledPluginComponentMap.remove(pluginId)
        }
      }
    }
    else {
      val components: MutableList<ListPluginComponent?>? = myMarketplacePluginComponentMap.get(pluginId)
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
    myTopController!!.showProject(false)

    for (info in myInstallingInfos.values) {
      info.fromBackground(this)
    }
    if (!myInstallingInfos.isEmpty()) {
      myTopController!!.showProgress(true)
    }
  }

  var pluginUpdatesService: PluginUpdatesService
    get() = myPluginUpdatesService!!
    set(service) {
      myPluginUpdatesService = service
    }

  val sessionId: String
    get() = mySessionId.toString()

  fun installOrUpdatePlugin(
    parentComponent: JComponent?,
    descriptor: PluginUiModel,
    updateDescriptor: PluginUiModel?,
    modalityState: ModalityState,
    controller: UiPluginManagerController,
    callback: Consumer<Boolean?>
  ) {
    val isUpdate = updateDescriptor != null
    val actionDescriptor: PluginUiModel = if (isUpdate) updateDescriptor else descriptor
    if (!PluginManagerMain.checkThirdPartyPluginsAllowed(List.of<IdeaPluginDescriptor?>(actionDescriptor.getDescriptor()))) {
      return
    }

    val customization = findPluginInstallationCustomization(descriptor.pluginId)
    if (customization != null) {
      customization.beforeInstallOrUpdate(isUpdate)
    }

    if (myInstallSource != null) {
      val pluginId = descriptor.pluginId.idString
      myInstallSource!!.logInstallPlugins(mutableListOf<String>(pluginId))
    }

    if (descriptor.isFromMarketplace) {
      val installSource = descriptor.installSource
      if (installSource != null) {
        installSource.logInstallPlugins(List.of<String>(descriptor.pluginId.idString))
      }
    }

    val allowInstallWithoutRestart = Ref.create<Boolean?>(true)
    val uninstallPlugin = Ref.create<Boolean?>(false)
    if (isUpdate) {
      if (descriptor.isBundled) {
        allowInstallWithoutRestart.set(false)
      }
      else if (!controller.allowLoadUnloadWithoutRestart(descriptor.pluginId)) {
        allowInstallWithoutRestart.set(false)
      }
      else if (!descriptor.isEnabled) {
        controller.deletePluginFiles(descriptor.pluginId)
      }
      else if (controller.allowLoadUnloadSynchronously(descriptor.pluginId)) {
        allowInstallWithoutRestart.set(controller.uninstallDynamicPlugin(parentComponent,
                                                                         mySessionId.toString(),
                                                                         descriptor.pluginId,
                                                                         true))
      }
      else {
        uninstallPlugin.set(true)
      }
    }

    ProgressManager.getInstance()
      .runProcessWithProgressAsynchronously(object : Task.Backgroundable(project,
                                                                         parentComponent,
                                                                         IdeBundle.message("progress.title.loading.plugin.details"),
                                                                         true,
                                                                         null) {
        override fun run(indicator: ProgressIndicator) {
          if (uninstallPlugin.get()) {
            controller.performUninstall(mySessionId.toString(), descriptor.pluginId)
          }
          val pluginUiModel = loadDetails(actionDescriptor, indicator)
          if (pluginUiModel == null) {
            return
          }

          val pluginsToInstall = List.of<IdeaPluginDescriptor?>(pluginUiModel.getDescriptor())
          ApplicationManager.getApplication().invokeAndWait(
            Runnable {
              PluginManagerMain.suggestToEnableInstalledDependantPlugins(this@MyPluginModel, pluginsToInstall, updateDescriptor != null)
            }, modalityState)


          val info = InstallPluginInfo(indicator as BgProgressIndicator,
                                       descriptor,
                                       this@MyPluginModel,
                                       !isUpdate)
          prepareToInstall(info)
          val installPluginRequest = InstallPluginRequest(mySessionId.toString(),
                                                          descriptor.pluginId,
                                                          List.of<PluginDto>(fromModel(pluginUiModel)),
                                                          allowInstallWithoutRestart.get()!!,
                                                          FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI,
                                                          needRestart)

          controller.performInstallOperation(installPluginRequest,
                                             parentComponent,
                                             modalityState,
                                             indicator,
                                             this@MyPluginModel
          ) { result: InstallPluginResult? ->
            applyInstallResult(result!!, info, callback)
            null
          }
        }

        fun applyInstallResult(result: InstallPluginResult, info: InstallPluginInfo, callback: Consumer<Boolean?>) {
          val installedDescriptor = result.installedDescriptor
          if (result.success) {
            descriptor.addInstalledSource(controller.getTarget())
            if (installedDescriptor != null) {
              installedDescriptor.installSource = descriptor.installSource
              info.setInstalledModel(installedDescriptor)
            }
          }
          disableById(result.pluginsToDisable)
          if (myPluginManagerCustomizer != null) {
            myPluginManagerCustomizer.updateAfterModification {
              info.finish(result.success, result.cancel, result.showErrors, result.restartRequired, getErrors(result))
              callback.accept(result.success)
              null
            }
          }
          else {
            info.finish(result.success, result.cancel, result.showErrors, result.restartRequired, getErrors(result))
            callback.accept(result.success)
          }
        }

        fun getErrors(result: InstallPluginResult): MutableMap<PluginId?, MutableList<HtmlChunk?>?> {
          return Companion.getErrors(result.errors)
        }


        fun loadDetails(descriptor: PluginUiModel, indicator: ProgressIndicator): PluginUiModel? {
          if (descriptor.isFromMarketplace) {
            if (descriptor.detailsLoaded) {
              return descriptor
            }
            else {
              val model = MarketplaceRequests.getInstance().loadPluginDetails(descriptor, indicator)
              if (model != null) {
                return model
              }
              return null
            }
          }
          else {
            val builder = PluginUiModelBuilderFactory.getInstance().createBuilder(descriptor.pluginId)
            builder.setName(descriptor.name)
            builder.setDependencies(descriptor.dependencies)
            builder.setRepositoryName(PluginInstaller.UNKNOWN_HOST_MARKER)
            builder.setDisableAllowed(descriptor.isDisableAllowed())
            return builder.build()
          }
        }
      }, BgProgressIndicator())
  }


  fun toBackground(): Boolean {
    for (info in myInstallingInfos.values) {
      info.toBackground(myStatusBar)
    }

    if (FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI) {
      return !myInstallingInfos.isEmpty()
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

  private fun prepareToInstall(info: InstallPluginInfo) {
    val descriptor = info.getDescriptor()
    val pluginId = descriptor.pluginId
    myInstallingInfos.put(pluginId, info)

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

    val gridComponents = myMarketplacePluginComponentMap.get(pluginId)
    if (gridComponents != null) {
      for (gridComponent in gridComponents) {
        gridComponent.showProgress()
      }
    }
    val listComponents = myInstalledPluginComponentMap.get(pluginId)
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
  fun finishInstall(
    descriptor: PluginUiModel,
    installedDescriptor: PluginUiModel?,
    errors: MutableMap<PluginId?, MutableList<HtmlChunk?>>, success: Boolean,
    showErrors: Boolean,
    restartRequired: Boolean
  ) {
    val info: InstallPluginInfo = finishInstall(descriptor)

    if (myInstallingWithUpdatesPlugins.isEmpty()) {
      myTopController!!.showProgress(false)
    }

    val pluginId = descriptor.pluginId
    val marketplaceComponents = myMarketplacePluginComponentMap.get(pluginId)
    val errorList = errors.getOrDefault(pluginId, mutableListOf<HtmlChunk?>())
    if (marketplaceComponents != null) {
      for (gridComponent in marketplaceComponents) {
        if (installedDescriptor != null) {
          gridComponent.setPluginModel(installedDescriptor)
        }
        gridComponent.hideProgress(success, restartRequired, installedDescriptor)
        if (gridComponent.myInstalledDescriptorForMarketplace != null) {
          gridComponent.updateErrors(errorList)
        }
      }
    }
    val installedComponents = myInstalledPluginComponentMap.get(pluginId)
    if (installedComponents != null) {
      for (listComponent in installedComponents) {
        if (installedDescriptor != null) {
          listComponent.setPluginModel(installedDescriptor)
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

    if (info.install) {
      if (myInstalling != null && myInstalling!!.ui != null) {
        clearInstallingProgress(descriptor)
        if (installingPlugins.isEmpty()) {
          myInstalledPanel!!.removeGroup(myInstalling!!)
        }
        else {
          myInstalledPanel!!.removeFromGroup(myInstalling!!, descriptor)
          myInstalling!!.titleWithCount()
        }
        myInstalledPanel!!.doLayout()
      }
      if (success) {
        appendOrUpdateDescriptor(if (installedDescriptor != null) installedDescriptor else descriptor, restartRequired, errorList)
        appendDependsAfterInstall(success, restartRequired, errors, installedDescriptor)
        if (installedDescriptor == null && descriptor.isFromMarketplace && this.downloadedGroup != null && downloadedGroup!!.ui != null) {
          val component = downloadedGroup!!.ui.findComponent(descriptor.pluginId)
          if (component != null) {
            component.setInstalledPluginMarketplaceModel(descriptor)
          }
        }
      }
      else if (myCancelInstallCallback != null) {
        myCancelInstallCallback!!.accept(descriptor)
      }
    }
    else if (success) {
      if (this.downloadedGroup != null && downloadedGroup!!.ui != null && restartRequired) {
        val component = downloadedGroup!!.ui.findComponent(pluginId)
        if (component != null) {
          component.enableRestart()
        }
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
        if (listComponent.getPluginModel() === descriptor) {
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
    installing: PluginsGroup
  ) {
    myInstalledPanel = panel
    this.downloadedGroup = downloaded
    myInstalling = installing
  }

  private fun appendDependsAfterInstall(
    success: Boolean,
    restartRequired: Boolean,
    errors: MutableMap<PluginId?, MutableList<HtmlChunk?>>,
    installedDescriptor: PluginUiModel?
  ) {
    if (this.downloadedGroup == null || downloadedGroup!!.ui == null) {
      return
    }
    for (descriptor in InstalledPluginsState.getInstance().getInstalledPlugins()) {
      val pluginId = descriptor.getPluginId()
      if (downloadedGroup!!.ui.findComponent(pluginId) != null) {
        continue
      }

      appendOrUpdateDescriptor(PluginUiModelAdapter(descriptor), restartRequired, errors.get(pluginId))

      val id = pluginId.idString

      for (entry in myMarketplacePluginComponentMap.entries) {
        if (id == entry.key!!.idString) {
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
      view.set(index, descriptor)
    }
  }

  fun appendOrUpdateDescriptor(descriptor: PluginUiModel, restartNeeded: Boolean, errors: MutableList<HtmlChunk?>?) {
    val id = descriptor.pluginId
    if (!UiPluginManager.getInstance().isPluginInstalled(id)) {
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
      myInstalledPanel!!.setSelection(downloadedGroup!!.ui.plugins.get(0))
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
      downloadedGroup!!.setErrors(descriptor, errors)
      myInstalledPanel!!.addToGroup(this.downloadedGroup!!, descriptor)
      downloadedGroup!!.titleWithEnabled(PluginModelFacade(this))
      myInstalledPanel!!.doLayout()
    }
  }

  val vendors: SortedSet<String?>
    get() {
      if (ContainerUtil.isEmpty<String?>(myVendors)) {
        val vendorsCount: MutableMap<String?, Int?> = getVendorsCount(
          this.installedDescriptors)
        myVendors = TreeSet<String?>(Comparator { v1: String?, v2: String? ->
          val result = vendorsCount.get(v2)!! - vendorsCount.get(v1)!!
          if (result == 0) v2!!.compareTo(v1!!, ignoreCase = true) else result
        })
        myVendors!!.addAll(vendorsCount.keys)
      }
      return Collections.unmodifiableSortedSet<String?>(myVendors)
    }

  val tags: SortedSet<String?>
    get() {
      if (ContainerUtil.isEmpty<String?>(myTags)) {
        myTags = TreeSet<String?>(
          Comparator { obj: String?, str: String? -> obj!!.compareTo(str!!, ignoreCase = true) })
        val sessionId = this.sessionId

        for (descriptor in this.installedDescriptors) {
          myTags!!.addAll(descriptor.calculateTags(sessionId))
        }
      }
      return Collections.unmodifiableSortedSet<String?>(myTags)
    }

  val installedDescriptors: MutableList<PluginUiModel>
    get() {
      checkNotNull(myInstalledPanel)

      return myInstalledPanel!!
        .getGroups()
        .stream()
        .filter { group: UIPluginGroup? -> !group!!.excluded }
        .flatMap<ListPluginComponent?> { group: UIPluginGroup? -> group!!.plugins.stream() }
        .map<PluginUiModel?> { obj: ListPluginComponent? -> obj!!.getPluginModel() }
        .collect(Collectors.toList())
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
    val state = enabledMap.get(pluginId)
    return if (state != null) state else PluginEnabledState.ENABLED
  }

  fun isRequiredPluginForProject(pluginId: PluginId): Boolean {
    val project = project
    return project != null &&
           myRequiredPluginsForProject
             .computeIfAbsent(pluginId
             ) { id: PluginId? ->
               ContainerUtil.exists<String?>(getDependenciesOnPlugins(project),
                                             Condition { anObject: String? -> id!!.idString.equals(anObject) })
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
    val isEnabled = enabled == null || enabled.isEnabled()
    UiPluginManager.getInstance().setPluginStatus(mySessionId.toString(), List.of<PluginId>(pluginId), isEnabled)
  }

  fun setEnabledState(
    descriptors: MutableCollection<out IdeaPluginDescriptor?>,
    action: PluginEnableDisableAction
  ): Boolean {
    val pluginIds = ContainerUtil.map(descriptors, { it: IdeaPluginDescriptor? -> it!!.getPluginId() })
    val result =
      UiPluginManager.getInstance().enablePlugins(mySessionId.toString(), pluginIds, action.isEnable(), project)
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
    descriptors: MutableCollection<out IdeaPluginDescriptor?>,
    action: PluginEnableDisableAction
  ): Boolean {
    val pluginIds = ContainerUtil.map(descriptors, { it: IdeaPluginDescriptor? -> it!!.getPluginId() })
    PluginModelAsyncOperationsExecutor.enablePlugins(coroutineScope, mySessionId.toString(), pluginIds, action.isEnable(),
                                                     project) { result: SetEnabledStateResult? ->
      if (result!!.pluginNamesToSwitch.isEmpty()) {
        applyChangedStates(result.changedStates)
        updateEnabledStateInUi()
      }
      else {
        askToUpdateDependencies(action, result.pluginNamesToSwitch, result.pluginsIdsToSwitch)
      }
      null
    }
    return true
  }

  private fun askToUpdateDependencies(
    action: PluginEnableDisableAction,
    pluginNames: MutableSet<String>,
    pluginIds: MutableSet<PluginId?>
  ) {
    if (!createUpdateDependenciesDialog(pluginNames, action)) {
      return
    }
    val result =
      UiPluginManager.getInstance().setEnableStateForDependencies(mySessionId.toString(), pluginIds, action.isEnable())
    if (!result.changedStates.isEmpty()) {
      applyChangedStates(result.changedStates)
      updateEnabledStateInUi()
    }
  }

  private fun createUpdateDependenciesDialog(
    dependencies: MutableCollection<String>,
    action: PluginEnableDisableAction
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
    else dependencies.stream()
      .map<String?> { str: String? -> "&nbsp;".repeat(5) + str }
      .collect(Collectors.joining("<br>"))

    val enabled = action.isEnable()
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

  override fun enable(descriptors: MutableCollection<out IdeaPluginDescriptor?>): Boolean {
    return setEnabledState(descriptors, PluginEnableDisableAction.ENABLE_GLOBALLY)
  }

  override fun disable(descriptors: MutableCollection<out IdeaPluginDescriptor?>): Boolean {
    return setEnabledState(descriptors, PluginEnableDisableAction.DISABLE_GLOBALLY)
  }

  fun enableRequiredPlugins(descriptor: IdeaPluginDescriptor) {
    val pluginsToEnable: MutableSet<PluginId?> = UiPluginManager.getInstance().enableRequiredPlugins(mySessionId.toString(),
                                                                                                     descriptor.getPluginId())
    setStatesByIds(pluginsToEnable, true)
  }

  private fun runInvalidFixCallback() {
    if (myInvalidFixCallback != null) {
      ApplicationManager.getApplication().invokeLater(myInvalidFixCallback!!, ModalityState.any())
    }
  }

  fun setInvalidFixCallback(invalidFixCallback: Runnable?) {
    myInvalidFixCallback = invalidFixCallback
  }

  fun setCancelInstallCallback(callback: Consumer<PluginUiModel?>) {
    myCancelInstallCallback = callback
  }

  private fun updateButtons() {
    PluginModelAsyncOperationsExecutor.updateButtons(coroutineScope,
                                                     myInstalledPluginComponents,
                                                     myMarketplacePluginComponentMap,
                                                     myDetailPanels)
  }

  private fun applyChangedStates(changedStates: MutableMap<PluginId?, Boolean?>) {
    changedStates.forEach { (pluginId: PluginId?, enabled: Boolean?) ->
      super.setEnabled(pluginId!!, if (enabled) PluginEnabledState.ENABLED else PluginEnabledState.DISABLED)
    }
  }

  open fun runRestartButton(component: Component) {
    if (PluginManagerConfigurable.showRestartDialog() == Messages.YES) {
      needRestart = true
      createShutdownCallback = false

      val settings = DialogWrapper.findInstance(component)
      if (settings is SettingsDialog) {
        settings.applyAndClose(false /* will be saved on app exit */)
      }
      else if (isModified()) {
        try {
          apply(null)
        }
        catch (e: ConfigurationException) {
          LOG.error(e)
        }
      }
      ApplicationManagerEx.getApplicationEx().restart(true)
    }
  }

  fun uninstallAndUpdateUi(descriptor: PluginUiModel) {
    uninstallAndUpdateUi(descriptor, UiPluginManager.getInstance().getController())
  }

  @ApiStatus.Internal
  fun uninstallAndUpdateUi(descriptor: PluginUiModel, controller: UiPluginManagerController) {
    uninstallAndUpdateUi(descriptor, controller, null)
  }

  @ApiStatus.Internal
  fun uninstallAndUpdateUi(
    descriptor: PluginUiModel,
    controller: UiPluginManagerController,
    callback: Runnable?
  ) {
    val scope = coroutineScope.childScope(javaClass.getName(), getIO.getIO(), true)
    myTopController!!.showProgress(true)
    for (panel in myDetailPanels) {
      if (panel.descriptorForActions === descriptor) {
        panel.showUninstallProgress(scope)
      }
    }
    try {
      PluginModelAsyncOperationsExecutor
        .performUninstall(scope, descriptor, mySessionId.toString(),
                          controller) { needRestartForUninstall: Boolean?, errorCheckResult: MutableMap<PluginId?, CheckErrorsResult?>? ->
          needRestart = needRestart or (descriptor.isEnabled && needRestartForUninstall)
          val errors: MutableMap<PluginId?, MutableList<HtmlChunk?>?> = Companion.getErrors(errorCheckResult!!)
          if (myPluginManagerCustomizer != null) {
            myPluginManagerCustomizer.updateAfterModification {
              updateUiAfterUninstall(descriptor, needRestartForUninstall!!, errors)
              callback!!.run()
              null
            }
          }
          else {
            updateUiAfterUninstall(descriptor, needRestartForUninstall!!, errors)
            callback!!.run()
          }
          null
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

  private fun updateUiAfterUninstall(
    descriptor: PluginUiModel, needRestartForUninstall: Boolean,
    errors: MutableMap<PluginId?, MutableList<HtmlChunk?>?>
  ) {
    val pluginId = descriptor.pluginId
    myTopController!!.showProgress(false)
    val listComponents = myInstalledPluginComponentMap.get(pluginId)
    if (listComponents != null) {
      for (listComponent in listComponents) {
        listComponent.updateAfterUninstall(needRestartForUninstall)
      }
    }

    val marketplaceComponents = myMarketplacePluginComponentMap.get(pluginId)
    if (marketplaceComponents != null) {
      for (component in marketplaceComponents) {
        if (component.myInstalledDescriptorForMarketplace != null) {
          component.updateAfterUninstall(needRestartForUninstall)
        }
      }
    }
    for (component in myInstalledPluginComponents) {
      component.updateErrors(errors.getOrDefault(component.getPluginModel().pluginId, mutableListOf<HtmlChunk?>()))
    }
    for (plugins in myMarketplacePluginComponentMap.values) {
      for (plugin in plugins) {
        if (plugin.myInstalledDescriptorForMarketplace != null) {
          plugin.updateErrors(errors.get(plugin.getPluginModel().pluginId))
        }
      }
    }

    for (panel in myDetailPanels) {
      if (panel.descriptorForActions === descriptor) {
        panel.updateAfterUninstall(needRestartForUninstall)
      }
    }
  }

  fun hasErrors(descriptor: IdeaPluginDescriptor): Boolean {
    return !getErrors(descriptor).isEmpty()
  }

  fun getErrors(descriptor: IdeaPluginDescriptor): MutableList<out HtmlChunk?> {
    val pluginId = descriptor.getPluginId()
    if (isDeleted(descriptor)) {
      return mutableListOf<HtmlChunk?>()
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
    myStatusBar = if (statusBar != null || window == null) statusBar else getStatusBar(window.getOwner())
    myPluginManagerCustomizer = PluginManagerCustomizer.getInstance()
  }

  fun getIcon(descriptor: IdeaPluginDescriptor, big: Boolean, error: Boolean, disabled: Boolean): Icon {
    val key = descriptor.getPluginId().idString + big + error + disabled
    var icon = myIcons.get(key)
    if (icon == null) {
      icon = PluginLogo.getIcon(descriptor, big, error, disabled)
      if (icon !== getDefault().getIcon(big, error, disabled)) {
        myIcons.put(key, icon)
      }
    }
    return icon
  }

  companion object {
    private val LOG = Logger.getInstance(MyPluginModel::class.java)
    private val FINISH_DYNAMIC_INSTALLATION_WITHOUT_UI = SystemProperties.getBooleanProperty(
      "plugins.finish-dynamic-plugin-installation-without-ui", true)

    val installingPlugins: MutableSet<PluginUiModel?> = HashSet<PluginUiModel?>()
    private val myInstallingWithUpdatesPlugins: MutableSet<PluginId?> = HashSet<PluginId?>()
    @JvmField
    val myInstallingInfos: MutableMap<PluginId?, InstallPluginInfo> = HashMap<PluginId?, InstallPluginInfo>()

    private fun getStatusBar(frame: Window?): StatusBarEx? {
      return if (frame is IdeFrame && frame !is WelcomeFrame) (frame as IdeFrame).getStatusBar() as StatusBarEx? else null
    }

    fun isInstallingOrUpdate(pluginId: PluginId?): Boolean {
      return myInstallingWithUpdatesPlugins.contains(pluginId)
    }

    @JvmStatic
    fun finishInstall(descriptor: PluginUiModel): InstallPluginInfo {
      val info: InstallPluginInfo = myInstallingInfos.remove(descriptor.pluginId)!!
      info.close()
      myInstallingWithUpdatesPlugins.remove(descriptor.pluginId)
      if (info.install) {
        installingPlugins.remove(descriptor)
      }
      return info
    }

    fun addProgress(descriptor: IdeaPluginDescriptor, indicator: ProgressIndicatorEx) {
      val info: InstallPluginInfo? = myInstallingInfos.get(descriptor.getPluginId())
      if (info == null) return
      info.indicator.addStateDelegate(indicator)
    }

    fun removeProgress(descriptor: IdeaPluginDescriptor, indicator: ProgressIndicatorEx) {
      val info: InstallPluginInfo? = myInstallingInfos.get(descriptor.getPluginId())
      if (info == null) return
      info.indicator.removeStateDelegate(indicator)
    }

    private fun getVendorsCount(descriptors: MutableCollection<PluginUiModel>): MutableMap<String?, Int?> {
      val vendors: MutableMap<String?, Int?> = HashMap<String?, Int?>()

      for (descriptor in descriptors) {
        val vendor = StringUtil.trim(descriptor.vendor)
        if (!StringUtil.isEmptyOrSpaces(vendor)) {
          vendors.compute(vendor) { `__`: String?, old: Int? -> (if (old != null) old else 0) + 1 }
        }
      }

      return vendors
    }

    fun isVendor(descriptor: PluginUiModel, vendors: MutableSet<String>): Boolean {
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

    fun getErrors(errorCheckResults: MutableMap<PluginId?, CheckErrorsResult?>): MutableMap<PluginId?, MutableList<HtmlChunk?>?> {
      return errorCheckResults.entries.stream()
        .collect(Collectors.toMap(
          Function { Map.Entry.key },
          Function { entry: MutableMap.MutableEntry<PluginId?, CheckErrorsResult?>? -> Companion.getErrors(entry!!.value!!) }
        ))
    }

    fun getErrors(checkErrorsResult: CheckErrorsResult): MutableList<HtmlChunk?> {
      if (checkErrorsResult.isDisabledDependencyError) {
        val loadingError = checkErrorsResult.loadingError
        return if (loadingError != null) List.of<HtmlChunk?>(createTextChunk(loadingError)) else mutableListOf<HtmlChunk?>()
      }

      val errors = ArrayList<HtmlChunk?>()

      val requiredPluginNames: MutableSet<String?> = checkErrorsResult.requiredPluginNames
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

      return Collections.unmodifiableList<HtmlChunk?>(errors)
    }

    fun getPluginNames(descriptors: MutableCollection<out IdeaPluginDescriptor?>): @Unmodifiable MutableSet<String?> {
      return ContainerUtil.map2Set(descriptors,
                                   { obj: IdeaPluginDescriptor? -> obj!!.getName() })
    }

    fun joinPluginNamesOrIds(pluginNames: MutableSet<String?>): String {
      return StringUtil.join<String?>(pluginNames,
                                      com.intellij.util.Function { str: String? -> StringUtil.wrapWithDoubleQuote(str!!) },
                                      ", ")
    }

    private fun getDependenciesOnPlugins(project: Project): @Unmodifiable MutableList<String?> {
      return ContainerUtil.map<DependencyOnPlugin?, String?>(
        ExternalDependenciesManager.getInstance(project).getDependencies<DependencyOnPlugin?>(DependencyOnPlugin::class.java),
        com.intellij.util.Function { obj: DependencyOnPlugin? -> obj!!.getPluginId() })
    }

    private fun createTextChunk(message: @Nls String): HtmlChunk.Element {
      return HtmlChunk.span().addText(message)
    }
  }
}
