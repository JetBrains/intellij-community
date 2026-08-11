@file:Suppress("unused", "UNUSED_PARAMETER", "UsePropertyAccessSyntax", "ReplaceJavaStaticMethodWithKotlinAnalog", "CompanionObjectInExtension")

// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.plugins.newui.getPluginsViewCustomizer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.RelativeFont
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Component
import java.text.SimpleDateFormat
import java.util.function.Consumer
import java.util.function.Function
import javax.swing.JComponent

@ApiStatus.Internal
class PluginManagerConfigurable() : SearchableConfigurable, Configurable.NoScroll, Configurable.NoMargin, Configurable.TopComponentProvider {
  private var myPanel: PluginManagerConfigurablePanel? = null
  private var openSource: PluginManagerOpenSourceEnum? = null

  /**
   * @deprecated Use {@link PluginManagerConfigurable#PluginManagerConfigurable()}
   */
  @Deprecated("Use PluginManagerConfigurable()", replaceWith = ReplaceWith("PluginManagerConfigurable()"))
  constructor(project: Project?) : this()

  override fun getId(): String {
    return ID
  }

  override fun getDisplayName(): String {
    return IdeBundle.message("title.plugins")
  }

  override fun getHelpTopic(): String {
    return ID
  }

  @RequiresEdt
  override fun getCenterComponent(controller: Configurable.TopComponentController): JComponent {
    val panel = createPanelIfNeeded()
    return panel.getCenterComponent(controller)
  }

  @get:RequiresEdt
  val topComponent: JComponent
    get() = getCenterComponent(Configurable.TopComponentController.EMPTY)

  override fun createComponent(): JComponent {
    val panel = createPanelIfNeeded()

    try {
      getPluginsViewCustomizer().processConfigurable(this)
    }
    catch (e: Exception) {
      LOG.error("Error while processing configurable", e)
    }

    return panel.getComponent()
  }

  @RequiresEdt
  private fun createPanelIfNeeded(): PluginManagerConfigurablePanel {
    return createPanelIfNeeded(null)
  }

  @RequiresEdt
  private fun createPanelIfNeeded(searchQuery: String?): PluginManagerConfigurablePanel {
    if (myPanel == null) {
      myPanel = PluginManagerConfigurablePanel(searchQuery, openSource ?: PluginManagerOpenSourceEnum.OTHER)
    }
    return myPanel!!
  }

  @RequiresEdt
  fun setOpenSource(source: PluginManagerOpenSourceEnum) {
    openSource = source
  }

  @RequiresEdt
  fun setOpenSourceFromSettings() {
    if (openSource == null) {
      openSource = PluginManagerOpenSourceEnum.SETTINGS
    }
  }

  override fun disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel!!)
      myPanel = null
    }
  }

  override fun cancel() {
    if (myPanel != null) {
      myPanel!!.cancel()
    }
  }

  override fun isModified(): Boolean {
    return myPanel != null && myPanel!!.isModified()
  }

  @Throws(ConfigurationException::class)
  override fun apply() {
    if (myPanel != null) {
      myPanel!!.apply()
    }
  }

  fun scheduleApply() {
    if (myPanel != null) {
      myPanel!!.scheduleApply()
    }
  }

  override fun reset() {
    if (myPanel != null) {
      myPanel!!.reset()
    }
  }

  @RequiresEdt
  override fun enableSearch(option: String?): Runnable? {
    return createPanelIfNeeded(option).enableSearch(option)
  }

  @RequiresEdt
  fun enableSearch(option: String?, ignoreTagMarketplaceTab: Boolean): Runnable? {
    return createPanelIfNeeded(option).enableSearch(option, ignoreTagMarketplaceTab)
  }

  fun isMarketplaceTabShowing(): Boolean {
    return myPanel != null && myPanel!!.isMarketplaceTabShowing()
  }

  fun isInstalledTabShowing(): Boolean {
    return myPanel != null && myPanel!!.isInstalledTabShowing()
  }

  @RequiresEdt
  fun openMarketplaceTab(option: String) {
    createPanelIfNeeded(option).openMarketplaceTab(option)
  }

  @RequiresEdt
  fun openInstalledTab(option: String) {
    createPanelIfNeeded(option).openInstalledTab(option)
  }

  @RequiresEdt
  private fun setInstallSource(source: FUSEventSource?) {
    createPanelIfNeeded().setInstallSource(source)
  }

  @RequiresEdt
  private fun selectAndEnable(descriptors: Set<IdeaPluginDescriptor>) {
    createPanelIfNeeded().selectAndEnable(descriptors)
  }

  @RequiresEdt
  private fun select(pluginIds: Collection<PluginId>) {
    createPanelIfNeeded().select(pluginIds)
  }

  override fun isSearchableInActions(): Boolean {
    return false
  }

  companion object {
    const val ID: String = "preferences.pluginManager"
    const val SELECTION_TAB_KEY: String = "PluginConfigurable.selectionTab"

    @JvmField
    val PLUGIN_INSTALL_CALLBACK_DATA_KEY: DataKey<Consumer<PluginInstallCallbackData>> =
      DataKey.create("PLUGIN_INSTALL_CALLBACK_DATA_KEY")

    @Suppress("UseJBColor")
    @JvmField
    val MAIN_BG_COLOR: Color =
      JBColor.namedColor("Plugins.background", JBColor.lazy { if (JBColor.isBright()) UIUtil.getListBackground() else Color(0x313335) })

    @JvmField
    val SEARCH_BG_COLOR: Color = JBColor.namedColor("Plugins.SearchField.background", MAIN_BG_COLOR)

    @JvmField
    val SEARCH_FIELD_BORDER_COLOR: Color = JBColor.namedColor("Plugins.borderColor", JBColor.border())

    @JvmField
    val DATE_FORMAT: SimpleDateFormat = SimpleDateFormat("MMM dd, yyyy")

    private val LOG = Logger.getInstance(PluginManagerConfigurable::class.java)

    @JvmStatic
    fun <T : Component> setTinyFont(component: T): T {
      return if (SystemInfo.isMac) RelativeFont.TINY.install(component) else component
    }

    @JvmStatic
    @Messages.YesNoResult
    fun showRestartDialog(): Int {
      return showRestartDialog(getUpdatesDialogTitle())
    }

    @JvmStatic
    @Messages.YesNoResult
    fun showRestartDialog(title: @NlsContexts.DialogTitle String): Int {
      return showRestartDialog(title, ::getUpdatesDialogMessage)
    }

    @JvmStatic
    @Messages.YesNoResult
    fun showRestartDialog(
      title: @NlsContexts.DialogTitle String,
      message: Function<in String, @Nls String>,
    ): Int {
      val action = IdeBundle.message(
        if (ApplicationManager.getApplication().isRestartCapable()) "ide.restart.action" else "ide.shutdown.action"
      )
      return Messages.showYesNoDialog(
        message.apply(action),
        title,
        action,
        IdeBundle.message("ide.notnow.action"),
        Messages.getQuestionIcon(),
      )
    }

    @JvmStatic
    fun shutdownOrRestartApp() {
      shutdownOrRestartApp(getUpdatesDialogTitle())
    }

    @JvmStatic
    fun shutdownOrRestartApp(title: @NlsContexts.DialogTitle String) {
      shutdownOrRestartAppAfterInstall(title, ::getUpdatesDialogMessage)
    }

    @JvmStatic
    fun shutdownOrRestartAppAfterInstall(
      title: @NlsContexts.DialogTitle String,
      message: Function<in String, @Nls String>,
    ) {
      if (showRestartDialog(title, message) == Messages.YES) {
        // TODO this function should
        //  - schedule restart in invokeLater with ModalityState.nonModal();
        //  - close settings dialog.
        //  What happens:
        //  - the settings dialog should be displayed in a service coroutine.
        //  - restart awaits completion of all service coroutines.
        //  - calling restart synchronously from this function prevents completion of the service coroutine.
        //  => deadlock IDEA-335883.
        //  IDEA-335883 is currently fixed by showing the dialog outside of the container scope.
        ApplicationManagerEx.getApplicationEx().restart(true)
      }
    }

    @JvmStatic
    fun getUpdatesDialogTitle(): @NlsContexts.DialogTitle String {
      return IdeBundle.message(
        "updates.dialog.title",
        ApplicationNamesInfo.getInstance().fullProductName,
      )
    }

    @JvmStatic
    fun getUpdatesDialogMessage(action: @Nls String): @NlsContexts.DialogMessage String {
      return IdeBundle.message(
        "ide.restart.required.message",
        action,
        ApplicationNamesInfo.getInstance().fullProductName,
      )
    }

    /**
     * @deprecated Please use {@link #showPluginConfigurable(Project, Collection)}.
     */
    @JvmStatic
    @Deprecated(
      "Please use showPluginConfigurable(Project, Collection).",
      replaceWith = ReplaceWith("showPluginConfigurable(project, pluginIds)"),
    )
    fun showPluginConfigurable(project: Project?, vararg descriptors: IdeaPluginDescriptor) {
      showPluginConfigurable(
        project,
        descriptors.map(IdeaPluginDescriptor::getPluginId),
      )
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showPluginManagerDialog(project: Project?, source: PluginManagerOpenSourceEnum, advancedInitialization: (PluginManagerConfigurable) -> Unit) {
      val configurable = createWithOpenSource(source)
      ShowSettingsUtil.getInstance().editConfigurable(
        project,
        configurable,
        Runnable {
          advancedInitialization(configurable)
        }
      )
    }

    private fun createWithOpenSource(openSource: PluginManagerOpenSourceEnum): PluginManagerConfigurable {
      return PluginManagerConfigurable().apply { setOpenSource(openSource) }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun createForWelcomeScreen(): PluginManagerConfigurable {
      return createWithOpenSource(PluginManagerOpenSourceEnum.WELCOME_SCREEN)
    }

    @JvmStatic
    fun showPluginConfigurable(project: Project?, pluginIds: Collection<PluginId?>) {
      showPluginConfigurable(project, pluginIds, PluginManagerOpenSourceEnum.OTHER)
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showPluginConfigurable(project: Project?, pluginIds: Collection<PluginId?>, openSource: PluginManagerOpenSourceEnum) {
      showPluginManagerDialog(project, openSource, {
        it.select(pluginIds as Collection<PluginId>)
      })
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showSuggestedPlugins(project: Project?, source: FUSEventSource?) {
      showPluginManagerDialog(project, PluginManagerOpenSourceEnum.NOTIFICATION, {
        it.setInstallSource(source)
        it.openMarketplaceTab("/suggested")
      })
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showPluginConfigurableAndEnable(project: Project?, descriptors: Set<IdeaPluginDescriptor>) {
      showPluginManagerDialog(project, PluginManagerOpenSourceEnum.NOTIFICATION, {
        it.selectAndEnable(descriptors)
      })
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showSettingsDialogFromWelcomeScreen(project: Project?) {
      ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable::class.java) {
        it.setOpenSource(PluginManagerOpenSourceEnum.WELCOME_SCREEN)
      }
    }

    @ApiStatus.Internal
    @JvmStatic
    fun showDialogFromWelcomeScreen(project: Project?, pluginIds: Collection<PluginId?>) {
      showPluginConfigurable(project, pluginIds, PluginManagerOpenSourceEnum.WELCOME_SCREEN)
    }
  }
}
