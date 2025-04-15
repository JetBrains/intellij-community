// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.ide.plugins.newui

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.ProjectUtil.getProjectForComponent
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.PluginManagerCore.buildPluginIdMap
import com.intellij.ide.plugins.PluginManagerCore.findPlugin
import com.intellij.ide.plugins.PluginManagerCore.getIncompatibleOs
import com.intellij.ide.plugins.PluginManagerCore.getPlugin
import com.intellij.ide.plugins.PluginManagerCore.looksLikePlatformPluginAlias
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.plugins.marketplace.MarketplaceRequests.Companion.getLastCompatiblePluginUpdate
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import com.intellij.ide.plugins.marketplace.statistics.PluginManagerUsageCollector.pluginCardOpened
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginHomepage
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginReviewNoteUrl
import com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginWriteReviewUrl
import com.intellij.ide.plugins.newui.PluginsViewCustomizer.PluginDetailsCustomizer
import com.intellij.ide.plugins.newui.SelectionBasedPluginModelAction.OptionButtonController
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.impl.feedback.PlatformFeedbackDialogs
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
import com.intellij.util.ui.*
import com.intellij.util.ui.AsyncProcessIcon.BigCentered
import com.intellij.util.ui.StartupUiUtil.labelFont
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.function.Consumer
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.TabbedPaneUI
import javax.swing.text.View
import javax.swing.text.html.ImageView
import javax.swing.text.html.ParagraphView
import kotlin.coroutines.coroutineContext

@Internal
class PluginDetailsPageComponent @JvmOverloads constructor(
  private val pluginModel: MyPluginModel,
  private val searchListener: LinkListener<Any>,
  private val isMarketplace: Boolean,
  private val isMultiTabs: Boolean = isMultiTabs(),
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
  private var nameAndButtons: BaselinePanel? = null
  private var restartButton: JButton? = null
  private var installButton: InstallButton? = null
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

  private var plugin: IdeaPluginDescriptor? = null
  private var isPluginAvailable = false
  private var isPluginCompatible = false
  private var updateDescriptor: IdeaPluginDescriptor? = null
  private var installedDescriptorForMarketplace: IdeaPluginDescriptor? = null

  private var showComponent: ListPluginComponent? = null

  private val customizer: PluginDetailsCustomizer

  private var enableDisableController: OptionButtonController<PluginDetailsPageComponent>? = null

  init {
    nameAndButtons = if (isMultiTabs) BaselinePanel(12, false) else BaselinePanel()
    customizer = getPluginsViewCustomizer().getPluginDetailsCustomizer(pluginModel)

    createPluginPanel()
    select(1, true)
    setEmptyState(EmptyState.NONE_SELECTED)
  }

  companion object {
    @JvmStatic
    @Deprecated("Always true")
    fun isMultiTabs(): Boolean = true

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

    @JvmStatic
    fun loadAllPluginDetails(
      marketplace: MarketplaceRequests,
      node: PluginNode,
      resultNode: PluginNode,
    ) {
      if (!node.suggestedFeatures.isEmpty()) {
        resultNode.suggestedFeatures = node.suggestedFeatures
      }

      val metadata = marketplace.loadPluginMetadata(node)
      if (metadata != null) {
        if (metadata.screenshots != null) {
          resultNode.setScreenShots(metadata.screenshots)
          resultNode.externalPluginIdForScreenShots = node.externalPluginId
        }
        metadata.toPluginNode(resultNode)
      }

      loadReviews(marketplace, node, resultNode)
      loadDependencyNames(marketplace, resultNode)
    }
  }

  val descriptorForActions: IdeaPluginDescriptor?
    get() = if (!isMarketplace || installedDescriptorForMarketplace == null) plugin else installedDescriptorForMarketplace

  fun setPlugin(plugin: IdeaPluginDescriptor?) {
    if (plugin != null) {
      this.plugin = plugin
    }
  }

  fun isShowingPlugin(pluginDescriptor: IdeaPluginDescriptor): Boolean = plugin?.pluginId == pluginDescriptor.pluginId

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
    if (isMultiTabs) {
      createTabsContentPanel()
    }
    else {
      createContentPanel()
    }

    rootPanel = OpaquePanel(BorderLayout())
    controlledByOrgNotification = createNotificationPanel(
      AllIcons.General.Warning,
      IdeBundle.message("plugins.configurable.not.allowed"))
    platformIncompatibleNotification = createNotificationPanel(
      AllIcons.General.Information,
      IdeBundle.message("plugins.configurable.plugin.unavailable.for.platform", SystemInfo.getOsName()))

    val feedbackDialogProvider = PlatformFeedbackDialogs.getInstance()
    uninstallFeedbackNotification = createFeedbackNotificationPanel { pluginId: String, pluginName: String, project: Project? ->
      feedbackDialogProvider.getUninstallFeedbackDialog(pluginId, pluginName, project)
    }
    disableFeedbackNotification = createFeedbackNotificationPanel { pluginId: String, pluginName: String, project: Project? ->
      feedbackDialogProvider.getDisableFeedbackDialog(pluginId, pluginName, project)
    }
    rootPanel!!.add(panel!!, BorderLayout.CENTER)
  }

  private fun createContentPanel() {
    panel = OpaquePanel(BorderLayout(0, scale(32)), PluginManagerConfigurable.MAIN_BG_COLOR).also {
      it.border = createMainBorder()
    }

    createHeaderPanel().add(createCenterPanel())
    createBottomPanel()
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

    nameAndButtons!!.add(JBLabel().setCopyable(true).also { myVersion1 = it })

    createButtons()
    nameAndButtons!!.setProgressDisabledButton((if (isMarketplace) installButton else updateButton)!!)

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
        val pluginName = plugin.name
        val component = e.inputEvent.component
        val project = getProjectForComponent(component)

        val feedbackDialog = createDialogWrapperFunction(pluginIdString, pluginName, project)
        if (feedbackDialog == null) {
          return@HyperlinkEventAction
        }

        val isSent = feedbackDialog.showAndGet()
        if (isSent) {
          sentFeedbackPlugins.add(plugin.pluginId)
          updateNotifications()
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

  private fun updateNotifications() {
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
      val pluginIdMap = buildPluginIdMap()
      val pluginDescriptor = pluginIdMap.getOrDefault(plugin.pluginId, null)
      if (pluginDescriptor != null && pluginModel.isUninstalled(pluginDescriptor)) {
        rootPanel.add(uninstallFeedbackNotification!!, BorderLayout.NORTH)
      }
      else if (pluginModel.isDisabledInDiff(plugin.pluginId)) {
        rootPanel.add(disableFeedbackNotification!!, BorderLayout.NORTH)
      }
    }
  }

  private fun createEnableDisableAction(action: PluginEnableDisableAction): SelectionBasedPluginModelAction.EnableDisableAction<PluginDetailsPageComponent> {
    return SelectionBasedPluginModelAction.EnableDisableAction(
      pluginModel,
      action,
      false,
      java.util.List.of(this),
      { it.descriptorForActions },
      { updateNotifications() },
    )
  }

  private fun createHeaderPanel(): JPanel {
    val header = NonOpaquePanel(BorderLayout(scale(15), 0))
    header.border = JBUI.Borders.emptyRight(20)
    panel!!.add(header, BorderLayout.NORTH)

    val iconLabel = JLabel()
    this.iconLabel = iconLabel
    iconLabel.border = JBUI.Borders.emptyTop(5)
    iconLabel.verticalAlignment = SwingConstants.TOP
    iconLabel.isOpaque = false
    header.add(iconLabel, BorderLayout.WEST)

    return header
  }

  private fun createCenterPanel(): JPanel {
    val offset = PluginManagerConfigurable.offset5()
    val centerPanel = NonOpaquePanel(VerticalLayout(offset))

    val nameAndButtons = nameAndButtons!!
    nameAndButtons.setYOffset(scale(3))
    nameAndButtons.add(nameComponent)
    createButtons()
    centerPanel.add(nameAndButtons, VerticalLayout.FILL_HORIZONTAL)
    if (!isMarketplace) {
      errorComponent = ErrorComponent()
      centerPanel.add(errorComponent!!, VerticalLayout.FILL_HORIZONTAL)
    }
    createMetricsPanel(centerPanel)

    return centerPanel
  }

  private fun createButtons() {
    val nameAndButtons = nameAndButtons!!
    nameAndButtons.addButtonComponent(RestartButton(pluginModel).also { restartButton = it })

    nameAndButtons.addButtonComponent(UpdateButton().also { updateButton = it })
    updateButton!!.addActionListener {
      pluginModel.installOrUpdatePlugin(
        this,
        descriptorForActions!!, updateDescriptor,
        ModalityState.stateForComponent(updateButton!!),
      )
    }

    nameAndButtons.addButtonComponent(InstallButton(true).also { installButton = it })
    installButton!!.addActionListener(ActionListener { _ ->
      pluginModel.installOrUpdatePlugin(this, plugin!!, null, ModalityState.stateForComponent(installButton!!))
    })

    if (isMultiTabs) {
      enableDisableController = SelectionBasedPluginModelAction.createOptionButton(
        { action -> this.createEnableDisableAction(action) },
        { this.createUninstallAction() })
      nameAndButtons.addButtonComponent(enableDisableController!!.button.also { gearButton = it })
      nameAndButtons.addButtonComponent(enableDisableController!!.bundledButton.also { myEnableDisableButton = it })
      nameAndButtons.addButtonComponent(enableDisableController!!.uninstallButton.also { myUninstallButton = it })
    }
    else {
      gearButton = SelectionBasedPluginModelAction.createGearButton(
        { action -> this.createEnableDisableAction(action) },
        { this.createUninstallAction() })
      gearButton!!.isOpaque = false
      nameAndButtons.addButtonComponent(gearButton!!)
    }

    for (component in nameAndButtons.buttonComponents) {
      component.background = PluginManagerConfigurable.MAIN_BG_COLOR
    }

    customizer.processPluginNameAndButtonsComponent(nameAndButtons)
  }

  fun setOnlyUpdateMode() {
    if (isMultiTabs) {
      nameAndButtons!!.removeButtons()
    }
    else {
      nameAndButtons!!.removeButtons()
      val parent = isEnabledForProject!!.parent
      parent?.remove(isEnabledForProject)
      panel!!.border = JBUI.Borders.empty(15, 20, 0, 0)
    }
    emptyPanel!!.border = null
  }

  private fun createMetricsPanel(centerPanel: JPanel) {
    createVersionComponent(true)

    val offset = scale(10)
    val panel1 = NonOpaquePanel(TextHorizontalLayout(offset))
    centerPanel.add(panel1)
    if (isMarketplace) {
      downloads =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Downloads, ListPluginComponent.GRAY_COLOR, true)

      rating =
        ListPluginComponent.createRatingLabel(panel1, null, "", AllIcons.Plugins.Rating, ListPluginComponent.GRAY_COLOR, true)
    }
    author = LinkPanel(panel1, false, true, null, TextHorizontalLayout.FIX_LABEL)

    isEnabledForProject = JLabel()
    isEnabledForProject!!.horizontalTextPosition = SwingConstants.LEFT
    isEnabledForProject!!.foreground = ListPluginComponent.GRAY_COLOR
    setFont(isEnabledForProject!!, true)

    val layout = if (isMarketplace) object : TextHorizontalLayout(offset) {
      override fun layoutContainer(parent: Container) {
        super.layoutContainer(parent)
        if (tagPanel != null && tagPanel!!.isVisible) {
          val baseline = tagPanel!!.getBaseline(-1, -1)
          if (baseline != -1) {
            val versionBounds = version!!.bounds
            val versionSize = version!!.preferredSize
            val versionY = tagPanel!!.y + baseline - version!!.getBaseline(versionSize.width, versionSize.height)
            version!!.setBounds(versionBounds.x, versionY, versionBounds.width, versionBounds.height)

            if (date!!.isVisible) {
              val dateBounds = date!!.bounds
              val dateSize = date!!.preferredSize
              val dateY = tagPanel!!.y + baseline - date!!.getBaseline(dateSize.width, dateSize.height)
              date!!.setBounds(dateBounds.x - scale(4), dateY, dateBounds.width, dateBounds.height)
            }
          }
        }
      }
    }
    else TextHorizontalLayout(scale(7))

    val panel2: JPanel = NonOpaquePanel(layout)
    panel2.border = JBUI.Borders.emptyTop(5)
    panel2.add(TagPanel(searchListener).also { tagPanel = it })
    (if (isMarketplace) panel2 else panel1).add(version)
    panel2.add(isEnabledForProject)

    date =
      ListPluginComponent.createRatingLabel(panel2, TextHorizontalLayout.FIX_LABEL, "", null, ListPluginComponent.GRAY_COLOR, true)
    centerPanel.add(panel2)
  }

  private fun createVersionComponent(tiny: Boolean) {
    // text field without horizontal margins
    val version = object : JTextField() {
      override fun setBorder(border: Border?) {
        super.setBorder(null)
      }

      override fun updateUI() {
        super.updateUI()
        version?.let {
          setFont(it, tiny)
        }
        if (versionSize != null) {
          setFont(versionSize!!, tiny)
        }
      }
    }

    this.version = version
    version.putClientProperty("TextFieldWithoutMargins", true)
    version.isEditable = false
    setFont(version, tiny)
    version.setBorder(null)
    version.setOpaque(false)
    version.setForeground(ListPluginComponent.GRAY_COLOR)
    version.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        val caretPosition = version.caretPosition
        version.selectionStart = caretPosition
        version.selectionEnd = caretPosition
      }
    })

    versionSize = JLabel()
    setFont(versionSize!!, tiny)
  }

  private fun createScrollPane(component: JComponent): JBScrollPane {
    val scrollPane = JBScrollPane(component)
    scrollPane.verticalScrollBar.background = PluginManagerConfigurable.MAIN_BG_COLOR
    scrollPane.border = JBUI.Borders.empty()
    scrollPanes.add(scrollPane)
    return scrollPane
  }

  private fun createBottomPanel() {
    val bottomPanel: JPanel = OpaquePanel(VerticalLayout(PluginManagerConfigurable.offset5()), PluginManagerConfigurable.MAIN_BG_COLOR)
    bottomPanel.border = JBUI.Borders.empty(0, 0, 15, 20)

    bottomScrollPane = createScrollPane(bottomPanel)
    panel!!.add(bottomScrollPane)

    bottomPanel.add(licensePanel)
    licensePanel.border = JBUI.Borders.emptyBottom(20)

    if (isMarketplace) {
      homePage = LinkPanel(bottomPanel, false)
      bottomPanel.add(JLabel())
    }

    val constraints: Any = scale(700)
    bottomPanel.add(createDescriptionComponent(createHtmlImageViewHandler()).also { descriptionComponent = it }, constraints)
    changeNotesPanel = ChangeNotesPanel(bottomPanel, constraints, descriptionComponent!!)

    val separator = JLabel()
    separator.border = JBUI.Borders.emptyTop(20)
    bottomPanel.add(separator)

    if (isMarketplace) {
      bottomPanel.add(JLabel().also { mySize = it })
    }
    else {
      homePage = LinkPanel(bottomPanel, false)
    }
  }

  private fun createHtmlImageViewHandler(): Consumer<View> {
    return Consumer { view: View ->
      val width = view.getPreferredSpan(View.X_AXIS)
      if (width < 0 || width > bottomScrollPane!!.width) {
        bottomScrollPane!!.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      }
    }
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
    newReviewLink.showWithBrowseUrl(IdeBundle.message("plugins.new.review.action"), false) {
      val pluginId = plugin!!.pluginId
      val installedPlugin = getPlugin(pluginId)
      getPluginWriteReviewUrl(pluginId, installedPlugin?.version)
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
      val installedNode = installedPluginMarketplaceNode
      val node = installedNode ?: component!!.pluginDescriptor as PluginNode
      val reviewComments = node.reviewComments!!
      val page = reviewComments.nextPage
      ProcessIOExecutorService.INSTANCE.execute {
        val items = MarketplaceRequests.getInstance().loadPluginReviews(node, page)
        ApplicationManager.getApplication().invokeLater({
                                                          if (showComponent != component) {
                                                            return@invokeLater
                                                          }
                                                          if (items != null) {
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
    val size = selection.size
    showPlugin(if (size == 1) selection[0] else null, size > 1)
  }

  fun showPlugin(component: ListPluginComponent?) {
    showPlugin(component, false)
  }

  private fun showPlugin(component: ListPluginComponent?, multiSelection: Boolean) {
    if (showComponent == component && (component == null || updateDescriptor === component.myUpdateDescriptor)) {
      return
    }
    showComponent = component

    if (indicator != null) {
      MyPluginModel.removeProgress(descriptorForActions!!, indicator!!)
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
      val descriptor = component.pluginDescriptor
      if (descriptor is PluginNode) {
        if (!descriptor.detailsLoaded()) {
          syncLoading = false
          doLoad(component) {
            val marketplace = MarketplaceRequests.getInstance()
            val pluginNode = marketplace.loadPluginDetails(descriptor)
            if (pluginNode != null) {
              coroutineContext.ensureActive()
              loadAllPluginDetails(marketplace = marketplace, node = descriptor, resultNode = pluginNode)
              component.pluginDescriptor = pluginNode
            }
          }
        }
        else if (!descriptor.isConverted &&
                 (descriptor.screenShots == null || descriptor.reviewComments == null || descriptor.dependencyNames == null)) {
          syncLoading = false
          doLoad(component) {
            val marketplace = MarketplaceRequests.getInstance()
            if (descriptor.screenShots == null && descriptor.externalPluginIdForScreenShots != null) {
              val metadata = marketplace.loadPluginMetadata(descriptor.externalPluginIdForScreenShots!!)
              if (metadata != null) {
                if (metadata.screenshots != null) {
                  descriptor.setScreenShots(metadata.screenshots)
                }
                metadata.toPluginNode(descriptor)
              }
            }

            if (descriptor.reviewComments == null) {
              coroutineContext.ensureActive()
              loadReviews(marketplace, descriptor, descriptor)
            }
            if (descriptor.dependencyNames == null) {
              coroutineContext.ensureActive()
              loadDependencyNames(marketplace, descriptor)
            }
          }
        }
        else if (!descriptor.isConverted && !isMarketplace) {
          component.setInstalledPluginMarketplaceNode(descriptor)
        }
      }
      else if (!descriptor.isBundled && component.installedPluginMarketplaceNode == null) {
        syncLoading = false
        doLoad(component) {
          val marketplace = MarketplaceRequests.getInstance()
          val node = marketplace.getLastCompatiblePluginUpdate(component.pluginDescriptor.pluginId) ?: return@doLoad

          coroutineContext.ensureActive()
          val update = getLastCompatiblePluginUpdate(java.util.Set.of(component.pluginDescriptor.pluginId))
          if (!update.isEmpty()) {
            val compatibleUpdate = update[0]
            node.externalPluginId = compatibleUpdate.externalPluginId
            node.externalUpdateId = compatibleUpdate.externalUpdateId
          }

          coroutineContext.ensureActive()

          val fullNode = marketplace.loadPluginDetails(node)
          if (fullNode != null) {
            loadAllPluginDetails(marketplace, node, fullNode)
            component.setInstalledPluginMarketplaceNode(fullNode)
          }
        }
      }

      if (syncLoading) {
        showPluginImpl(component.pluginDescriptor, component.myUpdateDescriptor)
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
          showPluginImpl(component.pluginDescriptor, component.myUpdateDescriptor)
          pluginCardOpened(component.pluginDescriptor, component.group)
        }
      }
    }
  }

  fun showPluginImpl(pluginDescriptor: IdeaPluginDescriptor, updateDescriptor: IdeaPluginDescriptor?) {
    plugin = pluginDescriptor
    val policy = PluginManagementPolicy.getInstance()
    this.updateDescriptor = if (updateDescriptor != null && policy.canEnablePlugin(updateDescriptor)) updateDescriptor else null
    isPluginCompatible = getIncompatibleOs(pluginDescriptor) == null
    isPluginAvailable = isPluginCompatible && policy.canEnablePlugin(updateDescriptor)
    if (isMarketplace && isMultiTabs) {
      installedDescriptorForMarketplace = findPlugin(plugin!!.pluginId)
      nameAndButtons!!.setProgressDisabledButton((if (this.updateDescriptor == null) installButton else updateButton)!!)
    }
    showPlugin()

    select(0, true)

    var suggestedCommercialIde: String? = null

    if (plugin is PluginNode) {
      suggestedCommercialIde = (plugin as PluginNode).suggestedCommercialIde
      if (suggestedCommercialIde != null) {
        installButton!!.isVisible = false
      }
    }

    if (plugin != null) {
      customizer.processShowPlugin(plugin!!)
    }

    mySuggestedIdeBanner.suggestIde(suggestedCommercialIde, plugin!!.pluginId)
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

  private fun showPlugin() {
    val plugin = this.plugin!!
    val text: @NlsSafe String = "<html><span>" + plugin.name + "</span></html>"
    nameComponent.text = text
    nameComponent.foreground = null
    updateNotifications()
    updateIcon()

    errorComponent?.isVisible = false

    updateButtons()

    val descriptorForActions = descriptorForActions!!
    var version = descriptorForActions.version
    if (descriptorForActions.isBundled && !descriptorForActions.allowBundledUpdate()) {
      version = IdeBundle.message("plugin.version.bundled") + (if (Strings.isEmptyOrSpaces(version)) "" else " $version")
    }
    if (updateDescriptor != null) {
      version = NewUiUtil.getVersion(descriptorForActions, updateDescriptor!!)
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

    tagPanel!!.setTags(PluginManagerConfigurable.getTags(plugin))

    if (isMarketplace) {
      showMarketplaceData(plugin)
      updateMarketplaceTabsVisible(show = plugin is PluginNode && !plugin.isConverted)
    }
    else {
      val node = installedPluginMarketplaceNode
      updateMarketplaceTabsVisible(node != null)
      if (node != null) {
        showMarketplaceData(node)
      }
      updateEnabledForProject()
    }

    val vendor = if (plugin.isBundled) null else plugin.vendor?.trim()
    val organization = if (plugin.isBundled) null else plugin.organization?.trim()
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

    unavailableWithoutSubscriptionBanner?.isVisible = showComponent?.isNotFreeInFreeMode == true
    partiallyAvailableBanner?.isVisible = showComponent?.isNotFreeInFreeMode != true &&
                                          PluginManagerCore.dependsOnUltimateOptionally(showComponent?.pluginDescriptor)

    val homepage = getPluginHomepage(plugin.pluginId)

    if (plugin.isBundled && !plugin.allowBundledUpdate() || !isPluginFromMarketplace || homepage == null) {
      homePage!!.hide()
    }
    else {
      homePage!!.showWithBrowseUrl(
        IdeBundle.message("plugins.configurable.plugin.homepage.link"),
        true
      ) { homepage }
    }

    if (date != null) {
      val date = if (descriptorForActions is PluginNode) descriptorForActions.presentableDate else null
      this.date!!.text = if (isMultiTabs) IdeBundle.message("plugins.configurable.release.date.0", date) else date
      this.date!!.isVisible = date != null
    }

    if (suggestedFeatures != null) {
      var feature: String? = null
      if (isMarketplace && plugin is PluginNode) {
        feature = plugin.suggestedFeatures.firstOrNull()
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
      myImagesComponent!!.show((node ?: plugin))
    }

    ApplicationManager.getApplication().invokeLater({
                                                      IdeEventQueue.getInstance().flushQueue()
                                                      for (scrollPane in scrollPanes) {
                                                        (scrollPane.verticalScrollBar as JBScrollBar).setCurrentValue(0)
                                                      }
                                                    }, ModalityState.any())

    if (MyPluginModel.isInstallingOrUpdate(plugin)) {
      showProgress()
    }
    else {
      fullRepaint()
    }
  }

  private fun showMarketplaceData(descriptor: IdeaPluginDescriptor?) {
    var rating: String? = null
    var downloads: String? = null
    var size: String? = null
    var requiredPluginNames: Collection<String> = emptyList()

    if (descriptor is PluginNode) {
      rating = descriptor.presentableRating
      downloads = descriptor.presentableDownloads
      size = descriptor.presentableSize

      if (reviewPanel != null) {
        updateReviews(descriptor)
      }

      updateUrlComponent(forumUrl, "plugins.configurable.forum.url", descriptor.forumUrl)
      updateUrlComponent(licenseUrl, "plugins.configurable.license.url", descriptor.licenseUrl)
      updateUrlComponent(bugtrackerUrl, "plugins.configurable.bugtracker.url", descriptor.bugtrackerUrl)
      updateUrlComponent(documentationUrl, "plugins.configurable.documentation.url", descriptor.documentationUrl)
      updateUrlComponent(sourceCodeUrl, "plugins.configurable.source.code", descriptor.sourceCodeUrl)
      updateUrlComponent(pluginReportUrl, "plugins.configurable.report.marketplace.plugin", descriptor.reportPluginUrl)

      vendorInfoPanel!!.show(descriptor)

      requiredPluginNames = descriptor.dependencyNames ?: emptyList()

      if (customRepoForDebug != null) {
        val customRepo = descriptor.repositoryName
        customRepoForDebug!!.text = "Custom Repository: $customRepo" //NON-NLS
        customRepoForDebug!!.isVisible = customRepo != null
      }
    }

    this.rating!!.text = if (isMultiTabs) IdeBundle.message("plugins.configurable.rate.0", rating) else rating
    this.rating!!.isVisible = rating != null

    this.downloads!!.text = if (isMultiTabs) IdeBundle.message("plugins.configurable.downloads.0", downloads) else downloads
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

  private val installedPluginMarketplaceNode: PluginNode?
    get() = if (showComponent == null) null else showComponent!!.installedPluginMarketplaceNode

  private fun updateReviews(pluginNode: PluginNode) {
    val comments = pluginNode.reviewComments

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
        updateNotifications()
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

      val message: String = if (descriptor is PluginNode) {
        val ideProductCode = ApplicationInfoImpl.getShadowInstanceImpl().build.productCode

        val trialPeriod = descriptor.getTrialPeriodByProductCode(ideProductCode)
        val isFreemium = descriptor.tags.contains(Tags.Freemium.name)
        requiresCommercialIde = descriptor.suggestedCommercialIde != null

        getPaidPluginLicenseText(isFreemium, trialPeriod)
      }
      else {
        IdeBundle.message("label.install.paid.without.trial")
      }

      licensePanel.showBuyPluginWithText(
        message, false, false,
        { descriptor }, false,
        !requiresCommercialIde // if the descriptor requires a commercial IDE, we do not show trial/price message
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
        if (ApplicationManager.getApplication().isEAP) {
          tagPanel!!.setFirstTagTooltip(IdeBundle.message("tooltip.license.not.required.for.eap.version"))
          licensePanel.hideWithChildren()
          return
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

  fun updateAll() {
    if (plugin != null) {
      if (indicator != null) {
        MyPluginModel.removeProgress(descriptorForActions!!, indicator!!)
        hideProgress(false, false)
      }
      showPluginImpl(plugin!!, updateDescriptor)
    }
  }

  private fun updateButtons() {
    if (!isPluginAvailable) {
      restartButton!!.isVisible = false
      installButton!!.isVisible = false
      updateButton!!.isVisible = false
      gearButton!!.isVisible = false
      myUninstallButton?.isVisible = false
      if (isMultiTabs) {
        myEnableDisableButton!!.isVisible = false
      }
      return
    }

    val installedWithoutRestart = InstalledPluginsState.getInstance().wasInstalledWithoutRestart(plugin!!.pluginId)
    if (isMarketplace) {
      val installed = InstalledPluginsState.getInstance().wasInstalled(plugin!!.pluginId)
      restartButton!!.isVisible = installed

      installButton!!.setEnabled(getPlugin(
        plugin!!.pluginId) == null && !installedWithoutRestart,
                                 IdeBundle.message("plugins.configurable.installed"))
      installButton!!.isVisible = !installed

      updateButton!!.isVisible = false
      myUninstallButton?.isVisible = false
      if (isMultiTabs) {
        if (installed || installedDescriptorForMarketplace == null) {
          gearButton!!.isVisible = false
          myEnableDisableButton!!.isVisible = false
        }
        else {
          val state = getDeletedState(
            installedDescriptorForMarketplace!!)
          val uninstalled = state[0]
          val uninstalledWithoutRestart = state[1]

          installButton!!.isVisible = false

          if (uninstalled) {
            if (uninstalledWithoutRestart) {
              restartButton!!.isVisible = false
              installButton!!.isVisible = true
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
          updateButton!!.isVisible = !uninstalled && updateDescriptor != null && !installedWithoutRestart
          updateEnableForNameAndIcon()
          updateErrors()
        }
      }
      else {
        gearButton!!.isVisible = false
      }
    }
    else {
      installButton!!.isVisible = false

      val state = getDeletedState(plugin!!)
      val uninstalled = state[0]
      val uninstalledWithoutRestart = state[1]

      if (uninstalled) {
        if (uninstalledWithoutRestart) {
          restartButton!!.isVisible = false
          installButton!!.isVisible = true
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
      if (isMultiTabs) {
        val bundled = plugin!!.isBundled
        val isEssential = ApplicationInfo.getInstance().isEssentialPlugin(
          plugin!!.pluginId)
        gearButton!!.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode != true
        myEnableDisableButton!!.isVisible = bundled
        myEnableDisableButton!!.isEnabled = !isEssential && showComponent?.isNotFreeInFreeMode != true
        myUninstallButton?.isVisible = !uninstalled && !bundled && showComponent?.isNotFreeInFreeMode == true
      }
      else {
        gearButton!!.isVisible = !uninstalled
      }

      updateEnableForNameAndIcon()
      updateErrors()
    }
  }

  private fun updateIcon(errors: List<HtmlChunk?> = pluginModel.getErrors(descriptorForActions!!)) {
    if (iconLabel == null) {
      return
    }

    val hasErrors = !isMarketplace && !errors.isEmpty()

    iconLabel!!.isEnabled = isMarketplace || pluginModel.isEnabled(plugin!!)
    iconLabel!!.icon = pluginModel.getIcon(plugin!!, true, hasErrors, false)
    iconLabel!!.disabledIcon = pluginModel.getIcon(plugin!!, true, hasErrors, true)
  }

  private fun updateErrors() {
    if (showComponent?.isNotFreeInFreeMode != true) {
      val errors = pluginModel.getErrors(descriptorForActions!!)
      updateIcon(errors)
      errorComponent!!.setErrors(errors) { this.handleErrors() }
    }
  }

  private fun handleErrors() {
    pluginModel.enableRequiredPlugins(descriptorForActions!!)

    updateIcon()
    updateEnabledState()
    fullRepaint()
  }

  fun showProgress() {
    indicator = OneLineProgressIndicator(false)
    indicator!!.setCancelRunnable { pluginModel.finishInstall(descriptorForActions!!, null, false, false, true) }
    nameAndButtons!!.setProgressComponent(null, indicator!!.createBaselineWrapper())

    MyPluginModel.addProgress(descriptorForActions!!, indicator!!)

    fullRepaint()
  }

  private fun fullRepaint() {
    doLayout()
    revalidate()
    repaint()
  }

  fun hideProgress(success: Boolean, restartRequired: Boolean) {
    indicator = null
    nameAndButtons!!.removeProgressComponent()

    if (success) {
      if (restartRequired) {
        updateAfterUninstall(true)
      }
      else {
        val installButton = installButton
        if (installButton != null) {
          installButton.setEnabled(false, IdeBundle.message("plugin.status.installed"))
          if (isMultiTabs && installButton.isVisible) {
            installedDescriptorForMarketplace = findPlugin(plugin!!.pluginId)
            installedDescriptorForMarketplace?.let {
              installButton.isVisible = false
              myVersion1!!.text = it.getVersion()
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

    fullRepaint()
  }

  private fun updateEnableForNameAndIcon() {
    val enabled = pluginModel.isEnabled(descriptorForActions!!)
    nameComponent.foreground = if (enabled) null else ListPluginComponent.DisabledColor
    if (iconLabel != null) {
      iconLabel!!.isEnabled = enabled
    }
  }

  fun updateEnabledState() {
    if ((isMarketplace && installedDescriptorForMarketplace == null) || plugin == null) {
      return
    }

    if (!pluginModel.isUninstalled(descriptorForActions!!)) {
      if (enableDisableController != null) {
        enableDisableController!!.update()
      }
      if (isMultiTabs) {
        val bundled = descriptorForActions!!.isBundled
        gearButton!!.isVisible = !bundled
        myEnableDisableButton!!.isVisible = bundled
      }
      else {
        gearButton!!.isVisible = true
      }
    }

    updateNotifications()
    updateEnableForNameAndIcon()
    updateErrors()
    updateEnabledForProject()

    updateButton!!.isVisible = updateDescriptor != null

    fullRepaint()
  }

  fun updateAfterUninstall(showRestart: Boolean) {
    installButton!!.isVisible = false
    updateButton!!.isVisible = false
    gearButton!!.isVisible = false
    myUninstallButton?.isVisible = false
    if (myEnableDisableButton != null) {
      myEnableDisableButton!!.isVisible = false
    }
    restartButton!!.isVisible = isPluginAvailable && showRestart

    if (!showRestart && InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(descriptorForActions!!.pluginId)) {
      installButton!!.isVisible = true
      installButton!!.setEnabled(false, IdeBundle.message("plugins.configurable.uninstalled"))
    }

    if (!showRestart) {
      updateNotifications()
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

private fun loadReviews(marketplace: MarketplaceRequests, node: PluginNode, resultNode: PluginNode) {
  val reviewComments = PageContainer<PluginReviewComment>(20, 0)
  marketplace.loadPluginReviews(node, reviewComments.nextPage)?.let {
    reviewComments.addItems(it)
  }
  resultNode.setReviewComments(reviewComments)
}

private fun loadDependencyNames(marketplace: MarketplaceRequests, resultNode: PluginNode) {
  resultNode.dependencyNames = resultNode.dependencies.asSequence()
    .filter { !it.isOptional }
    .map(IdeaPluginDependency::pluginId)
    .filter { isNotPlatformAlias(it) }
    .map { pluginId ->
      findPlugin(pluginId)?.let {
        return@map it.name
      }

      marketplace.getLastCompatiblePluginUpdate(pluginId)?.name ?: pluginId.idString
    }
    .toList()
}

private fun isNotPlatformAlias(pluginId: PluginId): Boolean {
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

private fun getDeletedState(descriptor: IdeaPluginDescriptor): BooleanArray {
  val pluginId = descriptor.pluginId
  var uninstalled = NewUiUtil.isDeleted(descriptor)
  val uninstalledWithoutRestart = InstalledPluginsState.getInstance().wasUninstalledWithoutRestart(pluginId)
  if (!uninstalled) {
    val pluginsState = InstalledPluginsState.getInstance()
    uninstalled = pluginsState.wasInstalled(pluginId) || pluginsState.wasUpdated(pluginId)
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

private fun setFont(component: JComponent, tiny: Boolean) {
  component.font = labelFont
  if (tiny) {
    PluginManagerConfigurable.setTinyFont(component)
  }
}