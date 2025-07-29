// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.ide.plugins.newui

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil.getProjectForComponent
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.PluginManagerCore.looksLikePlatformPluginAlias
import com.intellij.ide.plugins.api.ReviewsPageContainer
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.pluginCardOpened
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginHomepage
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginReviewNoteUrl
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginWriteReviewUrl
import com.intellij.ide.plugins.newui.PluginsViewCustomizer.PluginDetailsCustomizer
import com.intellij.ide.plugins.newui.SelectionBasedPluginModelAction.OptionButtonController
import com.intellij.ide.plugins.newui.buttons.InstallOptionButton
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.impl.feedback.PlatformFeedbackDialogs
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.*
import com.intellij.ui.components.labels.LinkListener
import com.intellij.ui.components.panels.ListLayout
import com.intellij.ui.components.panels.ListLayout.Companion.horizontal
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.system.OS
import com.intellij.util.ui.*
import com.intellij.util.ui.AsyncProcessIcon.BigCentered
import com.intellij.util.ui.StartupUiUtil.labelFont
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.lang.Runnable
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.plaf.TabbedPaneUI
import javax.swing.text.View
import javax.swing.text.html.ImageView
import javax.swing.text.html.ParagraphView
import kotlin.coroutines.coroutineContext

@Internal
class PluginDetailsPageComponent @JvmOverloads constructor(
  private val pluginModel: PluginModelFacade,
  private val searchListener: LinkListener<Any>,
  private val isMarketplace: Boolean,
) : MultiPanel() {
  @Suppress("OPT_IN_USAGE")
  private val limitedDispatcher = Dispatchers.IO.limitedParallelism(2)

  private val loadingIcon = BigCentered(IdeBundle.message("progress.text.loading"))

  private var emptyPanel: JBPanelWithEmptyText? = null

  private var tabbedPane: JBTabbedPane? = null

  private var rootPanel: OpaquePanel? = null
  private var panel: OpaquePanel? = null
  private var iconLabel: JLabel? = null
  private val nameComponent = createNameComponent()
  private val additionalTextLabel = JLabel()
  private var nameAndButtons: BaselinePanel? = null
  private var restartButton: JButton? = null
  private var installButton: PluginInstallButton? = null
  private val mySuggestedIdeBanner = SuggestedIdeBanner()
  private var updateButton: JButton? = null
  private var gearButton: JComponent? = null
  private var myEnableDisableButton: JButton? = null
  private var myUninstallButton: JButton? = null
  private var errorComponent: ErrorComponent? = null
  private var version: JTextField? = null
  private var isEnabledForProject: JLabel? = null
  private var versionSize: JLabel? = null
  private var tagPanel: TagPanel? = null

  private var date: JLabel? = null
  private var rating: JLabel? = null
  private var downloads: JLabel? = null
  private var myVersion1: JBLabel? = null
  private var myVersion2: JLabel? = null
  private var mySize: JLabel? = null
  private var requiredPlugins: JEditorPane? = null
  private var customRepoForDebug: JLabel? = null

  private var author: LinkPanel? = null
  private var controlledByOrgNotification: BorderLayoutPanel? = null
  private var platformIncompatibleNotification: BorderLayoutPanel? = null
  private var uninstallFeedbackNotification: BorderLayoutPanel? = null
  private var disableFeedbackNotification: BorderLayoutPanel? = null
  private val sentFeedbackPlugins = HashSet<PluginId>()
  private val licensePanel = LicensePanel(false)
  private val customLicensePanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    isVisible = false
  }
  private val unavailableWithoutSubscriptionBanner: InlineBannerBase? = UnavailableWithoutSubscriptionComponent.getBanner()
  private val partiallyAvailableBanner: InlineBannerBase? = PartiallyAvailableComponent.getBanner()
  private var homePage: LinkPanel? = null
  private var forumUrl: LinkPanel? = null
  private var licenseUrl: LinkPanel? = null
  private var pluginReportUrl: LinkPanel? = null
  private var vendorInfoPanel: VendorInfoPanel? = null
  private var bugtrackerUrl: LinkPanel? = null
  private var documentationUrl: LinkPanel? = null
  private var sourceCodeUrl: LinkPanel? = null
  private var suggestedFeatures: SuggestedComponent? = null
  private var bottomScrollPane: JBScrollPane? = null
  private val scrollPanes = ArrayList<JBScrollPane>()
  private var descriptionComponent: JEditorPane? = null
  private var description: String? = null
  private var changeNotesPanel: ChangeNotes? = null
  private var myChangeNotesEmptyState: JBPanelWithEmptyText? = null
  private var myImagesComponent: PluginImagesComponent? = null
  private var reviewPanel: ReviewCommentListContainer? = null
  private var reviewNextPageButton: JButton? = null
  private var indicator: OneLineProgressIndicator? = null

  private var plugin: PluginUiModel? = null
  private var isPluginAvailable = false
  private var isPluginCompatible = false
  private var updateDescriptor: PluginUiModel? = null
  private var installedDescriptorForMarketplace: PluginUiModel? = null

  private var showComponent: ListPluginComponent? = null

  private val customizer: PluginDetailsCustomizer

  private var enableDisableController: OptionButtonController<PluginDetailsPageComponent>? = null

  private val pluginManagerCustomizer: PluginManagerCustomizer?
  private val notificationsUpdateSemaphore = OverflowSemaphore(overflow = BufferOverflow.DROP_OLDEST)
  private val coroutineScope = pluginModel.getModel().coroutineScope

  init {
    nameAndButtons = BaselinePanel(12, false)
    customizer = getPluginsViewCustomizer().getPluginDetailsCustomizer(pluginModel.getModel())
    pluginManagerCustomizer = if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
      PluginManagerCustomizer.EP_NAME.extensionList.firstOrNull()
    }
    else null

    createPluginPanel()
    select(1, true)
    setEmptyState(EmptyState.NONE_SELECTED)
  }

  companion object {
    @JvmStatic
    fun createDescriptionComponent(imageViewHandler: Consumer<in View>?): JEditorPane {
      val kit = HTMLEditorKitBuilder().withViewFactoryExtensions({ e, view ->
                                                                   if (view is ParagraphView) {
                                                                     return@withViewFactoryExtensions object : ParagraphView(e) {
                                                                       init {
                                                                         super.setLineSpacing(0.3f)
                                                                       }

                                                                       override fun setLineSpacing(ls: Float) {
                                                                       }
                                                                     }
                                                                   }
                                                                   if (imageViewHandler != null && view is ImageView) {
                                                                     imageViewHandler.accept(view)
                                                                   }
                                                                   view
                                                                 }).build()

      val sheet = kit.styleSheet
      sheet.addRule("ul { margin-left-ltr: 30; margin-right-rtl: 30; }")
      sheet.addRule("a { color: " + ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED) + "; }")
      sheet.addRule("h4 { font-weight: bold; }")
      sheet.addRule("strong { font-weight: bold; }")
      sheet.addRule("p { margin-bottom: 6px; }")

      val font = labelFont

      val size = font.size
      sheet.addRule("h3 { font-size: " + (size + 3) + "; font-weight: bold; }")
      sheet.addRule("h2 { font-size: " + (size + 5) + "; font-weight: bold; }")
      sheet.addRule("h1 { font-size: " + (size + 9) + "; font-weight: bold; }")
      sheet.addRule("h0 { font-size: " + (size + 12) + "; font-weight: bold; }")

      val editorPane = JEditorPane()
      editorPane.isEditable = false
      editorPane.isOpaque = false
      editorPane.border = null
      editorPane.contentType = "text/html"
      editorPane.editorKit = kit
      editorPane.addHyperlinkListener(HelpIdAwareLinkListener.getInstance())

      return editorPane
    }
  }

  val descriptorForActions: PluginUiModel?
    get() = if (!isMarketplace || installedDescriptorForMarketplace == null) plugin else installedDescriptorForMarketplace

  fun setPlugin(pluginDescriptor: IdeaPluginDescriptor?) {
    if (pluginDescriptor != null) {
      this.plugin = PluginUiModelAdapter(pluginDescriptor)
    }
  }

  fun setPlugin(model: PluginUiModel?) {
    if (model != null) {
      this.plugin = model
    }
  }

  fun isShowingPlugin(pluginId: PluginId): Boolean = plugin?.pluginId == pluginId

  override fun create(key: Int): JComponent {
    if (key == 0) {
      return rootPanel!!
    }

    if (key == 1) {
      var emptyPanel = emptyPanel
      if (emptyPanel == null) {
        emptyPanel = JBPanelWithEmptyText()
        this.emptyPanel = emptyPanel
        emptyPanel.border = CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insetsTop(1))
        emptyPanel.isOpaque = true
        emptyPanel.background = PluginManagerConfigurable.MAIN_BG_COLOR
        loadingIcon.isOpaque = true
        loadingIcon.setPaintPassiveIcon(false)
        emptyPanel.add(loadingIcon)
      }
      return emptyPanel
    }
    return super.create(key)
  }

  private fun createPluginPanel() {
    createTabsContentPanel()

    rootPanel = OpaquePanel(BorderLayout())
    controlledByOrgNotification = createNotificationPanel(
      AllIcons.General.Warning,
      IdeBundle.message("plugins.configurable.not.allowed"))
    platformIncompatibleNotification = createNotificationPanel(
      AllIcons.General.Information,
      IdeBundle.message("plugins.configurable.plugin.unavailable.for.platform", OS.CURRENT))

    val feedbackDialogProvider = PlatformFeedbackDialogs.getInstance()
    uninstallFeedbackNotification = createFeedbackNotificationPanel { pluginId: String, pluginName: String, project: Project? ->
      feedbackDialogProvider.getUninstallFeedbackDialog(pluginId, pluginName, project)
    }
    disableFeedbackNotification = createFeedbackNotificationPanel { pluginId: String, pluginName: String, project: Project? ->
      feedbackDialogProvider.getDisableFeedbackDialog(pluginId, pluginName, project)
    }
    rootPanel!!.add(panel!!, BorderLayout.CENTER)
  }

  private fun createTabsContentPanel() {
    panel = OpaquePanel(BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR)

    val topPanel = OpaquePanel(VerticalLayout(JBUI.scale(8)), PluginManagerConfigurable.MAIN_BG_COLOR)
    topPanel.border = createMainBorder()
    panel!!.add(topPanel, BorderLayout.NORTH)

    topPanel.add(TagPanel(searchListener).also { tagPanel = it })
    topPanel.add(nameComponent)

    val linkPanel = NonOpaquePanel(HorizontalLayout(JBUI.scale(12)))
    topPanel.add(linkPanel)
    author = LinkPanel(linkPanel, false, false, null, null)
    homePage = LinkPanel(linkPanel, false)

    topPanel.add(nameAndButtons)
    topPanel.add(mySuggestedIdeBanner, VerticalLayout.FILL_HORIZONTAL)

    suggestedFeatures = SuggestedComponent()
    topPanel.add(suggestedFeatures, VerticalLayout.FILL_HORIZONTAL)

    additionalTextLabel.foreground = ListPluginComponent.GRAY_COLOR
    additionalTextLabel.isVisible = false

    val versionLabel = JBLabel().setCopyable(true).also { myVersion1 = it }
    val versionPanel = VersionPanel(versionLabel)
    versionPanel.add(versionLabel)
    versionPanel.add(additionalTextLabel)
    nameAndButtons!!.add(versionPanel)

    createButtons()
    nameAndButtons!!.setProgressDisabledButton((if (isMarketplace) installButton?.getComponent() else if (updateDescriptor != null) updateButton else gearButton)!!)

    topPanel.add(ErrorComponent().also { errorComponent = it }, VerticalLayout.FILL_HORIZONTAL)
    topPanel.add(licensePanel)
    licensePanel.border = JBUI.Borders.emptyBottom(5)
    topPanel.add(customLicensePanel)
    customLicensePanel.border = JBUI.Borders.emptyBottom(5)

    if (unavailableWithoutSubscriptionBanner != null) {
      topPanel.add(unavailableWithoutSubscriptionBanner, VerticalLayout.FILL_HORIZONTAL)
      unavailableWithoutSubscriptionBanner.isVisible = false
    }
    if (partiallyAvailableBanner != null) {
      topPanel.add(partiallyAvailableBanner, VerticalLayout.FILL_HORIZONTAL)
      partiallyAvailableBanner.isVisible = false
    }

    createTabs(panel!!)
  }

  private fun createFeedbackNotificationPanel(
    createDialogWrapperFunction: (String, String, Project?) -> DialogWrapper?,
  ): BorderLayoutPanel {
    val panel = createBaseNotificationPanel()

    val action = HyperlinkEventAction { e ->
      val plugin = plugin ?: return@HyperlinkEventAction

      if (e.description == "showFeedback") {
        val pluginIdString = plugin.pluginId.idString
        val pluginName = plugin.name!!
        val component = e.inputEvent.component
        val project = getProjectForComponent(component)

        val feedbackDialog = createDialogWrapperFunction(pluginIdString, pluginName, project)
        if (feedbackDialog == null) {
          return@HyperlinkEventAction
        }

        val isSent = feedbackDialog.showAndGet()
        if (isSent) {
          sentFeedbackPlugins.add(plugin.pluginId)
          scheduleNotificationsUpdate()
        }
      }
    }
    val label = DslLabel(DslLabelType.LABEL)
    label.maxLineLength = MAX_LINE_LENGTH_WORD_WRAP
    val text: @NlsSafe String = "<span>Foo</span>"
    label.text = text
    label.minimumSize = label.preferredSize
    label.text = IdeBundle.message("plugins.configurable.plugin.feedback")
    label.action = action

    panel.addToCenter(label)
    return panel
  }

  private fun scheduleNotificationsUpdate() {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(this).asContextElement()) {
      notificationsUpdateSemaphore.withPermit {
        updateNotifications()
      }
    }
  }

  private suspend fun updateNotifications() {
    val rootPanel = rootPanel!!
    rootPanel.remove(controlledByOrgNotification)
    rootPanel.remove(platformIncompatibleNotification)
    rootPanel.remove(uninstallFeedbackNotification)
    rootPanel.remove(disableFeedbackNotification)

    if (!isPluginAvailable) {
      if (!isPluginCompatible) {
        rootPanel.add(platformIncompatibleNotification!!, BorderLayout.NORTH)
      }
      else {
        rootPanel.add(controlledByOrgNotification!!, BorderLayout.NORTH)
      }
    }

    val plugin = plugin
    if (plugin != null && !sentFeedbackPlugins.contains(plugin.pluginId)) {
      val foundPlugin = withContext(Dispatchers.IO) { UiPluginManager.getInstance().findPlugin(plugin.pluginId) }
      if (foundPlugin != null && pluginModel.isUninstalled(foundPlugin.pluginId)) {
        rootPanel.add(uninstallFeedbackNotification!!, BorderLayout.NORTH)
      }
      else {
        val disabledInDiff = withContext(Dispatchers.IO) { pluginModel.isDisabledInDiff(plugin) }
        if (disabledInDiff) {
          rootPanel.add(disableFeedbackNotification!!, BorderLayout.NORTH)
        }
      }
    }
  }

  private fun createEnableDisableAction(action: PluginEnableDisableAction): SelectionBasedPluginModelAction.EnableDisableAction<PluginDetailsPageComponent> {
    return SelectionBasedPluginModelAction.EnableDisableAction(
      object : PluginModelFacade(pluginModel.getModel()) {
        override fun getState(model: PluginUiModel): PluginEnabledState {
          if (model.pluginId == plugin?.pluginId && showComponent?.isNotFreeInFreeMode == true) {
            return PluginEnabledState.DISABLED
          }
          return super.getState(model)
        }
      },
      action,
      false,
      java.util.List.of(this),
      { it.descriptorForActions },
      { scheduleNotificationsUpdate() },
    )
  }

  private fun createButtons() {
    val nameAndButtons = nameAndButtons!!
    nameAndButtons.addButtonComponent(RestartButton(pluginModel).also { restartButton = it })

    nameAndButtons.addButtonComponent(UpdateButton().also { updateButton = it })
    updateButton!!.addActionListener {
      updatePlugin()
    }

    nameAndButtons.addButtonComponent(createInstallButton().also { installButton = it }.getComponent())

    enableDisableController = SelectionBasedPluginModelAction.createOptionButton(
      { action -> this.createEnableDisableAction(action) },
      createUninstallAction())
    nameAndButtons.addButtonComponent(enableDisableController!!.button.also { gearButton = it })
    nameAndButtons.addButtonComponent(enableDisableController!!.bundledButton.also { myEnableDisableButton = it })
    nameAndButtons.addButtonComponent(enableDisableController!!.uninstallButton.also { myUninstallButton = it })

    for (component in nameAndButtons.buttonComponents) {
      component.background = PluginManagerConfigurable.MAIN_BG_COLOR
    }

    customizer.processPluginNameAndButtonsComponent(nameAndButtons)
  }

  fun setOnlyUpdateMode() {
    nameAndButtons!!.removeButtons()
    emptyPanel!!.border = null
  }

  private fun updatePlugin() {
    coroutineScope.launch {
      val modalityState = ModalityState.stateForComponent(updateButton!!)
      val customizedAction = pluginManagerCustomizer?.getUpdateButtonCustomizationModel(pluginModel,
                                                                                        descriptorForActions!!,
                                                                                        updateDescriptor,
                                                                                        modalityState)?.action

      withContext(Dispatchers.EDT + ModalityState.stateForComponent(this@PluginDetailsPageComponent).asContextElement()) {
        if (customizedAction != null) {
          customizedAction()
        }
        else {
          pluginModel.installOrUpdatePlugin(
            this@PluginDetailsPageComponent,
            descriptorForActions!!, updateDescriptor,
            modalityState,
          )
        }
      }
    }
  }

  private fun createScrollPane(component: JComponent): JBScrollPane {
    val scrollPane = JBScrollPane(component)
    scrollPane.verticalScrollBar.background = PluginManagerConfigurable.MAIN_BG_COLOR
    scrollPane.border = JBUI.Borders.empty()
    scrollPanes.add(scrollPane)
    return scrollPane
  }

  private fun createHtmlImageViewHandler(): Consumer<View> {
    return Consumer { view: View ->
      val width = view.getPreferredSpan(View.X_AXIS)
      if (width < 0 || width > bottomScrollPane!!.width) {
        bottomScrollPane!!.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      }
    }
  }

  private suspend fun customizeInstallButton() {
    val pluginToInstall = plugin ?: return
    if (gearButton!!.isEnabled) {
      installButton?.setVisible(false)
      return
    }
    val modalityState = ModalityState.stateForComponent(installButton!!.getComponent())
    val customizationModel = pluginManagerCustomizer?.getInstallButonCustomizationModel(pluginModel, pluginToInstall, modalityState)
                             ?: return
    val installOptionButton = installButton as? InstallOptionButton ?: return
    installOptionButton.setOptions(customizationModel.additionalActions)
    val mainAction = customizationModel.mainAction
    if (mainAction != null) {
      installOptionButton.action = mainAction
      installOptionButton.setEnabled(true)
      installOptionButton.isVisible = true
    }
    else {
      setDefaultInstallAction(installOptionButton)
      val text = if (customizationModel.isVisible) null else IdeBundle.message("plugins.configurable.installed")
      installButton?.setEnabled(customizationModel.isVisible, text)
      installButton?.setVisible(customizationModel.isVisible)
    }

    if (installButton?.isVisible() == true) {
      gearButton?.isVisible = false
    }

  }

  private suspend fun customizeEnableDisableButton() {
    if (pluginManagerCustomizer == null) return
    val uiModel = descriptorForActions ?: return
    if (uiModel.isBundled) return
    val component = gearButton ?: return
    val modalityState = ModalityState.stateForComponent(component)
    val customizationModel = pluginManagerCustomizer.getDisableButtonCustomizationModel(pluginModel, uiModel, modalityState) ?: return
    enableDisableController?.setOptions(customizationModel.additionalActions)
    val visible = customizationModel.isVisible && customizationModel.text == null
    component.isVisible = visible
    component.isEnabled = visible
    if (customizationModel.text != null && restartButton?.isVisible != true) {
      enableDisableController?.setText(customizationModel.text)
      gearButton?.isVisible = true
      gearButton?.isEnabled = false
    }
    else {
      enableDisableController?.update()
    }
  }

  private fun updateAdditionalText() {
    additionalTextLabel.isVisible = !isMarketplace
    if (isMarketplace) {
      return
    }
    val additionalText = pluginManagerCustomizer?.getAdditionalTitleText(plugin!!)
    if (additionalText != null) {
      additionalTextLabel.text = additionalText
    }
    additionalTextLabel.isVisible = additionalText != null
  }

  private fun createTabs(parent: JPanel) {
    val pane: JBTabbedPane = object : JBTabbedPane() {
      override fun setUI(ui: TabbedPaneUI) {
        putClientProperty("TabbedPane.hoverColor", ListPluginComponent.HOVER_COLOR)

        val contentOpaque = UIManager.getBoolean("TabbedPane.contentOpaque")
        UIManager.getDefaults()["TabbedPane.contentOpaque"] = false
        try {
          super.setUI(ui)
        }
        finally {
          UIManager.getDefaults()["TabbedPane.contentOpaque"] = contentOpaque
        }
        setTabContainerBorder(this)
      }

      override fun setEnabledAt(index: Int, enabled: Boolean) {
        super.setEnabledAt(index, enabled)
        getTabComponentAt(index).isEnabled = enabled
      }
    }
    pane.isOpaque = false
    pane.border = JBUI.Borders.emptyTop(6)
    pane.background = PluginManagerConfigurable.MAIN_BG_COLOR
    parent.add(pane)
    tabbedPane = pane

    createDescriptionTab(pane)
    createChangeNotesTab(pane)
    createReviewTab(pane)
    createAdditionalInfoTab(pane)

    setTabContainerBorder(pane)
  }

  private fun createDescriptionTab(pane: JBTabbedPane) {
    descriptionComponent = createDescriptionComponent(createHtmlImageViewHandler())

    myImagesComponent = PluginImagesComponent()
    myImagesComponent!!.border = JBUI.Borders.emptyRight(16)

    val parent: JPanel = OpaquePanel(BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR)
    parent.border = JBUI.Borders.empty(16, 16, 0, 0)
    parent.add(myImagesComponent, BorderLayout.NORTH)
    parent.add(descriptionComponent)

    addTabWithoutBorders(pane
    ) {
      pane.addTab(IdeBundle.message("plugins.configurable.overview.tab.name"),
                  createScrollPane(parent).also { bottomScrollPane = it })
    }
    myImagesComponent!!.setParent(bottomScrollPane!!.viewport)
  }

  private fun createChangeNotesTab(pane: JBTabbedPane) {
    val changeNotes = createDescriptionComponent(null)
    changeNotesPanel = ChangeNotes { text ->
      if (text != null) {
        changeNotes.text = XmlStringUtil.wrapInHtml(text)
        if (changeNotes.caret != null) {
          changeNotes.caretPosition = 0
        }
      }
      changeNotes.isVisible = text != null
    }
    val parent = JBPanelWithEmptyText(BorderLayout())
    parent.isOpaque = true
    parent.background = PluginManagerConfigurable.MAIN_BG_COLOR
    parent.border = JBUI.Borders.emptyLeft(12)
    parent.add(changeNotes)
    myChangeNotesEmptyState = parent
    pane.add(IdeBundle.message("plugins.configurable.whats.new.tab.name"), createScrollPane(parent))
  }

  private fun createReviewTab(pane: JBTabbedPane) {
    val topPanel: JPanel = Wrapper(BorderLayout(0, JBUI.scale(5)))
    topPanel.border = JBUI.Borders.empty(16, 16, 12, 16)

    val newReviewLink = LinkPanel(topPanel, true, false, null, BorderLayout.WEST)
    val pluginManager = UiPluginManager.getInstance()
    newReviewLink.showWithBrowseUrl(IdeBundle.message("plugins.new.review.action"), false) {
      val pluginUiModel = plugin!!
      val installedPlugin = pluginManager.getPlugin(pluginUiModel.pluginId)
      getPluginWriteReviewUrl(pluginUiModel.pluginId, installedPlugin?.version)
    }

    val notePanel: JPanel = Wrapper(
      horizontal(JBUI.scale(5), ListLayout.Alignment.CENTER, ListLayout.GrowPolicy.NO_GROW))
    val noteLink = LinkPanel(notePanel, true, true, null, null)
    noteLink.showWithBrowseUrl(IdeBundle.message("plugins.review.note"), IdeBundle.message("plugins.review.note.link"), false
    ) { getPluginReviewNoteUrl() }
    topPanel.add(notePanel, BorderLayout.SOUTH)

    val reviewsPanel: JPanel = OpaquePanel(BorderLayout(), PluginManagerConfigurable.MAIN_BG_COLOR)
    reviewsPanel.add(topPanel, BorderLayout.NORTH)

    reviewPanel = ReviewCommentListContainer()
    reviewsPanel.add(reviewPanel)

    reviewNextPageButton = JButton(IdeBundle.message("plugins.review.panel.next.page.button"))
    reviewNextPageButton!!.isOpaque = false
    reviewsPanel.add(Wrapper(FlowLayout(), reviewNextPageButton), BorderLayout.SOUTH)

    reviewNextPageButton!!.addActionListener { e: ActionEvent? ->
      reviewNextPageButton!!.icon = AnimatedIcon.Default.INSTANCE
      reviewNextPageButton!!.isEnabled = false

      val component = showComponent
      val installedModel = installedPluginMarketplaceNode
      val node = installedModel ?: component!!.pluginModel
      val reviewComments = node.reviewComments!!
      val page = reviewComments.getNextPage()
      ProcessIOExecutorService.INSTANCE.execute {
        val items = UiPluginManager.getInstance().loadPluginReviews(node.pluginId, page)
        if (items == null) return@execute
        ApplicationManager.getApplication().invokeLater({
                                                          if (showComponent != component) {
                                                            return@invokeLater
                                                          }
                                                          if (items.isNotEmpty()) {
                                                            reviewComments.addItems(items)
                                                            reviewPanel!!.addComments(items)
                                                            reviewPanel!!.fullRepaint()
                                                          }

                                                          reviewNextPageButton!!.icon = null
                                                          reviewNextPageButton!!.isEnabled = true
                                                          reviewNextPageButton!!.isVisible = reviewComments.isNextPage
                                                        },
                                                        ModalityState.stateForComponent(component!!))
      }
    }

    addTabWithoutBorders(pane
    ) {
      pane.add(IdeBundle.message("plugins.configurable.reviews.tab.name"), createScrollPane(reviewsPanel))
    }
  }

  private fun createAdditionalInfoTab(pane: JBTabbedPane) {
    val infoPanel: JPanel = OpaquePanel(VerticalLayout(JBUI.scale(16)), PluginManagerConfigurable.MAIN_BG_COLOR)
    infoPanel.border = JBUI.Borders.empty(16, 12, 0, 0)

    documentationUrl = LinkPanel(infoPanel, false)
    bugtrackerUrl = LinkPanel(infoPanel, false)
    forumUrl = LinkPanel(infoPanel, false)
    sourceCodeUrl = LinkPanel(infoPanel, false)
    licenseUrl = LinkPanel(infoPanel, false)
    pluginReportUrl = LinkPanel(infoPanel, false)

    infoPanel.add(VendorInfoPanel().also { vendorInfoPanel = it })
    infoPanel.add(JLabel().also { rating = it })
    infoPanel.add(JLabel().also { downloads = it })
    infoPanel.add(JLabel().also { myVersion2 = it })
    infoPanel.add(JLabel().also { date = it })
    infoPanel.add(JLabel().also { mySize = it })
    infoPanel.add(createRequiredPluginsComponent().also { requiredPlugins = it }, VerticalLayout.FILL_HORIZONTAL)

    rating!!.foreground = ListPluginComponent.GRAY_COLOR
    downloads!!.foreground = ListPluginComponent.GRAY_COLOR
    myVersion2!!.foreground = ListPluginComponent.GRAY_COLOR
    date!!.foreground = ListPluginComponent.GRAY_COLOR
    mySize!!.foreground = ListPluginComponent.GRAY_COLOR

    if (isMarketplace && ApplicationManager.getApplication().isInternal) {
      infoPanel.add(JLabel().also { customRepoForDebug = it })
      customRepoForDebug!!.foreground = ListPluginComponent.GRAY_COLOR
    }

    pane.add(IdeBundle.message("plugins.configurable.additional.info.tab.name"), Wrapper(infoPanel))
  }

  fun showPlugins(selection: List<ListPluginComponent?>) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(this).asContextElement()) {
      val size = selection.size
      showPlugin(if (size == 1) selection[0] else null, size > 1)
    }
  }

  fun showPlugin(component: ListPluginComponent?) {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(this).asContextElement()) {
      showPlugin(component, false)
    }
  }

  private suspend fun showPlugin(component: ListPluginComponent?, multiSelection: Boolean) {
    if (showComponent == component && (component == null || updateDescriptor === component.updatePluginDescriptor)) {
      return
    }
    showComponent = component

    if (indicator != null) {
      PluginModelFacade.removeProgress(descriptorForActions!!, indicator!!)
      hideProgress(false, false)
    }

    if (component == null) {
      installedDescriptorForMarketplace = null
      updateDescriptor = installedDescriptorForMarketplace
      plugin = updateDescriptor
      select(1, true)
      setEmptyState(if (multiSelection) EmptyState.MULTI_SELECT else EmptyState.NONE_SELECTED)
    }
    else {
      var syncLoading = true
      val pluginUiModel = component.pluginModel
      if (pluginUiModel.isFromMarketplace) {
        if (!pluginUiModel.detailsLoaded) {
          syncLoading = false
          doLoad(component) {
            val loadedModel = loadPluginDetails(pluginUiModel)
            if (loadedModel != null) {
              coroutineContext.ensureActive()
              loadAllPluginDetails(pluginUiModel, loadedModel)
              component.pluginModel = loadedModel
            }
          }
        }
        else if (!pluginUiModel.isConverted &&
                 (pluginUiModel.screenShots == null || pluginUiModel.reviewComments == null || pluginUiModel.dependencyNames == null)) {
          syncLoading = false
          doLoad(component) {
            if (pluginUiModel.screenShots == null && pluginUiModel.externalPluginIdForScreenShots != null) {
              val metadata = UiPluginManager.getInstance().loadPluginMetadata(pluginUiModel.externalPluginIdForScreenShots!!)
              if (metadata != null) {
                if (metadata.screenshots != null) {
                  pluginUiModel.screenShots = metadata.screenshots
                }
                metadata.toPluginUiModel(pluginUiModel)
              }
            }

            if (pluginUiModel.reviewComments == null) {
              coroutineContext.ensureActive()
              loadReviews(pluginUiModel)
            }
            if (pluginUiModel.dependencyNames == null) {
              coroutineContext.ensureActive()
              loadDependencyNames(pluginUiModel)
            }
          }
        }
        else if (!pluginUiModel.isConverted && !isMarketplace) {
          component.setInstalledPluginMarketplaceModel(pluginUiModel)
        }
      }
      else if (!pluginUiModel.isBundled && component.installedPluginMarketplaceModel == null) {
        syncLoading = false
        doLoad(component) {
          val lastUpdateModel = UiPluginManager.getInstance().getLastCompatiblePluginUpdateModel(component.pluginModel.pluginId)
                                ?: return@doLoad

          coroutineContext.ensureActive()
          val update = UiPluginManager.getInstance().getLastCompatiblePluginUpdate(setOf(component.pluginModel.pluginId), false)
          if (!update.isEmpty()) {
            val compatibleUpdate = update[0]
            lastUpdateModel.externalPluginId = compatibleUpdate.externalPluginId
            lastUpdateModel.externalUpdateId = compatibleUpdate.externalUpdateId
          }

          coroutineContext.ensureActive()

          val fullNode = loadPluginDetails(lastUpdateModel)
          if (fullNode != null) {
            loadAllPluginDetails(lastUpdateModel, fullNode)
            component.setInstalledPluginMarketplaceModel(fullNode)
          }
        }
      }

      if (syncLoading) {
        showPluginImpl(component.pluginModel, component.getUpdatePluginDescriptor())
        pluginCardOpened(component.pluginDescriptor, component.group)
      }
    }
  }

  private fun doLoad(component: ListPluginComponent, task: suspend () -> Unit) {
    startLoading()
    val coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope
    coroutineScope.launch(limitedDispatcher) {
      task()
      coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(component).asContextElement()) {
        if (showComponent == component) {
          stopLoading()
          showPluginImpl(component.pluginModel, component.updatePluginDescriptor)
          pluginCardOpened(component.pluginDescriptor, component.group)
        }
      }
    }
  }

  suspend fun showPluginImpl(pluginUiModel: PluginUiModel, updateDescriptor: PluginUiModel?) {
    plugin = pluginUiModel
    this.updateDescriptor = if (updateDescriptor != null && updateDescriptor.canBeEnabled) updateDescriptor else null
    isPluginCompatible = !pluginUiModel.isIncompatibleWithCurrentOs
    isPluginAvailable = isPluginCompatible && updateDescriptor?.canBeEnabled ?: true
    if (isMarketplace) {
      withContext(Dispatchers.IO) {
        if (plugin == null) return@withContext
        installedDescriptorForMarketplace = UiPluginManager.getInstance().findPlugin(pluginUiModel.pluginId)
      }
      nameAndButtons!!.setProgressDisabledButton((if (this.updateDescriptor == null) installButton?.getComponent() else updateButton)!!)
    }
    if (plugin == null) return
    showPlugin(pluginUiModel)

    select(0, true)

    val suggestedCommercialIde: String? = plugin!!.suggestedCommercialIde

    if (suggestedCommercialIde != null) {
      installButton!!.setVisible(false)
    }

    if (plugin != null) {
      customizer.processShowPlugin(plugin!!.getDescriptor())
    }

    mySuggestedIdeBanner.suggestIde(suggestedCommercialIde, plugin!!.pluginId)
    applyCustomization()
  }

  private enum class EmptyState {
    NONE_SELECTED,
    MULTI_SELECT,
    PROGRESS
  }

  private fun setEmptyState(emptyState: EmptyState) {
    val text = emptyPanel!!.emptyText
    text.clear()
    loadingIcon.isVisible = false
    loadingIcon.suspend()
    when (emptyState) {
      EmptyState.MULTI_SELECT -> {
        text.setText(IdeBundle.message("plugins.configurable.several.plugins"))
        text.appendSecondaryText(IdeBundle.message("plugins.configurable.one.plugin.details"), StatusText.DEFAULT_ATTRIBUTES, null)
      }
      EmptyState.NONE_SELECTED -> text.setText(IdeBundle.message("plugins.configurable.plugin.details"))
      EmptyState.PROGRESS -> {
        loadingIcon.isVisible = true
        loadingIcon.resume()
      }
    }
  }

  private fun showPlugin(pluginModel: PluginUiModel) {
    val text: @NlsSafe String = "<html><span>" + pluginModel.name + "</span></html>"
    nameComponent.text = text
    nameComponent.foreground = null
    scheduleNotificationsUpdate()
    updateIcon()

    errorComponent?.isVisible = false

    updateButtons()

    val descriptorForActions = descriptorForActions!!
    var version = descriptorForActions.version
    if (descriptorForActions.isBundled && !descriptorForActions.allowBundledUpdate) {
      version = IdeBundle.message("plugin.version.bundled") + (if (Strings.isEmptyOrSpaces(version)) "" else " $version")
    }
    if (updateDescriptor != null) {
      version = NewUiUtil.getUpdateVersionText(descriptorForActions.version, updateDescriptor!!.version)
    }

    val isVersion = !Strings.isEmptyOrSpaces(version)

    if (this.version != null) {
      this.version!!.text = version
      versionSize!!.text = version
      this.version!!.preferredSize = Dimension(versionSize!!.preferredSize.width + scale(4), versionSize!!.preferredSize.height)

      this.version!!.isVisible = isVersion
    }

    if (myVersion1 != null) {
      myVersion1!!.text = version
      myVersion1!!.isVisible = isVersion
    }

    if (myVersion2 != null) {
      myVersion2!!.text = IdeBundle.message("plugins.configurable.version.0", version)
      myVersion2!!.isVisible = isVersion
    }

    val tags = pluginModel.calculateTags(this@PluginDetailsPageComponent.pluginModel.getModel().sessionId)

    tagPanel!!.setTags(tags)

    if (isMarketplace) {
      showMarketplaceData(pluginModel)
      updateMarketplaceTabsVisible(show = pluginModel.isFromMarketplace && !pluginModel.isConverted)
    }
    else {
      val node = installedPluginMarketplaceNode
      updateMarketplaceTabsVisible(node != null)
      if (node != null) {
        showMarketplaceData(node)
      }
      updateEnabledForProject()
    }

    val vendor = if (pluginModel.isBundled) null else pluginModel.vendor?.trim()
    val organization = if (pluginModel.isBundled) null else pluginModel.organization?.trim()
    if (!organization.isNullOrBlank()) {
      author!!.show(organization) {
        searchListener.linkSelected(
          null,
          SearchWords.VENDOR.value + (if (organization.indexOf(' ') == -1) organization else '\"'.toString() + organization + "\"")
        )
      }
    }
    else if (!vendor.isNullOrBlank()) {
      author!!.show(vendor, null)
    }
    else {
      author!!.hide()
    }

    showLicensePanel()

    val isNotFreeInFreeMode = showComponent?.isNotFreeInFreeMode == true
    unavailableWithoutSubscriptionBanner?.isVisible = isNotFreeInFreeMode
    partiallyAvailableBanner?.isVisible = !isNotFreeInFreeMode && PluginManagerCore.dependsOnUltimateOptionally(showComponent?.pluginDescriptor)

    val homepage = getPluginHomepage(pluginModel.pluginId)

    if (pluginModel.isBundled && !pluginModel.allowBundledUpdate || !isPluginFromMarketplace || homepage == null) {
      homePage!!.hide()
    }
    else {
      homePage!!.showWithBrowseUrl(
        IdeBundle.message("plugins.configurable.plugin.homepage.link"),
        true
      ) { homepage }
    }

    if (date != null) {
      val date = if (descriptorForActions.isFromMarketplace) descriptorForActions.presentableDate() else null
      this.date!!.text = IdeBundle.message("plugins.configurable.release.date.0", date)
      this.date!!.isVisible = date != null
    }

    if (suggestedFeatures != null) {
      var feature: String? = null
      if (isMarketplace && pluginModel.isFromMarketplace) {
        feature = pluginModel.suggestedFeatures.firstOrNull()
      }
      suggestedFeatures!!.setSuggestedText(feature)
    }

    for (scrollPane in scrollPanes) {
      scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    val description = getDescription()
    if (description != null && description != this.description) {
      this.description = description
      descriptionComponent!!.text = XmlStringUtil.wrapInHtml(description)
      if (descriptionComponent!!.caret != null) {
        descriptionComponent!!.caretPosition = 0
      }
    }
    descriptionComponent!!.isVisible = description != null

    changeNotesPanel!!.show(getChangeNotes())

    if (myChangeNotesEmptyState != null) {
      val message = IdeBundle.message("plugins.configurable.notes.empty.text",
                                      StringUtil.defaultIfEmpty(StringUtil.defaultIfEmpty(organization, vendor), IdeBundle.message(
                                        "plugins.configurable.notes.empty.text.default.vendor")))
      myChangeNotesEmptyState!!.emptyText.setText(message)
    }

    if (myImagesComponent != null) {
      val node = installedPluginMarketplaceNode
      myImagesComponent!!.show((node ?: pluginModel))
    }

    ApplicationManager.getApplication().invokeLater({
                                                      IdeEventQueue.getInstance().flushQueue()
                                                      for (scrollPane in scrollPanes) {
                                                        (scrollPane.verticalScrollBar as JBScrollBar).setCurrentValue(0)
                                                      }
                                                    }, ModalityState.any())

    if (this@PluginDetailsPageComponent.pluginModel.isPluginInstallingOrUpdating(pluginModel)) {
      showInstallProgress()
    }
    else {
      fullRepaint()
    }
  }

  private fun showMarketplaceData(model: PluginUiModel?) {
    var rating: String? = null
    var downloads: String? = null
    var size: String? = null
    var requiredPluginNames: Collection<String> = emptyList()

    if (model?.isFromMarketplace == true) {
      rating = model.presentableRating()
      downloads = model.presentableDownloads()
      size = model.presentableSize()

      if (reviewPanel != null) {
        updateReviews(model)
      }

      updateUrlComponent(forumUrl, "plugins.configurable.forum.url", model.forumUrl)
      updateUrlComponent(licenseUrl, "plugins.configurable.license.url", model.licenseUrl)
      updateUrlComponent(bugtrackerUrl, "plugins.configurable.bugtracker.url", model.bugtrackerUrl)
      updateUrlComponent(documentationUrl, "plugins.configurable.documentation.url", model.documentationUrl)
      updateUrlComponent(sourceCodeUrl, "plugins.configurable.source.code", model.sourceCodeUrl)
      updateUrlComponent(pluginReportUrl, "plugins.configurable.report.marketplace.plugin", model.reportPluginUrl)

      vendorInfoPanel!!.show(model)

      requiredPluginNames = model.dependencyNames ?: emptyList()

      if (customRepoForDebug != null) {
        val customRepo = model.repositoryName
        customRepoForDebug!!.text = "Custom Repository: $customRepo" //NON-NLS
        customRepoForDebug!!.isVisible = customRepo != null
      }
    }

    this.rating!!.text = IdeBundle.message("plugins.configurable.rate.0", rating)
    this.rating!!.isVisible = rating != null

    this.downloads!!.text = IdeBundle.message("plugins.configurable.downloads.0", downloads)
    this.downloads!!.isVisible = downloads != null

    mySize!!.text = IdeBundle.message("plugins.configurable.size.0", size)
    mySize!!.isVisible = size != null

    requiredPlugins!!.text = IdeBundle.message("plugins.configurable.required.plugins.0",
                                               requiredPluginNames.joinToString(separator = "\n") { "    • $it" })
    requiredPlugins!!.isVisible = !requiredPluginNames.isEmpty()
  }

  private fun updateMarketplaceTabsVisible(show: Boolean) {
    if (!show && reviewPanel != null) {
      reviewPanel!!.clear()
    }
    if (!show && tabbedPane!!.selectedIndex > 1) {
      tabbedPane!!.selectedIndex = 0
    }
    tabbedPane!!.setEnabledAt(2, show) // review
    tabbedPane!!.setEnabledAt(3, show) // additional info
  }

  private val installedPluginMarketplaceNode: PluginUiModel?
    get() = if (showComponent == null) null else showComponent!!.installedPluginMarketplaceModel

  private fun updateReviews(model: PluginUiModel) {
    val comments = model.reviewComments

    reviewPanel!!.clear()
    if (comments != null) {
      reviewPanel!!.addComments(comments.items)
    }

    reviewNextPageButton!!.icon = null
    reviewNextPageButton!!.isEnabled = true
    reviewNextPageButton!!.isVisible = comments != null && comments.isNextPage
  }

  private fun createUninstallAction(): SelectionBasedPluginModelAction.UninstallAction<PluginDetailsPageComponent> {
    return SelectionBasedPluginModelAction.UninstallAction(
      pluginModel, false, this, java.util.List.of(this),
      { obj: PluginDetailsPageComponent -> obj.descriptorForActions },
      {
        scheduleNotificationsUpdate()
      })
  }

  private val isPluginFromMarketplace: Boolean
    get() {
      checkNotNull(plugin)
      val provider = PluginInfoProvider.getInstance()
      val marketplacePlugins = provider.loadCachedPlugins()
      if (marketplacePlugins != null) {
        return marketplacePlugins.contains(plugin!!.pluginId)
      }

      // will get the marketplace plugins ids next time
      provider.loadPlugins()
      // There are no marketplace plugins in the cache, but we should show the title anyway.
      return true
    }

  private fun showLicensePanel() {
    val descriptor = descriptorForActions
    val productCode = descriptor!!.productCode
    val customization = PluginInstallationCustomization.findPluginInstallationCustomization(descriptor.pluginId)
    val customLicense = customization?.createLicensePanel(isMarketplace, updateDescriptor != null)

    customLicensePanel.removeAll()

    if (customLicense != null) {
      customLicensePanel.add(customLicense, BorderLayout.CENTER)
      customLicensePanel.isVisible = true
      licensePanel.isVisible = false
      return
    }

    customLicensePanel.isVisible = false
    licensePanel.isVisible = true

    if (descriptor.isBundled || LicensePanel.isEA2Product(productCode)) {
      licensePanel.hideWithChildren()
      return
    }
    if (productCode == null) {
      val update = updateDescriptor
      if (update != null && update.productCode != null &&
          !LicensePanel.isEA2Product(update.productCode) &&
          !LicensePanel.shouldSkipPluginLicenseDescriptionPublishing(update)
      ) {
        licensePanel.showBuyPluginWithText(IdeBundle.message("label.next.plugin.version.is"), true, false,
                                           { update }, true,
                                           true)
      }
      else {
        licensePanel.hideWithChildren()
      }
    }
    else if (isMarketplace) {
      var requiresCommercialIde = false

      val message: String = if (descriptor.isFromMarketplace) {
        val ideProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode

        val trialPeriod = descriptor.getTrialPeriodByProductCode(ideProductCode)
        val tags = descriptor.tags ?: emptyList()
        val isFreemium = tags.contains(Tags.Freemium.name)
        requiresCommercialIde = descriptor.suggestedCommercialIde != null

        getPaidPluginLicenseText(isFreemium, trialPeriod)
      }
      else {
        IdeBundle.message("label.install.paid.without.trial")
      }

      licensePanel.showBuyPluginWithText(
        message, false, false,
        { descriptor }, false,
        !requiresCommercialIde // if the descriptor requires a commercial IDE, we do not show the trial/price message
      )
    }
    else {
      val instance = LicensingFacade.getInstance()
      if (instance == null) {
        licensePanel.hideWithChildren()
        return
      }

      val stamp = instance.getConfirmationStamp(productCode)
      if (stamp == null) {
        if (ApplicationManager.getApplication().isEAP && System.getProperty("eap.require.license") !in arrayOf("release", "true")) {
          tagPanel!!.setFirstTagTooltip(IdeBundle.message("tooltip.license.not.required.for.eap.version"))
          licensePanel.hideWithChildren()
          return
        }

        if (descriptor.isLicenseOptional) {
          licensePanel.hideWithChildren()
          return // do not show "No License" for Freemium plugins
        }

        licensePanel.setText(IdeBundle.message("label.text.plugin.no.license"), true, false)
      }
      else {
        licensePanel.setTextFromStamp(stamp, instance.getExpirationDate(productCode))
      }

      tagPanel!!.setFirstTagTooltip(licensePanel.message)
      //myLicensePanel.setLink("Manage licenses", () -> { XXX }, false);
      licensePanel.isVisible = true
    }
  }

  suspend fun updateAll() {
    if (plugin != null) {
      if (indicator != null) {
        PluginModelFacade.removeProgress(descriptorForActions!!, indicator!!)
        hideProgress(false, false)
      }
      showPluginImpl(plugin!!, updateDescriptor)
    }
  }

  private fun updateButtons() {
    if (!isPluginAvailable) {
      restartButton!!.isVisible = false
      installButton!!.setVisible(false)
      updateButton!!.isVisible = false
      gearButton!!.isVisible = false
      myUninstallButton?.isVisible = false
      myEnableDisableButton!!.isVisible = false
      return
    }

    val pluginState = UiPluginManager.getInstance().getPluginInstallationState(plugin!!.pluginId)
    val installedWithoutRestart = pluginState.status == PluginStatus.INSTALLED_WITHOUT_RESTART
    if (isMarketplace) {
      val installed = pluginState.status == PluginStatus.INSTALLED_AND_REQUIRED_RESTART
      restartButton!!.isVisible = pluginState.status == PluginStatus.INSTALLED_AND_REQUIRED_RESTART

      installButton!!.setEnabled(!pluginState.fullyInstalled && pluginState.status != PluginStatus.INSTALLED_WITHOUT_RESTART,
                                 IdeBundle.message("plugins.configurable.installed"))
      installButton!!.setVisible(!installed)

      updateButton!!.isVisible = false
      myUninstallButton?.isVisible = false
      if (installed || installedDescriptorForMarketplace == null) {
        gearButton!!.isVisible = false
        myEnableDisableButton!!.isVisible = false
      }
      else {
        val state = getDeletedState(installedDescriptorForMarketplace!!)
        val uninstalled = state[0]
        val uninstalledWithoutRestart = state[1]

        installButton!!.setVisible(false)

        if (uninstalled) {
          if (uninstalledWithoutRestart) {
            restartButton!!.isVisible = false
            installButton!!.setVisible(true)
            installButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
          }
          else {
            restartButton!!.isVisible = true
          }
        }

        val bundled = installedDescriptorForMarketplace!!.isBundled
        enableDisableController!!.update()
        gearButton!!.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode != true
        myUninstallButton?.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode == true
        myEnableDisableButton!!.isVisible = bundled
        myEnableDisableButton!!.isEnabled = showComponent?.isNotFreeInFreeMode != true
        updateButton!!.isVisible = !uninstalled && updateDescriptor != null && !installedWithoutRestart
        updateEnableForNameAndIcon()
        updateErrors()
      }
    }
    else {
      installButton!!.setVisible(false)

      val state = getDeletedState(plugin!!)
      val uninstalled = state[0]
      val uninstalledWithoutRestart = state[1]

      if (uninstalled) {
        if (uninstalledWithoutRestart) {
          restartButton!!.isVisible = false
          installButton!!.setVisible(true)
          installButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
        }
        else {
          restartButton!!.isVisible = true
        }
        updateButton!!.isVisible = false
      }
      else {
        restartButton!!.isVisible = false

        updateEnabledForProject()

        updateButton!!.isVisible = updateDescriptor != null && !installedWithoutRestart
      }
      if (enableDisableController != null) {
        enableDisableController!!.update()
      }
      val bundled = plugin!!.isBundled
      val isEssential = ApplicationInfo.getInstance().isEssentialPlugin(
        plugin!!.pluginId)
      gearButton!!.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode != true
      myEnableDisableButton!!.isVisible = bundled
      myEnableDisableButton!!.isEnabled = !isEssential && showComponent?.isNotFreeInFreeMode != true
      myUninstallButton?.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode == true

      updateEnableForNameAndIcon()
      updateErrors()
    }
  }

  private fun updateIcon() {
    val descriptor = descriptorForActions ?: return
    PluginModelAsyncOperationsExecutor.updateErrors(coroutineScope, pluginModel.getModel().sessionId, descriptor.pluginId) {
      updateIcon(it)
    }
  }

  private fun updateIcon(errors: List<HtmlChunk?>) {
    if (iconLabel == null) {
      return
    }

    val hasErrors = !isMarketplace && !errors.isEmpty()

    val isNotFreeInFreeMode = showComponent?.isNotFreeInFreeMode == true
    iconLabel!!.isEnabled = isMarketplace || (pluginModel.isEnabled(plugin!!) && !isNotFreeInFreeMode)
    iconLabel!!.icon = pluginModel.getIcon(plugin!!, true, hasErrors, false)
    iconLabel!!.disabledIcon = pluginModel.getIcon(plugin!!, true, hasErrors, true)
  }

  private fun updateErrors() {
    val descriptor = descriptorForActions ?: return
    if (showComponent?.isNotFreeInFreeMode != true) {
      PluginModelAsyncOperationsExecutor.updateErrors(coroutineScope, pluginModel.getModel().sessionId, descriptor.pluginId) {
        updateIcon(it)
        errorComponent!!.setErrors(it) { this.handleErrors() }
      }
    }
  }

  private fun handleErrors() {
    pluginModel.enableRequiredPlugins(descriptorForActions!!)

    updateIcon()
    updateEnabledState()
    fullRepaint()
  }

  fun showProgress(storeIndicator: Boolean, cancelRunnable: () -> Unit) {
    indicator = OneLineProgressIndicator(false)
    indicator!!.setCancelRunnable(cancelRunnable)
    nameAndButtons!!.setProgressComponent(null, indicator!!.createBaselineWrapper())
    if (storeIndicator) {
      PluginModelFacade.addProgress(descriptorForActions!!, indicator!!)
    }

    fullRepaint()
  }

  fun showInstallProgress() {
    showProgress(true) {
      pluginModel.finishInstall(descriptorForActions!!,
                                null,
                                false,
                                false,
                                true,
                                Collections.emptyMap())
    }
  }

  fun showUninstallProgress(cs: CoroutineScope) {
    showProgress(false) {
      cs.cancel()
      hideProgress()
    }
  }

  private fun fullRepaint() {
    doLayout()
    revalidate()
    repaint()
  }

  private fun applyCustomization() {
    coroutineScope.launch(Dispatchers.EDT + ModalityState.stateForComponent(this).asContextElement()) {
      if (plugin == null || pluginManagerCustomizer == null) return@launch
      customizeEnableDisableButton()
      customizeInstallButton()
      updateAdditionalText()
      if (updateDescriptor != null) {
        nameAndButtons!!.setProgressDisabledButton(updateButton!!)
      }
      else {
        if (installButton!!.isVisible()) {
          nameAndButtons!!.setProgressDisabledButton(installButton!!.getComponent())
        }
        else {
          nameAndButtons!!.setProgressDisabledButton(gearButton!!)
        }
      }
    }
  }

  private fun updateButtonsAndApplyCustomization() {
    updateButtons()
    applyCustomization()
  }

  fun hideProgress() {
    indicator = null
    nameAndButtons?.removeProgressComponent()
  }

  fun hideProgress(success: Boolean, restartRequired: Boolean, installedPlugin: PluginUiModel? = null) {
    indicator = null
    nameAndButtons!!.removeProgressComponent()
    if (pluginManagerCustomizer != null) {
      updateButtonsAndApplyCustomization()
    }
    else {
      if (success) {
        if (restartRequired) {
          updateAfterUninstall(true)
        }
        else {
          val installButton = installButton
          if (installButton != null) {
            installButton.setEnabled(false, IdeBundle.message("plugin.status.installed"))
            if (installButton.isVisible()) {
              installedDescriptorForMarketplace = installedPlugin
              installedDescriptorForMarketplace?.let {
                installButton.setVisible(false)
                myVersion1!!.text = it.version
                myVersion1!!.isVisible = true
                updateEnabledState()
                return
              }
            }
          }
          if (updateButton!!.isVisible) {
            updateButton!!.isEnabled = false
            updateButton!!.text = IdeBundle.message("plugin.status.installed")
          }
          myEnableDisableButton!!.isVisible = false
        }
      }
    }

    fullRepaint()
  }

  private fun createInstallButton(): PluginInstallButton {
    if (Registry.`is`("reworked.plugin.manager.enabled", false)) {
      val button = InstallOptionButton()
      setDefaultInstallAction(button)
      return button
    }
    val installButton = InstallButton(true)
    installButton.addActionListener { _ ->
      installOrUpdatePlugin()
    }
    return installButton
  }

  private fun setDefaultInstallAction(button: InstallOptionButton) {
    button.action = object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent?) {
        installOrUpdatePlugin()
      }
    }
  }

  private fun installOrUpdatePlugin() {
    val modalityState = ModalityState.stateForComponent(installButton!!.getComponent())
    pluginModel.installOrUpdatePlugin(this, plugin!!, null, modalityState)
  }

  private fun updateEnableForNameAndIcon() {
    val isNotFreeInFreeMode = showComponent?.isNotFreeInFreeMode == true
    val enabled = pluginModel.isEnabled(descriptorForActions!!) && !isNotFreeInFreeMode
    nameComponent.foreground = if (enabled) null else ListPluginComponent.DisabledColor
    if (iconLabel != null) {
      iconLabel!!.isEnabled = enabled
    }
  }

  fun updateEnabledState() {
    if ((isMarketplace && installedDescriptorForMarketplace == null) || plugin == null) {
      return
    }

    if (!pluginModel.isUninstalled(descriptorForActions!!.pluginId)) {
      if (enableDisableController != null) {
        enableDisableController!!.update()
      }
      val bundled = descriptorForActions!!.isBundled
      gearButton!!.isVisible = !bundled
      myEnableDisableButton!!.isVisible = bundled
    }

    scheduleNotificationsUpdate()
    updateEnableForNameAndIcon()
    updateErrors()
    updateEnabledForProject()

    updateButton!!.isVisible = updateDescriptor != null

    fullRepaint()
  }

  fun updateAfterUninstall(showRestart: Boolean) {
    if (pluginManagerCustomizer != null) {
      updateButtonsAndApplyCustomization()
      return
    }
    installButton!!.setVisible(false)
    updateButton!!.isVisible = false
    gearButton!!.isVisible = false
    myUninstallButton?.isVisible = false
    if (myEnableDisableButton != null) {
      myEnableDisableButton!!.isVisible = false
    }
    restartButton!!.isVisible = isPluginAvailable && showRestart
    val state = UiPluginManager.getInstance().getPluginInstallationState(descriptorForActions!!.pluginId)
    if (!showRestart && state.status == PluginStatus.UNINSTALLED_WITHOUT_RESTART) {
      installButton!!.setVisible(true)
      installButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
    }

    if (!showRestart) {
      scheduleNotificationsUpdate()
    }
  }

  private fun updateEnabledForProject() {
    val enabledForProject = isEnabledForProject ?: return
    val state = pluginModel.getState(plugin!!)
    enabledForProject.text = state.presentableText
    enabledForProject.icon = AllIcons.General.ProjectConfigurable
  }

  fun startLoading() {
    select(1, true)
    setEmptyState(EmptyState.PROGRESS)
    fullRepaint()
  }

  fun stopLoading() {
    loadingIcon.suspend()
    loadingIcon.isVisible = false
    fullRepaint()
  }

  override fun doLayout() {
    super.doLayout()
    updateIconLocation()
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    updateIconLocation()
  }

  private fun updateIconLocation() {
    if (loadingIcon.isVisible) {
      loadingIcon.updateLocation(this)
    }
  }

  private fun getDescription(): @Nls String? {
    return installedPluginMarketplaceNode?.description?.takeIf { it.isNotBlank() }
           ?: plugin?.description?.takeIf { it.isNotBlank() }
  }

  private fun getChangeNotes(): @NlsSafe String? {
    return plugin?.changeNotes?.takeIf { it.isNotBlank() }
           ?: installedPluginMarketplaceNode?.changeNotes?.takeIf { it.isNotBlank() }
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessiblePluginDetailsPageComponent()
    }
    return accessibleContext
  }

  private inner class AccessiblePluginDetailsPageComponent : AccessibleJComponent() {
    override fun getAccessibleName(): String {
      return if (plugin == null) {
        IdeBundle.message("plugins.configurable.plugin.details.page.accessible.name")
      }
      else {
        IdeBundle.message("plugins.configurable.plugin.details.page.accessible.name.0", plugin!!.name)
      }
    }

    override fun getAccessibleRole(): AccessibleRole = AccessibilityUtils.GROUPED_ELEMENTS
  }
}

@ApiStatus.Internal
fun loadPluginDetails(model: PluginUiModel): PluginUiModel? {
  return UiPluginManager.getInstance().loadPluginDetails(model)
}

@ApiStatus.Internal
fun loadAllPluginDetails(existingModel: PluginUiModel, targetModel: PluginUiModel): PluginUiModel? {
  if (!existingModel.suggestedFeatures.isEmpty()) {
    targetModel.suggestedFeatures = existingModel.suggestedFeatures
  }

  val externalPluginId = existingModel.externalPluginId ?: return null
  val metadata = UiPluginManager.getInstance().loadPluginMetadata(externalPluginId)
  if (metadata != null) {
    if (metadata.screenshots != null) {
      targetModel.screenShots = metadata.screenshots
      targetModel.externalPluginIdForScreenShots = externalPluginId
    }
    metadata.toPluginUiModel(targetModel)
  }
  loadReviews(targetModel)
  loadDependencyNames(targetModel)
  return targetModel
}

@ApiStatus.Internal
fun loadReviews(existingModel: PluginUiModel): PluginUiModel? {
  val reviewComments = ReviewsPageContainer(20, 0)
  val reviews = UiPluginManager.getInstance().loadPluginReviews(existingModel.pluginId, reviewComments.getNextPage()) ?: emptyList()
  reviewComments.addItems(reviews)
  existingModel.reviewComments = reviewComments
  return existingModel
}

@ApiStatus.Internal
fun loadDependencyNames(targetModel: PluginUiModel): PluginUiModel? {
  val resultNode = targetModel
  val pluginIds = resultNode.dependencies
    .filter { !it.isOptional }
    .map(PluginDependencyModel::pluginId)
    .filter { isNotPlatformAlias(it) }

  resultNode.dependencyNames = UiPluginManager.getInstance().findPluginNames(pluginIds)

  return targetModel
}


internal fun isNotPlatformAlias(pluginId: PluginId): Boolean {
  return if ("com.intellij" == pluginId.idString) false else !looksLikePlatformPluginAlias(pluginId)
}

private fun updateUrlComponent(panel: LinkPanel?, messageKey: String, url: String?) {
  if (panel == null) {
    return
  }

  if (url.isNullOrEmpty()) {
    panel.hide()
  }
  else {
    panel.showWithBrowseUrl(IdeBundle.message(messageKey), false, Supplier { url })
  }
}

private fun getDeletedState(pluginUiModel: PluginUiModel): BooleanArray {
  val pluginId = pluginUiModel.pluginId
  var uninstalled = pluginUiModel.isDeleted

  val state = UiPluginManager.getInstance().getPluginInstallationState(pluginId)
  val uninstalledWithoutRestart = state.status == PluginStatus.UNINSTALLED_WITHOUT_RESTART
  if (!uninstalled) {
    uninstalled = state.status in listOf(PluginStatus.INSTALLED_AND_REQUIRED_RESTART, PluginStatus.UPDATED, PluginStatus.UPDATED_WITH_RESTART)
  }

  return booleanArrayOf(uninstalled, uninstalledWithoutRestart)
}

private fun getPaidPluginLicenseText(isFreemium: Boolean, trialPeriod: Int?): @Nls String {
  val withTrial = trialPeriod != null && trialPeriod != 0
  return when {
    isFreemium -> {
      if (withTrial) {
        IdeBundle.message("label.install.freemium.for.free.with.trial.or", trialPeriod)
      }
      else {
        IdeBundle.message("label.install.freemium.for.free.without.trial.or")
      }
    }
    withTrial -> IdeBundle.message("label.install.paid.with.trial.or", trialPeriod)
    else -> IdeBundle.message("label.install.paid.without.trial")
  }
}

private fun createNotificationPanel(icon: Icon, message: @Nls String): BorderLayoutPanel {
  val panel = createBaseNotificationPanel()

  val notificationLabel = JBLabel()
  notificationLabel.icon = icon
  notificationLabel.verticalTextPosition = SwingConstants.TOP
  notificationLabel.text = HtmlChunk.html().addText(message).toString()

  panel.addToCenter(notificationLabel)
  return panel
}

private fun createBaseNotificationPanel(): BorderLayoutPanel {
  val panel = BorderLayoutPanel()
  val customLine = JBUI.Borders.customLine(JBUI.CurrentTheme.Banner.INFO_BACKGROUND, 1, 0, 1, 0)
  panel.border = JBUI.Borders.merge(JBUI.Borders.empty(10), customLine, true)
  panel.background = JBUI.CurrentTheme.Banner.INFO_BACKGROUND
  return panel
}

private fun createMainBorder(): CustomLineBorder {
  return object : CustomLineBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, JBUI.insetsTop(1)) {
    override fun getBorderInsets(c: Component): Insets = JBUI.insets(15, 20, 0, 20)
  }
}

private fun createNameComponent(): JEditorPane {
  val editorPane: JEditorPane = object : JEditorPane() {
    var baselineComponent: JLabel? = null

    override fun getBaseline(width: Int, height: Int): Int {
      var baselineComponent = baselineComponent
      if (baselineComponent == null) {
        baselineComponent = JLabel()
        this.baselineComponent = baselineComponent
        baselineComponent.font = font
      }
      baselineComponent.text = text
      val size = baselineComponent.preferredSize
      return baselineComponent.getBaseline(size.width, size.height)
    }

    override fun getPreferredSize(): Dimension {
      val size = super.getPreferredSize()
      if (size.height == 0) {
        size.height = minimumSize.height
      }
      return size
    }

    override fun updateUI() {
      super.updateUI()
      font = labelFont.deriveFont(Font.BOLD, 18f)
    }
  }

  UIUtil.convertToLabel(editorPane)
  editorPane.caret = EmptyCaret.INSTANCE

  editorPane.font = JBFont.create(labelFont.deriveFont(Font.BOLD, 18f))

  val text: @NlsSafe String = "<html><span>Foo</span></html>"
  editorPane.text = text
  editorPane.minimumSize = editorPane.preferredSize
  editorPane.text = null

  return editorPane
}

private fun setTabContainerBorder(pane: JComponent) {
  val tabContainer = UIUtil.uiChildren(pane).find { it.javaClass.simpleName == "TabContainer" }
  if (tabContainer is JComponent) {
    tabContainer.border = SideBorder(PluginManagerConfigurable.SEARCH_FIELD_BORDER_COLOR, SideBorder.BOTTOM)
  }
}

private fun createRequiredPluginsComponent(): JEditorPane {
  val editorPane = JEditorPane()
  UIUtil.convertToLabel(editorPane)
  editorPane.caret = EmptyCaret.INSTANCE
  editorPane.foreground = ListPluginComponent.GRAY_COLOR
  editorPane.contentType = "text/plain"
  return editorPane
}

private fun addTabWithoutBorders(pane: JBTabbedPane, callback: Runnable) {
  val insets = pane.tabComponentInsets
  pane.tabComponentInsets = JBInsets.emptyInsets()
  callback.run()
  pane.tabComponentInsets = insets
}
