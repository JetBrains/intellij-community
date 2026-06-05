// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.PluginEnableDisableAction
import com.intellij.ide.plugins.PluginEnabledState
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.getUiInspectorContextFor
import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorContextProvider
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.LicensingFacade
import com.intellij.ui.RelativeFont
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.PlatformUtils
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.system.OS
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.lang.Deprecated
import java.util.Arrays
import java.util.Collections
import java.util.StringJoiner
import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.plaf.ButtonUI
import javax.swing.text.BadLocationException

@ApiStatus.Internal
class ListPluginComponent(
  pluginModelFacade: PluginModelFacade,
  pluginUiModel: PluginUiModel,
  group: PluginsGroup,
  listModel: ListPluginModel,
  searchListener: LinkListener<Any>,
  coroutineScope: CoroutineScope,
  marketplace: Boolean,
) : JPanel() {
  private val myModelFacade: PluginModelFacade = pluginModelFacade
  private val mySearchListener: LinkListener<Any> = searchListener
  private val myMarketplace: Boolean = marketplace
  private val myGroup: PluginsGroup = group
  private val myIsAvailable: Boolean

  /** FIXME value logic is duplicated with {@link com.intellij.ide.plugins.newui.PluginDetailsPageComponent} */
  private val myIsDisableAllowed: Boolean
  private val myIsNotFreeInFreeMode: Boolean
  private var myPlugin: PluginUiModel = pluginUiModel
  private var myInstalledPluginMarketplaceNode: PluginUiModel? = null
  private var myOnlyUpdateMode = false
  private var myAfterUpdate = false

  @JvmField
  var myUpdateDescriptor: PluginUiModel? = null

  @JvmField
  var myInstalledDescriptorForMarketplace: PluginUiModel? = null

  private val myNameComponent = JBLabel()
  private val myIconComponent = JLabel(AllIcons.Plugins.PluginLogo)
  private val myLayout = BaselineLayout()
  private var successfullyFinishedOnce = false

  @JvmField
  var myRestartButton: JButton? = null

  @JvmField
  var myInstallButton: InstallButton? = null

  @JvmField
  var myUpdateButton: JButton? = null
  private var myEnableDisableButton: JComponent? = null
  private var myChooseUpdateButton: JCheckBox? = null
  private var myAlignButton: JComponent? = null
  private var myMetricsPanel: JPanel? = null
  private var myRating: JLabel? = null
  private var myDownloads: JLabel? = null
  private var myVersion: JLabel? = null
  private var myVendor: JLabel? = null
  private var myLicensePanel: LicensePanel? = null
  private var myUpdateLicensePanel: LicensePanel? = null
  private var myErrorPanel: JPanel? = null
  private var myErrorComponent: ErrorComponent? = null
  private var myIndicator: ProgressIndicatorEx? = null
  private var myEventHandler: EventHandler? = null
  private var myCustomizer: PluginManagerCustomizer? = null
  private val myCoroutineScope: CoroutineScope = coroutineScope
  private var mySelection: EventHandler.SelectionType = EventHandler.SelectionType.NONE

  init {
    myInstalledDescriptorForMarketplace = listModel.installedModels.get(pluginUiModel.pluginId)
    val pluginId = myPlugin.pluginId
    val compatible = !myPlugin.isIncompatibleWithCurrentPlatform
    val pluginInstallationState = listModel.pluginInstallationStates.get(pluginId)
    myIsAvailable = (compatible || isInstalledAndEnabled(pluginInstallationState!!)) && pluginUiModel.canBeEnabled
    val pluginManager = UiPluginManager.getInstance()
    myIsNotFreeInFreeMode =
      pluginManager.isPluginRequiresUltimateButItIsDisabled(pluginModelFacade.getModel().sessionId, pluginUiModel.pluginId)
    myIsDisableAllowed = pluginUiModel.isDisableAllowed && !myIsNotFreeInFreeMode
    pluginModelFacade.addComponent(this)
    myCustomizer = if (UiPluginManager.isCombinedPluginManagerEnabled()) PluginManagerCustomizer.getInstance() else null
    isOpaque = true
    border = JBUI.Borders.empty(10)
    layout = myLayout

    myIconComponent.verticalAlignment = SwingConstants.TOP
    myIconComponent.isOpaque = false
    myLayout.setIconComponent(myIconComponent)

    myNameComponent.setText(pluginUiModel.name)
    myLayout.setNameComponent(RelativeFont.BOLD.install(myNameComponent))

    createTag()

    if (myIsAvailable) {
      createButtons(listModel.installedModels.get(pluginId), pluginInstallationState)
      createMetricsPanel()
      createLicensePanel()
    }
    else {
      createNotAvailableMarker(compatible)
    }

    if (marketplace && myInstalledDescriptorForMarketplace == null) {
      updateIcon(false, !myIsAvailable)
    }
    else {
      updateErrors(listModel.errors.getOrDefault(pluginId, Collections.emptyList()))
    }
    if (myModelFacade.isPluginInstallingOrUpdating(pluginUiModel)) {
      showProgress(false)
    }
    updateColors(EventHandler.SelectionType.NONE)

    putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY, pluginUiModel.name)

    UiInspectorUtil.registerProvider(this, PluginIdUiInspectorContextProvider())

    try {
      getListPluginComponentCustomizer().processListPluginComponent(this)
    }
    catch (e: Exception) {
      LOG.error("Error while customizing list plugin component", e)
    }
  }

  fun getGroup(): PluginsGroup {
    return myGroup
  }

  fun getSelection(): EventHandler.SelectionType {
    return mySelection
  }

  fun setSelection(type: EventHandler.SelectionType) {
    setSelection(type, type == EventHandler.SelectionType.SELECTION)
  }

  fun setSelection(type: EventHandler.SelectionType, scrollAndFocus: Boolean) {
    mySelection = type

    if (scrollAndFocus) {
      val parent = parent as JComponent?
      if (parent != null) {
        scrollToVisible(parent, bounds)

        if (type == EventHandler.SelectionType.SELECTION && HANDLE_FOCUS_ON_SELECTION.get()) {
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown { IdeFocusManager.getGlobalInstance().requestFocus(this, true) }
        }
      }
    }

    updateColors(type)
    repaint()
  }

  fun onSelection(runnable: Runnable) {
    try {
      HANDLE_FOCUS_ON_SELECTION.set(false)
      runnable.run()
    }
    finally {
      HANDLE_FOCUS_ON_SELECTION.set(true)
    }
  }

  private fun createNotAvailableMarker(compatible: Boolean) {
    myInstallButton = createInstallButton()
    if (!compatible) {
      setupNotCompatibleMarkerButton()
    }
    else {
      setupNotAllowedMarkerButton()
    }
    myLayout.addButtonComponent(myInstallButton!!)
  }

  private fun setupNotCompatibleMarkerButton() {
    val myInstallButton = myInstallButton!!
    myInstallButton.setButtonColors(false)
    myInstallButton.setEnabled(false, IdeBundle.message("plugins.configurable.unavailable.for.platform"))
    myInstallButton.toolTipText = IdeBundle.message("plugins.configurable.plugin.unavailable.for.platform", OS.CURRENT)
  }

  private fun setupNotAllowedMarkerButton() {
    val myInstallButton = myInstallButton!!
    if (myMarketplace || myModelFacade.getState(myPlugin).isDisabled) {
      myInstallButton.setButtonColors(false)
      myInstallButton.setEnabled(false, IdeBundle.message("plugin.status.not.allowed"))
      myInstallButton.toolTipText = IdeBundle.message("plugin.status.not.allowed.tooltip")
    }
    else {
      myInstallButton.setButtonColors(false)
      myInstallButton.setEnabled(true, IdeBundle.message("plugin.status.not.allowed.but.enabled"))
      myInstallButton.text = IdeBundle.message("plugin.status.not.allowed.but.enabled")
      myInstallButton.toolTipText = IdeBundle.message("plugin.status.not.allowed.tooltip.but.enabled")
      myInstallButton.setBorderColor(JBColor.red)
      myInstallButton.setTextColor(JBColor.red)
      myInstallButton.addActionListener {
        myModelFacade.disable(myPlugin)
        setupNotAllowedMarkerButton()
      }
    }
    ColorButton.setWidth72(myInstallButton)
  }

  private fun createButtons(installedModel: PluginUiModel?, installationState: PluginInstallationState?) {
    var installationState = installationState
    if (installationState == null) {
      installationState = PluginInstallationState(false, null)
    }
    if (myMarketplace) {
      if (installationState.status == PluginStatus.INSTALLED_AND_REQUIRED_RESTART) {
        myRestartButton = RestartButton(myModelFacade)
        myLayout.addButtonComponent(myRestartButton!!)
      }
      else {
        val showInstall = installedModel == null

        myInstallButton = createInstallButton()
        myLayout.addButtonComponent(myInstallButton!!)

        myInstallButton!!.addActionListener {
          val pluginUpdateSourceApplier = PluginUpdateSourceApplier(myPlugin)
          pluginUpdateSourceApplier.applyPluginUpdateSourceId()
          PluginModelAsyncOperationsExecutor.performAutoInstall(myCoroutineScope,
                                                                myModelFacade,
                                                                myPlugin,
                                                                myCustomizer,
                                                                this,
                                                                pluginUpdateSourceApplier)
        }
        myInstallButton!!.setEnabled(showInstall, IdeBundle.message("plugin.status.installed"))

        ColorButton.setWidth72(myInstallButton!!)

        myInstalledDescriptorForMarketplace = installedModel
        myInstallButton!!.isVisible = showInstall

        if (myInstalledDescriptorForMarketplace != null && myInstalledDescriptorForMarketplace!!.isDeleted) {
          if (installationState.status == PluginStatus.UNINSTALLED_WITHOUT_RESTART) {
            myInstallButton!!.isVisible = true
            myInstallButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
            myInstallButton!!.preferredSize = null
            myAfterUpdate = true
          }
          else {
            myRestartButton = RestartButton(myModelFacade)
            myLayout.addButtonComponent(myRestartButton!!)

            myModelFacade.addUninstalled(myInstalledDescriptorForMarketplace!!.pluginId)
          }
        }
        else {
          createEnableDisableButton(Supplier { getInstalledDescriptorForMarketplace()!! })
          myEnableDisableButton!!.isVisible = !showInstall

          if (!showInstall) {
            updateEnabledStateUI()
          }
        }
      }
    }
    else {
      if (myPlugin.isDeleted) {
        if (installationState.status == PluginStatus.UNINSTALLED_WITHOUT_RESTART) {
          addInstalledStatusButton("plugins.configurable.uninstalled")
          myAfterUpdate = true
        }
        else {
          myRestartButton = RestartButton(myModelFacade)
          myLayout.addButtonComponent(myRestartButton!!)

          myModelFacade.addUninstalled(myPlugin.pluginId)
        }
      }
      else {
        if (installationState.status == PluginStatus.INSTALLED_AND_REQUIRED_RESTART ||
            installationState.status == PluginStatus.UPDATED_WITH_RESTART) {
          myRestartButton = RestartButton(myModelFacade)
          myLayout.addButtonComponent(myRestartButton!!)
        }
        else if (installedModel == null && installationState.status == PluginStatus.INSTALLED_WITHOUT_RESTART) {
          addInstalledStatusButton("plugins.configurable.installed")
        }
        else {
          createEnableDisableButton(Supplier { getPluginModel() })
          updateEnabledStateUI()
        }
      }

      myAlignButton = object : JComponent() {
        override fun getPreferredSize(): Dimension {
          return if (myEnableDisableButton is JCheckBox) {
            myEnableDisableButton!!.preferredSize
          }
          else {
            super.getPreferredSize()
          }
        }

        override fun isFocusable(): Boolean {
          return false
        }
      }
      myLayout.addButtonComponent(myAlignButton!!)
      myAlignButton!!.isOpaque = false
    }

    try {
      getListPluginComponentCustomizer().processCreateButtons(this)
    }
    catch (e: Exception) {
      LOG.error("Error while customizing create buttons", e)
    }
  }

  private fun createInstallButton(): InstallButton {
    return InstallButton(false, myPlugin.requiresUpgrade)
  }

  private fun createEnableDisableButton(modelFunction: Supplier<PluginUiModel>) {
    myEnableDisableButton = createEnableDisableButton(ActionListener {
      val pluginToSwitch = modelFunction.get()
      val action = if (myModelFacade.getState(myPlugin).isDisabled) {
        PluginEnableDisableAction.ENABLE_GLOBALLY
      }
      else {
        PluginEnableDisableAction.DISABLE_GLOBALLY
      }
      myModelFacade.setEnabledState(Collections.singletonList(pluginToSwitch), action)
    })

    myLayout.addButtonComponent(myEnableDisableButton!!)
    myEnableDisableButton!!.isOpaque = false
    myEnableDisableButton!!.isEnabled = myIsDisableAllowed
    myEnableDisableButton!!.accessibleContext.setAccessibleName(IdeBundle.message("plugins.configurable.enable.checkbox.accessible.name"))
  }

  private fun createMetricsPanel() {
    myMetricsPanel = NonOpaquePanel(TextHorizontalLayout(JBUIScale.scale(7)))
    myMetricsPanel!!.border = JBUI.Borders.emptyTop(5)
    myLayout.addLineComponent(myMetricsPanel!!)
    if (myMarketplace) {
      val downloads = myPlugin.presentableDownloads()
      if (downloads != null) {
        myDownloads = createRatingLabel(myMetricsPanel!!, downloads, AllIcons.Plugins.Downloads)
      }

      val rating = myPlugin.presentableRating()
      if (rating != null) {
        myRating = createRatingLabel(myMetricsPanel!!, rating, AllIcons.Plugins.Rating)
      }
      val version = if (myInstalledDescriptorForMarketplace == null) "" else myInstalledDescriptorForMarketplace!!.version
      myVersion = createVersionLabel(myMetricsPanel!!, version, false)
      myVersion!!.isVisible = !StringUtil.isEmptyOrSpaces(version)
    }
    else {
      val version = myPlugin.version
      if (!StringUtil.isEmptyOrSpaces(version)) {
        myVersion = createVersionLabel(myMetricsPanel!!, version, myPlugin.isBundledUpdate)
      }
    }

    if (!myPlugin.isBundled) {
      val vendor = StringUtil.defaultIfEmpty(Strings.trim(myPlugin.vendor), Strings.trim(myPlugin.organization))
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        myVendor = createRatingLabel(myMetricsPanel!!, TextHorizontalLayout.FIX_LABEL, vendor, null, null, true)
      }
    }
  }

  private fun createTag() {
    var tags = myPlugin.calculateTags()
    var tooltip: String? = null

    if (myIsNotFreeInFreeMode) {
      if (PlatformUtils.isPyCharmPro()) {
        tags = Collections.singletonList(Tags.Pro.name)
      }
      else {
        tags = Collections.singletonList(Tags.Ultimate.name)
      }
      tooltip = UnavailableWithoutSubscriptionComponent.getHelpTooltip()
    }
    if (!tags.isEmpty()) {
      val tagComponent = createTagComponent(tags[0])
      if (tooltip != null) {
        tagComponent.toolTipText = tooltip
      }
      myLayout.setTagComponent(PluginManagerConfigurable.setTinyFont(tagComponent))
    }
  }

  private fun createTagComponent(tag: @Nls String): TagComponent {
    val component = TagComponent(tag)
    //noinspection unchecked
    component.setListener(mySearchListener, component)
    return component
  }

  private fun setTagTooltip(text: @Nls String?) {
    if (myLayout.myTagComponent != null) {
      myLayout.myTagComponent!!.toolTipText = text
    }
  }

  private fun createLicensePanel() {
    val productCode = myPlugin.productCode
    val instance = LicensingFacade.getInstance()
    if (myMarketplace || productCode == null || instance == null || myPlugin.isBundled || LicensePanel.isEA2Product(productCode)) {
      return
    }

    val licensePanel = LicensePanel(true)

    val stamp = instance.getConfirmationStamp(productCode)
    if (stamp == null) {
      if (ApplicationManager.getApplication().isEAP &&
          Arrays.asList("release", "true").contains(System.getProperty("eap.require.license"))) {
        setTagTooltip(IdeBundle.message("label.text.plugin.eap.license.not.required"))
        return
      }

      if (myPlugin.isLicenseOptional) {
        return // do not show "No License" for Freemium plugins
      }

      licensePanel.setText(IdeBundle.message("label.text.plugin.no.license"), true, false)
    }
    else {
      licensePanel.setTextFromStamp(stamp, instance.getExpirationDate(productCode))
    }
    setTagTooltip(licensePanel.getMessage())

    if (licensePanel.isNotification()) {
      licensePanel.border = JBUI.Borders.emptyTop(3)
      //licensePanel.setLink("Manage licenses", () -> { XXX }, false);
      myLayout.addLineComponent(licensePanel)
      myLicensePanel = licensePanel
    }
  }

  fun setOnlyUpdateMode(installedPlugin: PluginUiModel?) {
    myOnlyUpdateMode = true

    removeButtons(false)

    myChooseUpdateButton = JCheckBox(null as String?, true)
    myLayout.setCheckBoxComponent(myChooseUpdateButton!!)
    myChooseUpdateButton!!.isOpaque = false
    myChooseUpdateButton!!.accessibleContext.setAccessibleName(IdeBundle.message("plugins.configurable.choose.update.checkbox.accessible.name"))

    if (installedPlugin != null) {
      if (myDownloads != null) {
        myMetricsPanel!!.remove(myDownloads)
      }
      if (myRating != null) {
        myMetricsPanel!!.remove(myRating)
      }
      if (myVendor != null) {
        myMetricsPanel!!.remove(myVendor)
      }
      if (myVersion != null) {
        myMetricsPanel!!.remove(myVersion)
      }

      val version = NewUiUtil.getUpdateVersionText(installedPlugin.version, myPlugin.version)
      val size = myPlugin.presentableSize()
      myVersion = createRatingLabel(
        myMetricsPanel!!,
        null,
        if (size != null) "$version | $size" else version,
        null,
        null,
        false,
      )
    }

    updateColors(EventHandler.SelectionType.NONE)
  }

  fun getChooseUpdateButton(): JCheckBox? {
    return myChooseUpdateButton
  }

  fun setUpdateDescriptor(updateDescriptor: PluginUiModel?) {
    if (myMarketplace && myInstalledDescriptorForMarketplace == null ||
        updateDescriptor != null && myModelFacade.isUninstalled(updateDescriptor.pluginId)) {
      return
    }
    if (myUpdateDescriptor == null && updateDescriptor == null) {
      return
    }
    if (myIndicator != null || isRestartEnabled()) {
      return
    }

    myUpdateDescriptor = updateDescriptor

    val descriptorForActions = getDescriptorForActions()

    if (updateDescriptor == null) {
      if (myVersion != null) {
        setVersionLabelState(myVersion!!, descriptorForActions.version, descriptorForActions.isBundledUpdate)
      }
      if (myUpdateLicensePanel != null) {
        myLayout.removeLineComponent(myUpdateLicensePanel!!)
        myUpdateLicensePanel = null
      }
      if (myUpdateButton != null) {
        myUpdateButton!!.isVisible = false
      }
      if (myAlignButton != null) {
        myAlignButton!!.isVisible = false
      }
    }
    else {
      if (myVersion != null) {
        setVersionLabelState(myVersion!!, descriptorForActions.version, descriptorForActions.isBundledUpdate)
      }
      if (descriptorForActions.productCode == null && updateDescriptor.productCode != null &&
          !descriptorForActions.isBundled && !LicensePanel.isEA2Product(updateDescriptor.productCode) &&
          !LicensePanel.shouldSkipPluginLicenseDescriptionPublishing(updateDescriptor)) {
        if (myUpdateLicensePanel == null) {
          myUpdateLicensePanel = LicensePanel(true)
          myLayout.addLineComponent(myUpdateLicensePanel!!)
          myUpdateLicensePanel!!.border = JBUI.Borders.emptyTop(3)
          myUpdateLicensePanel!!.isVisible = myErrorPanel == null
          if (myEventHandler != null) {
            myEventHandler!!.addAll(myUpdateLicensePanel!!)
          }
        }

        myUpdateLicensePanel!!.showBuyPluginWithText(
          IdeBundle.message("label.next.plugin.version.is"),
          true,
          false,
          { updateDescriptor },
          true,
          true,
        )
      }
      if (myUpdateButton == null) {
        myUpdateButton = UpdateButton()
        myLayout.addButtonComponent(myUpdateButton!!, 0)
        myUpdateButton!!.addActionListener { updatePlugin(descriptorForActions, updateDescriptor) }
      }
      else if (!successfullyFinishedOnce) {
        myUpdateButton!!.isEnabled = true
        myUpdateButton!!.isVisible = true
      }
      if (myAlignButton != null) {
        myAlignButton!!.isVisible = myEnableDisableButton != null && !myEnableDisableButton!!.isVisible
      }
    }

    fullRepaint()
  }

  fun setListeners(eventHandler: EventHandler) {
    myEventHandler = eventHandler
    eventHandler.addAll(this)
  }

  fun updateColors(type: EventHandler.SelectionType) {
    val background = PluginManagerConfigurable.MAIN_BG_COLOR
    val foreground = if (type == EventHandler.SelectionType.NONE) {
      background
    }
    else if (type == EventHandler.SelectionType.HOVER) {
      HOVER_COLOR
    }
    else {
      SELECTION_COLOR
    }

    updateColors(GRAY_COLOR, JBColor.lazy { ColorUtil.alphaBlending(foreground, background) })
  }

  private fun updateColors(grayedFg: Color, background: Color) {
    setBackground(background)

    var nameForeground: Color? = null
    var otherForeground: Color = grayedFg
    var calcColor = true

    if (mySelection != EventHandler.SelectionType.NONE) {
      val color = UIManager.getColor("Plugins.selectionForeground")
      if (color != null) {
        nameForeground = color
        otherForeground = color
        calcColor = false
      }
    }

    if (calcColor && !myIsAvailable) {
      calcColor = false
      nameForeground = DisabledColor
      otherForeground = DisabledColor
    }

    if (calcColor && (!myMarketplace || myInstalledDescriptorForMarketplace != null)) {
      val plugin = getDescriptorForActions()
      val disabled =
        myModelFacade.isUninstalled(plugin.pluginId) || !myModelFacade.isPluginInstallingOrUpdating(myPlugin) && !isEnabledState()
      if (disabled) {
        nameForeground = DisabledColor
        otherForeground = DisabledColor
      }
    }

    myNameComponent.horizontalTextPosition = SwingConstants.LEFT
    myNameComponent.foreground = nameForeground

    if (myRating != null) {
      myRating!!.foreground = otherForeground
    }
    if (myDownloads != null) {
      myDownloads!!.foreground = otherForeground
    }
    if (myVersion != null) {
      myVersion!!.foreground = otherForeground
    }
    if (myVendor != null) {
      myVendor!!.foreground = otherForeground
    }
  }

  fun updateErrors(errors: List<out HtmlChunk>) {
    val plugin = getDescriptorForActions()
    val hasErrors = errors.isNotEmpty() && !myIsNotFreeInFreeMode
    updateIcon(hasErrors, myModelFacade.isUninstalled(plugin.pluginId) || !isEnabledState() || !myIsAvailable)

    if (myAlignButton != null) {
      myAlignButton!!.isVisible = myRestartButton != null || myAfterUpdate
    }

    if (hasErrors) {
      val addListeners = myErrorComponent == null && myEventHandler != null

      if (myErrorPanel == null) {
        myErrorPanel = NonOpaquePanel()
        myLayout.addLineComponent(myErrorPanel!!)
      }

      if (myErrorComponent == null) {
        myErrorComponent = ErrorComponent()
        myErrorComponent!!.border = JBUI.Borders.emptyTop(5)
        myErrorPanel!!.add(myErrorComponent, BorderLayout.CENTER)
      }

      myErrorComponent!!.setErrors(errors) { myModelFacade.enableRequiredPluginsAsync(plugin) }

      if (addListeners) {
        myEventHandler!!.addAll(myErrorPanel!!)
      }
    }
    else if (myErrorPanel != null) {
      myLayout.removeLineComponent(myErrorPanel!!)
      myErrorPanel = null
      myErrorComponent = null
    }

    if (myLicensePanel != null) {
      myLicensePanel!!.isVisible = !hasErrors && !myIsNotFreeInFreeMode
    }
    if (myUpdateLicensePanel != null) {
      myUpdateLicensePanel!!.isVisible = !hasErrors && !myIsNotFreeInFreeMode
    }
  }

  /**
   * @deprecated use #updateErrors(List<? extends HtmlChunk>)
   */
  @Deprecated(forRemoval = true)
  fun updateErrors() {
    val plugin = getDescriptorForActions()
    if (myOnlyUpdateMode) {
      updateErrors(emptyList())
    }
    else {
      PluginModelAsyncOperationsExecutor.updateErrors(myCoroutineScope, myModelFacade.getModel().sessionId, plugin.pluginId) { res ->
        updateErrors(res)
      }
    }
  }

  private fun updatePlugin(descriptorForActions: PluginUiModel, updateDescriptor: PluginUiModel) {
    val pluginUpdateSourceApplier = PluginUpdateSourceApplier(updateDescriptor)
    pluginUpdateSourceApplier.applyPluginUpdateSourceId()
    PluginModelAsyncOperationsExecutor.updatePlugin(
      myCoroutineScope,
      myModelFacade,
      descriptorForActions,
      updateDescriptor,
      myCustomizer,
      ModalityState.stateForComponent(myUpdateButton!!),
      this,
      pluginUpdateSourceApplier,
    )
  }

  private fun updateIcon(errors: Boolean, disabled: Boolean) {
    myIconComponent.icon = myModelFacade.getIcon(myPlugin, false, errors, disabled)
  }

  fun showProgress() {
    showProgress(true)
  }

  private fun showProgress(repaint: Boolean) {
    if (successfullyFinishedOnce) return
    myIndicator = AbstractProgressIndicatorExBase()
    myLayout.setProgressComponent(object : AsyncProcessIcon("PluginListComponentIconProgress") {
      override fun getBaseline(width: Int, height: Int): Int {
        return (height * 0.85).toInt()
      }

      override fun removeNotify() {
        super.removeNotify()
        if (!isDisposed()) {
          dispose()
        }
      }
    })

    PluginModelFacade.addProgress(getDescriptorForActions(), myIndicator!!)

    if (repaint) {
      fullRepaint()
    }
  }

  fun hideProgress() {
    if (successfullyFinishedOnce) return
    myIndicator = null
    myLayout.removeProgressComponent()
  }

  fun pluginInstalled(success: Boolean, restartRequired: Boolean, installedPlugin: PluginUiModel?) {
    if (success) {
      successfullyFinishedOnce = true
      if (myUpdateDescriptor != null) {
        myUpdateDescriptor = null
      }
      if (restartRequired) {
        enableRestart()
      }
      else {
        if (myInstallButton != null) {
          myInstallButton!!.setEnabled(false, IdeBundle.message("plugin.status.installed"))
          if (myInstallButton!!.isVisible) {
            myInstalledDescriptorForMarketplace = installedPlugin
            if (myInstalledDescriptorForMarketplace != null) {
              if (myMarketplace) {
                myInstallButton!!.isVisible = false
                myEnableDisableButton!!.isVisible = true
                setVersionLabelState(myVersion!!,
                                     myInstalledDescriptorForMarketplace!!.version,
                                     myInstalledDescriptorForMarketplace!!.isBundledUpdate)
                myVersion!!.isVisible = true
                updateEnabledStateUI()
                fullRepaint()
              }
              else {
                myPlugin = myInstalledDescriptorForMarketplace!!
                myInstalledDescriptorForMarketplace = null
                updateButtons(installedPlugin, PluginInstallationState(true, PluginStatus.INSTALLED_WITHOUT_RESTART))
              }
              return
            }
          }
        }
        if (myUpdateButton != null) {
          myUpdateButton!!.isEnabled = false
          myUpdateButton!!.text = IdeBundle.message("plugin.status.installed")
          myAfterUpdate = true
        }
        if (myInstallButton == null && myUpdateButton == null) {
          addInstalledStatusButton("plugins.configurable.installed")
        }
        if (myEnableDisableButton != null) {
          myLayout.removeButtonComponent(myEnableDisableButton!!)
          myEnableDisableButton = null

          if (myAlignButton != null) {
            myAlignButton!!.isVisible = true
          }
        }
      }
    }

    fullRepaint()
  }

  private fun addInstalledStatusButton(key: String) {
    if (myRestartButton != null && myRestartButton!!.isVisible) {
      return
    }
    myInstallButton = createInstallButton()
    myLayout.addButtonComponent(myInstallButton!!)
    myInstallButton!!.isVisible = true
    myInstallButton!!.setEnabled(false, IdeBundle.message(key))
  }

  fun clearProgress() {
    myIndicator = null
  }

  fun enableRestart() {
    removeButtons(true)
  }

  private fun removeButtons(showRestart: Boolean) {
    if (myInstallButton != null) {
      myLayout.removeButtonComponent(myInstallButton!!)
      myInstallButton = null
    }
    if (myUpdateButton != null) {
      myLayout.removeButtonComponent(myUpdateButton!!)
      myUpdateButton = null
    }
    if (myEnableDisableButton != null) {
      myLayout.removeButtonComponent(myEnableDisableButton!!)
      myEnableDisableButton = null
    }
    if (myIsAvailable && showRestart && myRestartButton == null) {
      myRestartButton = RestartButton(myModelFacade)
      myLayout.addButtonComponent(myRestartButton!!, 0)
    }
    if (myAlignButton != null) {
      myAlignButton!!.isVisible = true
    }

    try {
      getListPluginComponentCustomizer().processRemoveButtons(this)
    }
    catch (e: Exception) {
      LOG.error("Error while customizing remove buttons", e)
    }
  }

  fun updateButtons(installedPlugin: PluginUiModel?, state: PluginInstallationState?) {
    if (myIsAvailable) {
      removeButtons(false)
      if (myRestartButton != null) {
        myLayout.removeButtonComponent(myRestartButton!!)
        myRestartButton = null
      }
      if (myAlignButton != null) {
        myLayout.removeButtonComponent(myAlignButton!!)
        myAlignButton = null
      }
      myAfterUpdate = false
      createButtons(installedPlugin, state)
      if (myUpdateDescriptor != null) {
        setUpdateDescriptor(myUpdateDescriptor)
      }
      doUpdateEnabledState()
    }
  }

  fun updateEnabledState() {
    if (myMarketplace && myInstalledDescriptorForMarketplace == null) {
      return
    }
    doUpdateEnabledState()
  }

  @Suppress("removal")
  private fun doUpdateEnabledState() {
    if (!myModelFacade.isUninstalled(getDescriptorForActions().pluginId)) {
      updateEnabledStateUI()
    }
    updateErrors()
    setSelection(mySelection, false)

    try {
      getListPluginComponentCustomizer().processUpdateEnabledState(this)
    }
    catch (e: Exception) {
      LOG.error("Error while customizing enabled state", e)
    }
  }

  private fun updateEnabledStateUI() {
    if (myEnableDisableButton is JCheckBox) {
      (myEnableDisableButton as JCheckBox).isSelected = myModelFacade.isEnabled(getDescriptorForActions()) && !myIsNotFreeInFreeMode
    }
  }

  fun updateAfterUninstall(needRestartForUninstall: Boolean, pluginInstallationState: PluginInstallationState) {
    myModelFacade.addUninstalled(getDescriptorForActions().pluginId)
    updateColors(mySelection)
    removeButtons(needRestartForUninstall)

    if (!needRestartForUninstall &&
        pluginInstallationState.status == PluginStatus.UNINSTALLED_WITHOUT_RESTART &&
        (myRestartButton == null || !myRestartButton!!.isVisible)) {
      myInstallButton = createInstallButton()
      myLayout.addButtonComponent(myInstallButton!!)
      myInstallButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
    }
    fullRepaint()
  }

  fun updatePlugin() {
    if ((!myMarketplace || myInstalledDescriptorForMarketplace == null) &&
        myUpdateButton != null && myUpdateButton!!.isVisible && myUpdateButton!!.isEnabled) {
      myUpdateButton!!.doClick()
    }
  }

  private fun isEnabledState(): Boolean {
    return myModelFacade.isEnabled(getDescriptorForActions()) && !myIsNotFreeInFreeMode

  }

  fun isMarketplace(): Boolean {
    return myMarketplace
  }

  fun isNotFreeInFreeMode(): Boolean {
    return myIsNotFreeInFreeMode
  }

  fun isDisableAllowed(): Boolean {
    return myIsDisableAllowed
  }

  fun isRestartEnabled(): Boolean {
    return myRestartButton != null && myRestartButton!!.isVisible
  }

  fun isUpdatedWithoutRestart(): Boolean {
    return myUpdateButton != null && myUpdateButton!!.isVisible && !myUpdateButton!!.isEnabled
  }

  fun underProgress(): Boolean {
    return myIndicator != null
  }

  fun close() {
    if (myIndicator != null) {
      PluginModelFacade.removeProgress(getDescriptorForActions(), myIndicator!!)
      myIndicator = null
    }
    myModelFacade.removeComponent(this)
  }

  fun createPopupMenu(group: DefaultActionGroup, selection: List<ListPluginComponent>) {
    if (selection.isEmpty()) {
      return
    }

    if (!myIsAvailable) {
      return
    }

    if (myOnlyUpdateMode) {
      return
    }

    for (component in selection) {
      if (myModelFacade.isPluginInstallingOrUpdating(component.myPlugin) || component.myAfterUpdate) {
        return
      }
    }

    var restart = true
    for (component in selection) {
      if (component.myRestartButton == null) {
        restart = false
        break
      }
    }
    if (restart) {
      group.add(ButtonAnAction(selection[0].myRestartButton!!))
      return
    }

    val size = selection.size
    var getDescriptorFunction = true

    if (myMarketplace) {
      val installButtons = arrayOfNulls<JButton>(size)
      var installCount = 0
      var installedCount = 0

      for (i in 0 until size) {
        val component = selection[i]
        val button = component.myInstallButton
        if (button != null && button.isVisible && button.isEnabled) {
          installButtons[i] = button
          installCount++
        }
        else if (component.myInstalledDescriptorForMarketplace != null) {
          installedCount++
        }
        else {
          return
        }
      }

      if (installCount == size) {
        group.add(ButtonAnAction(*installButtons.requireNoNulls()))
        return
      }
      if (installedCount != size) {
        return
      }

      getDescriptorFunction = false
    }

    var updateButtons: Array<JButton?>? = arrayOfNulls(size)

    for (i in 0 until size) {
      val button = selection[i].myUpdateButton
      if (button == null || !button.isVisible || !button.isEnabled) {
        updateButtons = null
        break
      }
      updateButtons!![i] = button
    }

    if (updateButtons != null) {
      group.add(ButtonAnAction(*updateButtons.requireNoNulls()))
      if (size > 1) {
        return
      }
    }

    val function: Function<ListPluginComponent, PluginUiModel> = if (getDescriptorFunction) {
      Function { it.getPluginModel() }
    }
    else {
      Function { it.getInstalledDescriptorForMarketplace()!! }
    }
    SelectionBasedPluginModelAction.addActionsTo(
      group,
      { action -> createEnableDisableAction(action, selection, function) },
      { createUninstallAction(selection.toMutableList(), function as Function<ListPluginComponent, PluginUiModel?>) },
    )
  }

  fun handleKeyAction(event: KeyEvent, selection: List<ListPluginComponent>) {
    if (selection.isEmpty()) {
      return
    }

    // If the focus is not on a ListPluginComponent, the focused component will handle the event.
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    if (event.keyCode == KeyEvent.VK_SPACE && focusOwner !is ListPluginComponent) {
      return
    }

    if (myOnlyUpdateMode) {
      if (event.keyCode == KeyEvent.VK_SPACE) {
        for (component in selection) {
          component.myChooseUpdateButton!!.doClick()
        }
      }
      return
    }

    for (component in selection) {
      if (myModelFacade.isPluginInstallingOrUpdating(component.myPlugin) || component.myAfterUpdate) {
        return
      }
    }

    var restart = true
    for (component in selection) {
      if (component.myRestartButton == null) {
        restart = false
        break
      }
    }

    var getDescriptorFunction = true
    val keyCode = event.keyCode
    if (myMarketplace) {
      if (keyCode == KeyEvent.VK_ENTER) {
        if (restart) {
          selection[0].myRestartButton!!.doClick()
        }

        var installCount = 0
        var installedCount = 0

        for (component in selection) {
          val button = component.myInstallButton
          if (button != null && button.isVisible && button.isEnabled) {
            installCount++
          }
          else if (component.myInstalledDescriptorForMarketplace != null) {
            installedCount++
          }
          else {
            return
          }
        }
        val size = selection.size
        if (installCount == size) {
          for (component in selection) {
            component.myInstallButton!!.doClick()
          }
          return
        }
        if (installedCount != size) {
          return
        }
        getDescriptorFunction = false
      }
      else if (keyCode == KeyEvent.VK_SPACE || keyCode == EventHandler.DELETE_CODE) {
        var installedCount = 0
        for (component in selection) {
          if (component.myInstalledDescriptorForMarketplace != null) {
            installedCount++
          }
          else {
            return
          }
        }
        if (installedCount != selection.size) {
          return
        }
        getDescriptorFunction = false
      }
      else {
        return
      }
    }

    var update = true
    for (component in selection) {
      val button = component.myUpdateButton
      if (button == null || !button.isVisible || !button.isEnabled) {
        update = false
        break
      }
    }

    if (keyCode == KeyEvent.VK_ENTER) {
      if (restart) {
        selection[0].myRestartButton!!.doClick()
      }
      else if (update) {
        for (component in selection) {
          component.myUpdateButton!!.doClick()
        }
      }
    }
    else if (!restart && !update) {
      val function: Function<ListPluginComponent, PluginUiModel> = if (getDescriptorFunction) {
        Function { it.getPluginModel() }
      }
      else {
        Function { it.getInstalledDescriptorForMarketplace()!! }
      }

      val action: DumbAwareAction = if (keyCode == KeyEvent.VK_SPACE && event.modifiersEx == 0) {
        createEnableDisableAction(getEnableDisableAction(selection), selection, function)
      }
      else if (keyCode == EventHandler.DELETE_CODE) {
        createUninstallAction(selection.toMutableList(), function as Function<ListPluginComponent, PluginUiModel?>)
      }
      else {
        return
      }

      ActionManager.getInstance().tryToExecute(action, event, this, ActionPlaces.UNKNOWN, true)
    }
  }

  private fun fullRepaint() {
    val parent = parent!!
    parent.doLayout()
    parent.revalidate()
    parent.repaint()
  }

  @Deprecated
  fun getPluginDescriptor(): IdeaPluginDescriptor {
    return myPlugin.getDescriptor()
  }

  fun getPluginModel(): PluginUiModel {
    return myPlugin
  }

  fun getInstalledDescriptorForMarketplace(): PluginUiModel? {
    return myInstalledDescriptorForMarketplace
  }

  fun getUpdatePluginDescriptor(): PluginUiModel? {
    return if (myUpdateDescriptor != null) myUpdateDescriptor else null
  }

  fun getDescriptorForActions(): PluginUiModel {
    return if (!myMarketplace || myInstalledDescriptorForMarketplace == null) myPlugin else myInstalledDescriptorForMarketplace!!
  }

  fun setPluginModel(pluginModel: PluginUiModel) {
    myPlugin = pluginModel
  }

  @Synchronized
  fun getInstalledPluginMarketplaceModel(): PluginUiModel? {
    return myInstalledPluginMarketplaceNode
  }

  @Synchronized
  fun setInstalledPluginMarketplaceModel(model: PluginUiModel) {
    myInstalledPluginMarketplaceNode = model
  }

  fun getCoroutineScope(): CoroutineScope {
    return myCoroutineScope
  }

  fun getModelFacade(): PluginModelFacade {
    return myModelFacade
  }

  fun getCustomizer(): PluginManagerCustomizer? {
    return myCustomizer
  }

  private fun getEnableDisableAction(selection: List<out ListPluginComponent>): PluginEnableDisableAction {
    val iterator = selection.iterator()
    val isGloballyEnabledGenerator = BooleanSupplier {
      myModelFacade.getState(iterator.next().getPluginModel()) == PluginEnabledState.ENABLED
    }

    val firstDisabled = !isGloballyEnabledGenerator.asBoolean
    while (iterator.hasNext()) {
      if (firstDisabled == isGloballyEnabledGenerator.asBoolean) {
        return PluginEnableDisableAction.ENABLE_GLOBALLY
      }
    }

    return PluginEnableDisableAction.globally(firstDisabled)
  }

  private fun createEnableDisableAction(
    action: PluginEnableDisableAction,
    selection: List<out ListPluginComponent>,
    function: Function<ListPluginComponent, PluginUiModel>,
  ): SelectionBasedPluginModelAction.EnableDisableAction<ListPluginComponent> {
    var model = myModelFacade
    if (myIsNotFreeInFreeMode) {
      model = object : PluginModelFacade(model.getModel()) {
        override fun getState(model: PluginUiModel): PluginEnabledState {
          if (model == function.apply(this@ListPluginComponent)) {
            return PluginEnabledState.DISABLED
          }
          return super.getState(model)
        }
      }
    }

    return SelectionBasedPluginModelAction.EnableDisableAction(model, action, true, selection, function) {
    }
  }

  private fun createUninstallAction(
    selection: MutableList<ListPluginComponent>,
    function: Function<ListPluginComponent, PluginUiModel?>,
  ): UninstallAction<ListPluginComponent> {
    return UninstallAction(myCoroutineScope, myModelFacade, true, this, selection, function) {
      selection.forEach { PluginUpdateSourceService.getInstance().erasePluginUpdateSourceId(it.myPlugin.pluginId) }
    }
  }

  fun getFocusableComponents(): List<JComponent> {
    val components: MutableList<JComponent> = ArrayList()
    if (UIUtil.isFocusable(myLayout.myCheckBoxComponent)) {
      components.add(myLayout.myCheckBoxComponent!!)
    }
    components.addAll(ContainerUtil.filter(myLayout.myButtonComponents, UIUtil::isFocusable))
    return components
  }

  class ButtonAnAction(vararg buttons: JButton) : DumbAwareAction(buttons[0].text) {
    private val myButtons: Array<out JButton> = buttons

    init {
      shortcutSet = CommonShortcuts.ENTER
    }

    override fun actionPerformed(e: AnActionEvent) {
      for (button in myButtons) {
        button.doClick()
      }
    }
  }

  private inner class PluginIdUiInspectorContextProvider : UiInspectorContextProvider {
    override fun getUiInspectorContext(): List<PropertyBean> {
      return getUiInspectorContextFor(myPlugin)
    }
  }

  private inner class BaselineLayout : AbstractLayoutManager() {
    private val myHGap: JBValue = JBValue.Float(10f)
    private val myHOffset: JBValue = JBValue.Float(8f)
    private val myButtonOffset: JBValue = JBValue.Float(6f)

    var myIconComponent: JComponent? = null
      private set
    var myNameComponent: JLabel? = null
      private set
    private var myProgressComponent: JComponent? = null
    var myTagComponent: JComponent? = null
      private set
    var myCheckBoxComponent: JComponent? = null
      private set
    val myButtonComponents: MutableList<JComponent> = ArrayList()
    private val myLineComponents: MutableList<JComponent> = ArrayList()
    private var myButtonEnableStates: BooleanArray? = null

    override fun preferredLayoutSize(parent: Container): Dimension {
      val result = Dimension(myNameComponent!!.preferredSize)

      if (myProgressComponent == null) {
        if (myCheckBoxComponent != null) {
          val size = myCheckBoxComponent!!.preferredSize
          result.width += size.width + myHOffset.get()
          result.height = Math.max(result.height, size.height)
        }

        if (myTagComponent != null) {
          val size = myTagComponent!!.preferredSize
          result.width += size.width + 2 * myHOffset.get()
          result.height = Math.max(result.height, size.height)
        }

        val count = myButtonComponents.size
        if (count > 0) {
          var visibleCount = 0

          for (component in myButtonComponents) {
            if (component.isVisible) {
              val size = component.preferredSize
              result.width += size.width
              result.height = Math.max(result.height, size.height)
              visibleCount++
            }
          }

          if (visibleCount > 0) {
            result.width += myHOffset.get()
            result.width += (visibleCount - 1) * myButtonOffset.get()
          }
        }
      }
      else {
        val size = myProgressComponent!!.preferredSize
        result.width += myHOffset.get() + size.width
        result.height = Math.max(result.height, size.height)
      }

      for (component in myLineComponents) {
        if (component.isVisible) {
          val size = component.preferredSize
          result.width = Math.max(result.width, size.width)
          result.height += size.height
        }
      }

      val iconSize = myIconComponent!!.preferredSize
      result.width += iconSize.width + myHGap.get()
      result.height = Math.max(result.height, iconSize.height)

      JBInsets.addTo(result, insets)
      return result
    }

    override fun layoutContainer(parent: Container) {
      val insets = insets
      var x = insets.left
      var y = insets.top

      if (myProgressComponent == null && myCheckBoxComponent != null) {
        val size = myCheckBoxComponent!!.preferredSize
        myCheckBoxComponent!!.setBounds(x, (parent.height - size.height) / 2, size.width, size.height)
        x += size.width + myHGap.get()
      }

      val iconSize = myIconComponent!!.preferredSize
      myIconComponent!!.setBounds(x, y, iconSize.width, iconSize.height)
      x += iconSize.width + myHGap.get()
      y += JBUIScale.scale(2)

      val width20 = JBUIScale.scale(20)
      val calcNameWidth = Math.max(width20, calculateNameWidth())
      val nameSize = myNameComponent!!.preferredSize
      val baseline = y + myNameComponent!!.getBaseline(nameSize.width, nameSize.height)

      myNameComponent!!.toolTipText = if (calcNameWidth < nameSize.width) myNameComponent!!.text else null
      nameSize.width = Math.min(nameSize.width, calcNameWidth)
      myNameComponent!!.setBounds(x, y, nameSize.width, nameSize.height)
      y += nameSize.height

      val width = width

      if (myProgressComponent == null) {
        var nextX = x + nameSize.width + myHOffset.get()

        if (myTagComponent != null) {
          val size = myTagComponent!!.preferredSize
          setBaselineBounds(nextX, baseline, myTagComponent!!, size)
          nextX += size.width
        }

        var lastX = width - insets.right

        if (calcNameWidth > width20) {
          for (component in myButtonComponents.asReversed()) {
            if (!component.isVisible) {
              continue
            }
            val size = component.preferredSize
            lastX -= size.width
            setBaselineBounds(lastX, baseline, component, size)
            lastX -= myButtonOffset.get()
          }
        }
        else {
          for (component in myButtonComponents) {
            if (component.isVisible) {
              val size = component.preferredSize
              setBaselineBounds(nextX, baseline, component, size)
              nextX += size.width + myButtonOffset.get()
            }
          }
        }
      }
      else {
        val size = myProgressComponent!!.preferredSize
        setBaselineBounds(width - size.width - insets.right, baseline, myProgressComponent!!, size)
      }

      val lineWidth = width - x - insets.right

      for (component in myLineComponents) {
        if (component.isVisible) {
          val lineHeight = component.preferredSize.height
          component.setBounds(x, y, lineWidth, lineHeight)
          y += lineHeight
        }
      }
    }

    private fun calculateNameWidth(): Int {
      val insets = insets
      var width = width - insets.left - insets.right - myIconComponent!!.preferredSize.width - myHGap.get()

      if (myProgressComponent != null) {
        return width - myProgressComponent!!.preferredSize.width - myHOffset.get()
      }

      if (myCheckBoxComponent != null) {
        width -= myCheckBoxComponent!!.preferredSize.width + myHOffset.get()
      }

      if (myTagComponent != null) {
        width -= myTagComponent!!.preferredSize.width + 2 * myHOffset.get()
      }

      var visibleCount = 0
      for (component in myButtonComponents) {
        if (component.isVisible) {
          width -= component.preferredSize.width
          visibleCount++
        }
      }
      width -= myButtonOffset.get() * (visibleCount - 1)
      if (visibleCount > 0) {
        width -= myHOffset.get()
      }

      return width
    }

    private fun setBaselineBounds(x: Int, y: Int, component: Component, size: Dimension) {
      if (component is ActionToolbar) {
        component.setBounds(x, insets.top - JBUI.scale(1), size.width, size.height)
      }
      else {
        component.setBounds(x, y - component.getBaseline(size.width, size.height), size.width, size.height)
      }
    }

    fun setIconComponent(iconComponent: JComponent) {
      assert(myIconComponent == null)
      myIconComponent = iconComponent
      add(iconComponent)
    }

    fun setNameComponent(nameComponent: JLabel) {
      assert(myNameComponent == null)
      myNameComponent = nameComponent
      add(nameComponent)
    }

    fun setTagComponent(component: JComponent) {
      assert(myTagComponent == null)
      myTagComponent = component
      add(component)
    }

    fun addLineComponent(component: JComponent) {
      myLineComponents.add(component)
      add(component)
    }

    fun removeLineComponent(component: JComponent) {
      myLineComponents.remove(component)
      remove(component)
    }

    fun addButtonComponent(component: JComponent) {
      addButtonComponent(component, -1)
    }

    fun addButtonComponent(component: JComponent, index: Int) {
      if (myButtonComponents.isEmpty() || index == -1) {
        myButtonComponents.add(component)
      }
      else {
        myButtonComponents.add(index, component)
      }
      add(component)
      updateVisibleOther()
    }

    fun removeButtonComponent(component: JComponent) {
      myButtonComponents.remove(component)
      remove(component)
      updateVisibleOther()
    }

    fun setCheckBoxComponent(checkBoxComponent: JComponent) {
      assert(myCheckBoxComponent == null)
      myCheckBoxComponent = checkBoxComponent
      add(checkBoxComponent)
      doLayout()
    }

    fun setProgressComponent(progressComponent: JComponent) {
      if (myProgressComponent != null) return
      myProgressComponent = progressComponent
      add(progressComponent)

      if (myEventHandler != null) {
        myEventHandler!!.addAll(progressComponent)
        myEventHandler!!.updateHover(this@ListPluginComponent)
      }

      setVisibleOther(false)
      doLayout()
    }

    fun removeProgressComponent() {
      if (myProgressComponent == null) {
        return
      }

      remove(myProgressComponent)
      myProgressComponent = null

      setVisibleOther(true)
      doLayout()
    }

    private fun updateVisibleOther() {
      if (myProgressComponent != null) {
        myButtonEnableStates = null
        setVisibleOther(false)
      }
    }

    private fun setVisibleOther(value: Boolean) {
      if (myTagComponent != null) {
        myTagComponent!!.isVisible = value
      }

      if (myButtonComponents.isEmpty()) {
        return
      }
      if (value) {
        assert(myButtonEnableStates != null && myButtonEnableStates!!.size == myButtonComponents.size)

        for (i in myButtonComponents.indices) {
          myButtonComponents[i].isVisible = myButtonEnableStates!![i]
        }
        myButtonEnableStates = null
      }
      else {
        assert(myButtonEnableStates == null)
        myButtonEnableStates = BooleanArray(myButtonComponents.size)

        for (i in myButtonComponents.indices) {
          val component = myButtonComponents[i]
          myButtonEnableStates!![i] = component.isVisible
          component.isVisible = false
        }
      }
    }
  }

  private fun isInstalledAndEnabled(pluginInstallationState: PluginInstallationState): Boolean {
    return pluginInstallationState.fullyInstalled && !myModelFacade.getState(myPlugin).isDisabled
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleListPluginComponent()
    }
    return accessibleContext
  }

  protected inner class AccessibleListPluginComponent : AccessibleJComponent() {
    override fun getAccessibleRole(): AccessibleRole {
      return AccessibilityUtils.GROUPED_ELEMENTS
    }

    override fun getAccessibleDescription(): String {
      val description = StringJoiner(", ")

      if (isNotNullAndVisible(myRestartButton)) {
        description.add(IdeBundle.message("plugins.configurable.list.component.accessible.description.restart.pending"))
      }

      if (isNotNullAndVisible(myUpdateButton)) {
        if (myUpdateButton!!.isEnabled) {
          description.add(IdeBundle.message("plugins.configurable.list.component.accessible.description.update.available"))
        }
        else {
          // Disabled but visible Update button contains update result text.
          description.add(myUpdateButton!!.text)
        }
      }

      if (isNotNullAndVisible(myEnableDisableButton) && myEnableDisableButton is JCheckBox) {
        val key = if ((myEnableDisableButton as JCheckBox).isSelected) "plugins.configurable.enabled" else "plugins.configurable.disabled"
        description.add(IdeBundle.message(key))
      }

      if (isNotNullAndVisible(myInstallButton)) {
        val isDefaultText = IdeBundle.message("action.AnActionButton.text.install") == myInstallButton!!.text
        if (myInstallButton!!.isEnabled && isDefaultText) {
          description.add(IdeBundle.message("plugins.configurable.list.component.accessible.description.install.available"))
        }
        else if (!myInstallButton!!.isEnabled && !isDefaultText) {
          // Install button contains status text when it is disabled and its text is not default.
          // Disabled buttons are not focusable, so this information can be missed by screen reader users.
          description.add(myInstallButton!!.text)
        }
      }

      if (isNotNullAndVisible(myLayout.myTagComponent) && myLayout.myTagComponent is TagComponent) {
        description.add((myLayout.myTagComponent as TagComponent).getText())
      }

      if (isNotNullAndVisible(myDownloads)) {
        description.add(IdeBundle.message("plugins.configurable.list.component.accessible.description.0.downloads", myDownloads!!.text))
      }

      if (isNotNullAndVisible(myRating)) {
        description.add(IdeBundle.message("plugins.configurable.list.component.accessible.description.0.stars", myRating!!.text))
      }

      if (isNotNullAndVisible(myVersion)) {
        description.add(myVersion!!.text)
      }

      if (isNotNullAndVisible(myVendor)) {
        description.add(myVendor!!.text)
      }

      if (isNotNullAndVisible(myErrorComponent)) {
        try {
          val myErrorComponent = myErrorComponent!!
          description.add(myErrorComponent.document.getText(0, myErrorComponent.document.length))
        }
        catch (_: BadLocationException) {
        }
      }

      //noinspection HardCodedStringLiteral
      return description.toString()
    }

    private fun isNotNullAndVisible(component: JComponent?): Boolean {
      return component != null && component.isVisible
    }
  }

  companion object {
    @JvmField
    val DisabledColor: Color = JBColor.namedColor("Plugins.disabledForeground", JBColor(0xB1B1B1, 0x696969))

    @JvmField
    val GRAY_COLOR: Color = JBColor.namedColor("Label.infoForeground", JBColor(Gray._120, Gray._135))

    @JvmField
    val SELECTION_COLOR: Color = JBColor.namedColor("Plugins.lightSelectionBackground", JBColor(0xEDF6FE, 0x464A4D))

    @JvmField
    val HOVER_COLOR: Color = JBColor.namedColor("Plugins.hoverBackground", JBColor(0xEDF6FE, 0x464A4D))

    private val LOG: Logger = Logger.getInstance(ListPluginComponent::class.java)
    private val HANDLE_FOCUS_ON_SELECTION: Ref<Boolean> = Ref(true)

    private fun scrollToVisible(parent: JComponent, bounds: Rectangle) {
      if (!parent.visibleRect.contains(bounds)) {
        parent.scrollRectToVisible(bounds)
      }
    }

    private fun createEnableDisableButton(listener: ActionListener): JCheckBox {
      return object : JCheckBox() {
        private var myBaseline = -1

        init {
          addActionListener(listener)
        }

        override fun getBaseline(width: Int, height: Int): Int {
          if (myBaseline == -1) {
            val checkBox = JCheckBox("Foo", true) // NON-NLS
            val size = checkBox.preferredSize
            myBaseline = checkBox.getBaseline(size.width, size.height) - JBUIScale.scale(1)
          }
          return myBaseline
        }

        override fun setUI(ui: ButtonUI) {
          myBaseline = -1
          super.setUI(ui)
        }

        override fun getPreferredSize(): Dimension {
          val size = super.getPreferredSize()
          return Dimension(size.width + JBUIScale.scale(8), size.height + JBUIScale.scale(2))
        }
      }
    }

    @JvmStatic
    fun createRatingLabel(panel: JPanel, text: @Nls String, icon: Icon?): JLabel {
      return createRatingLabel(panel, null, text, icon, null, true)
    }

    @JvmStatic
    fun createVersionLabel(panel: JPanel, text: @Nls String?, isBundledUpdate: Boolean): JLabel {
      val label = createRatingLabel(panel, null, null, null, null, true)
      setVersionLabelState(label, text, isBundledUpdate)
      return label
    }

    @JvmStatic
    fun setVersionLabelState(versionLabel: JLabel, text: @Nls String?, isBundledUpdate: Boolean) {
      if (isBundledUpdate) {
        if (versionLabel.toolTipText == null) {
          versionLabel.toolTipText = IdeBundle.message("plugin.status.is.updated.bundled.plugin.tooltip")
        }
        if (versionLabel.icon != AllIcons.Plugins.Updated) {
          versionLabel.icon = AllIcons.Plugins.Updated
        }
      }
      else {
        versionLabel.toolTipText = null
        versionLabel.icon = null
      }
      versionLabel.text = text
    }

    @JvmStatic
    fun createRatingLabel(panel: JPanel, constraints: Any?, text: @Nls String?, icon: Icon?, color: Color?, tiny: Boolean): JLabel {
      val label = JLabel(text, icon, SwingConstants.CENTER)
      label.isOpaque = false
      label.iconTextGap = 2
      if (color != null) {
        label.foreground = color
      }
      panel.add(if (tiny) PluginManagerConfigurable.setTinyFont(label) else label, constraints)
      return label
    }
  }
}
