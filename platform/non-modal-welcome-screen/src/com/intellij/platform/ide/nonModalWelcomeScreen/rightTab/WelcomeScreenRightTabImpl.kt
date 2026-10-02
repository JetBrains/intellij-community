// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.diagnostic.ExceptionAutoReportUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.gdpr.showDataSharingOptionsDialog
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.UI
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenPaintTracker
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.WelcomeContent
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenStartupSpanTracer
import com.intellij.platform.ide.nonModalWelcomeScreen.welcomeScreenStartupTracer
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.border.Border
import javax.swing.border.CompoundBorder
import kotlin.math.max

private val LOG = fileLogger()

/**
 * The welcome right tab. Construct it on the UI thread.
 *
 * @param body the default body that [prepareDefaultBody] prepared, or `null` when the preparation failed. The constructor places it.
 * Without a body, the tab shows its footer only.
 * @param owner the disposable that holds the sections of [body]. The tab registers itself under it, so dispose [owner], not the tab.
 */
internal class WelcomeScreenRightTabImpl(
  project: Project,
  contentProvider: WelcomeRightTabContentProvider,
  body: PreparedBody?,
  @JvmField val owner: Disposable,
) : WelcomeScreenRightTab(project, contentProvider) {

  private val paintTracker = WelcomeScreenPaintTracker.getInstance(project)

  /**
   * Tells if the tab holds its default body. The first paint after that calls [WelcomeScreenPaintTracker.rightPainted].
   * Read and written on the EDT only.
   */
  private var isBodyPlaced = false
  private var isBodyPainted = false

  override val component: JPanel = object : JPanel() {
    override fun paint(g: Graphics) {
      super.paint(g)
      if (isBodyPlaced && !isBodyPainted) {
        isBodyPainted = true
        paintTracker.rightPainted()
      }
    }
  }

  private val contentPanel = BorderLayoutPanel()

  private val sectionContents: List<WelcomeScreenFeatureUI.Content> = body?.sections?.map { it.content }.orEmpty()

  /**
   * A child scope of the provider scope. Cancelled with the owner, so a coroutine of the tab never outlives the tab.
   */
  private val scope: CoroutineScope = run {
    val parentContext = contentProvider.coroutineScope.coroutineContext
    CoroutineScope(parentContext + SupervisorJob(parentContext.job) + CoroutineName("welcome right tab"))
  }

  init {
    Disposer.register(owner, this)
    scope.coroutineContext.job.cancelOnDispose(this)

    contentPanel.isOpaque = false

    component.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    component.isFocusTraversalPolicyProvider = true
    component.isFocusCycleRoot = true
    component.isRequestFocusEnabled = true

    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner == null || !UIUtil.isAncestor(component, focusOwner)) {
          // The tab's own focus target, so a click reaches a section that states one. The traversal policy alone
          // would take the first focusable child instead, which is a footer control.
          val newFocus = getPreferredFocusedComponent()
          if (newFocus !== component) {
            ApplicationManager.getApplication().invokeLater {
              newFocus.requestFocusInWindow()
            }
          }
        }
      }
    })

    component.background = JBUI.CurrentTheme.EditorTabs.background()
    component.isOpaque = true

    component.layout = object : AbstractLayoutManager() {
      override fun preferredLayoutSize(container: Container): Dimension {
        var width = 0
        var height = JBUI.scale(32)
        val size = container.componentCount

        for (i in 0..<size) {
          val preferredSize = container.getComponent(i).preferredSize
          width = max(width, preferredSize.width)
          height += preferredSize.height
        }
        return Dimension(width, height)
      }

      override fun minimumLayoutSize(container: Container) = preferredLayoutSize(container)

      override fun layoutContainer(container: Container) {
        val count = container.componentCount
        if (count > 1) {
          val centeredChild = container.getComponent(0)
          val centeredSize = centeredChild.preferredSize
          val fullSize = container.size

          val bottomChild = container.getComponent(1)
          val bottomSize = bottomChild.preferredSize
          val offset = JBUI.scale(16)

          var topY = (fullSize.height - bottomSize.height - offset - centeredSize.height) / 2
          // A column taller than the tab reads from its top. Centring it would take the title off the top edge.
          topY = max(topY, 0)
          centeredChild.bounds = Rectangle((fullSize.width - centeredSize.width) / 2,
                                           topY, centeredSize.width, centeredSize.height)

          val bottomY = max(fullSize.height - bottomSize.height - offset, centeredChild.y + centeredSize.height + offset)
          bottomChild.bounds = Rectangle((fullSize.width - bottomSize.width) / 2,
                                         bottomY, bottomSize.width, bottomSize.height)
        }
      }
    }
    component.add(contentPanel)

    if (body != null) {
      welcomeScreenStartupSpanTracer.spanBuilder("welcome right tab body: EDT build").use {
        placeDefaultBody(body)
      }
      isBodyPlaced = true
    }

    createFooter()

    component.dropTarget = DropTarget(component, object : DropTargetAdapter() {
      override fun drop(e: DropTargetDropEvent) {
        e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
        val files = FileCopyPasteUtil.getFiles(e.transferable)
        e.dropComplete(contentProvider.getFileDragAndDropHandler().openFiles(project, files))
      }
    })
  }

  override fun getPreferredFocusedComponent(): JComponent {
    if (!WelcomeScreenTabFocusState.getInstance(project).contentFocusEnabled) {
      // The tab component itself takes no focus. This keeps the startup focus on the left project view (IJPL-248588).
      return component
    }
    return sectionFocusTarget()
           ?: IdeFocusTraversalPolicy.getPreferredFocusedComponent(component)
           ?: component
  }

  /**
   * Asks each section for its focus target, and takes the first one. A section that fails does not stop the others.
   */
  private fun sectionFocusTarget(): JComponent? {
    return sectionContents.firstNotNullOfOrNull { content ->
      try {
        content.preferredFocusedComponent?.invoke()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error("Cannot read the focus target of a welcome right tab section", e)
        null
      }
    }
  }

  /** The [Disposer] tree disposes the children of the tab. The tab holds nothing else. */
  override fun dispose() {
  }

  private fun placeDefaultBody(body: PreparedBody) {
    val sections = body.sections
    if (sections.isEmpty()) {
      createDefaultContent(contentPanel, body.featureModels, false)
    }
    else {
      val contentsPanel = JPanel(VerticalLayout(0))
      contentsPanel.isOpaque = false
      contentsPanel.border = JBUI.Borders.emptyBottom(40)
      createFeatureSections(contentsPanel, sections.map { it.content })
      contentPanel.addToCenter(contentsPanel)

      val bottomPanel = BorderLayoutPanel()
      bottomPanel.isOpaque = false
      createDefaultContent(bottomPanel, body.featureModels, true)
      contentPanel.addToBottom(bottomPanel)
    }
  }

  private fun createFeatureSections(parentPanel: JPanel, contents: List<WelcomeScreenFeatureUI.Content>) {
    for (content in contents) {
      parentPanel.add(content.component, VerticalLayout.CENTER)
    }
  }

  private fun createDefaultContent(
    parentPanel: BorderLayoutPanel,
    featureModels: List<WelcomeRightTabContentProvider.FeatureButtonModel>,
    extraContent: Boolean,
  ) {
    parentPanel.addToCenter(createFeatureGrid(featureModels, extraContent))

    val additionalPanel = JPanel(VerticalLayout(0))
    additionalPanel.isOpaque = false

    createAdditionalComponents(additionalPanel)
    createSingleBanner(additionalPanel, extraContent)

    if (additionalPanel.componentCount > 0) {
      parentPanel.addToBottom(additionalPanel)
    }
  }

  private fun createFeatureGrid(featureModels: List<WelcomeRightTabContentProvider.FeatureButtonModel>, extraContent: Boolean): JPanel {
    val buttonPanel = JPanel(GridLayout())
    buttonPanel.isOpaque = false

    val wrapper = Wrapper(true)
    wrapper.add(buttonPanel)

    val gridBuilder = RowsGridBuilder(buttonPanel)

    val buttonHeight = JBUI.scale(if (extraContent) 36 else 48)

    for (row in featureModels.chunked(contentProvider.buttonsPerRow)) {
      for (model in row) {
        val button = DisclosureButton()
        button.arrowIcon = null
        button.buttonHeight = buttonHeight
        button.isOpaque = false
        button.text = model.text
        button.icon = model.icon

        button.addActionListener { model.onClick(project, contentProvider.coroutineScope) }

        gridBuilder.cell(button, gaps = UnscaledGaps(right = 10))
      }
      gridBuilder.row()
    }

    return wrapper
  }

  private fun createAdditionalComponents(parentPanel: JPanel) {
    val additionalComponents = contentProvider.getAdditionalComponents(project)
    if (additionalComponents.isNotEmpty()) {
      val additionalPanel = JPanel(HorizontalLayout(16))
      additionalPanel.isOpaque = false
      additionalPanel.border = JBUI.Borders.emptyTop(24)
      parentPanel.add(additionalPanel, VerticalLayout.CENTER)

      for (row in additionalComponents) {
        for (model in row) {
          val label = when (model) {
            is WelcomeContent.Text -> {
              JLabel(model.text, model.icon, SwingConstants.LEADING)
            }
            is WelcomeContent.Link -> {
              LinkLabel<Any>(model.text, AllIcons.Ide.External_link_arrow) { _, _ -> model.onClick(project) }
            }
          }
          label.horizontalTextPosition = SwingConstants.LEFT
          additionalPanel.add(label, HorizontalLayout.Group.CENTER)
        }
      }
    }
  }

  private fun createSingleBanner(parentPanel: JPanel, extraContent: Boolean) {
    val singleBanner = WelcomeScreenRightTabBannerProvider.createSurveyBanner(project)
    if (singleBanner is Disposable) {
      Disposer.register(this, singleBanner)
    }
    if (singleBanner != null) {
      val wrapper = Wrapper(singleBanner)
      wrapper.border = JBUI.Borders.emptyTop(if (extraContent) 32 else 52)
      // A provider may return a placeholder that starts invisible and only becomes visible once some
      // async check resolves whether there's anything to show (e.g. GoFeaturesWelcomeRightTabBannerProvider).
      // Mirroring visibility onto the wrapper keeps its border from reserving space while that's the case.
      wrapper.isVisible = singleBanner.isVisible
      singleBanner.addComponentListener(object : ComponentAdapter() {
        override fun componentShown(e: ComponentEvent) {
          wrapper.isVisible = true
          wrapper.revalidate()
        }

        override fun componentHidden(e: ComponentEvent) {
          wrapper.isVisible = false
          wrapper.revalidate()
        }
      })
      parentPanel.add(wrapper, VerticalLayout.CENTER)
    }
  }

  private fun createFooter() {
    val coroutineScope = contentProvider.coroutineScope
    val models = createFooterModels()
    val actions = models.mapIndexed { index, model ->
      val addBorder = index < models.size - 1
      when (model) {
        is ComboBoxInfoPanelModel -> ComboBoxInfoPanelAction(model, addBorder)

        is ButtonInfoPanelModel -> ButtonInfoPanelAction(model, project, coroutineScope, addBorder)
      }
    }
    val toolbar = ActionManager.getInstance().createActionToolbar("WelcomeScreenRightTabFooter", DefaultActionGroup(actions), true)
    toolbar.targetComponent = component
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    toolbar.component.isOpaque = false

    val footerWrapper = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP, SwingConstants.CENTER))
    footerWrapper.isOpaque = false
    component.add(footerWrapper)
    footerWrapper.add(toolbar.component)

    addErrorReportAlert(footerWrapper)
  }

  private fun addErrorReportAlert(footerWrapper: JPanel) {
    fun reconcileErrorReportAlert(alert: JPanel) {
      scope.launch {
        LOG.debug("Recheck if error reporting is enabled, hide alert otherwise")

        if (!ExceptionAutoReportUtil.isAutoReportAllowedByUser()) {
          withContext(Dispatchers.UI) {
            alert.parent?.remove(alert)
          }

          LOG.debug("Error reporting is disabled, hide alert")
        }
      }
    }

    if (ExceptionAutoReportUtil.isConsentAllowedToBeVisible) {
      scope.launch {
        if (ExceptionAutoReportUtil.isAutoReportAllowedByUser()) {
          if (ExceptionAutoReportUtil.needNotificationOfDataCollection()) {
            LOG.info("Notify user that error reports are sent automatically")

            withContext(Dispatchers.UI) {
              val label = JLabel(IdeBundle.message("welcome.screen.ea.auto.report.alert"))
              label.foreground = UIUtil.getInactiveTextColor()

              val horizontalPanel = JPanel(HorizontalLayout(UIUtil.DEFAULT_HGAP))
              horizontalPanel.isOpaque = false
              horizontalPanel.add(label)
              horizontalPanel.add(ActionLink(IdeBundle.message("welcome.screen.ea.auto.report.configure.link")) {
                ExceptionAutoReportUtil.recordUserVisitedConfigure()

                if (showDataSharingOptionsDialog()) {
                  reconcileErrorReportAlert(horizontalPanel)
                }
              })

              footerWrapper.add(horizontalPanel)
              footerWrapper.invalidate()
              footerWrapper.repaint()

              horizontalPanel.launchOnShow("isAutoReportAllowedByUser") {
                reconcileErrorReportAlert(horizontalPanel)
              }
            }

            ExceptionAutoReportUtil.recordUserNotifiedOfDataCollection()
          }
          else {
            LOG.debug("User is already notified enough of error reports collection")
          }
        }
      }
    }
  }

  private fun createFooterModels(): List<InfoPanelModel> {
    return buildList {
      add(ButtonInfoPanelModel(WelcomeRightTabContentProvider.InfoButtonModel(IdeBundle.message("welcome.screen.plugins.title"),
                                                                              AllIcons.Nodes.Plugin) { project, _ ->
        PluginManagerConfigurable.showSettingsDialogFromWelcomeScreen(project)
      }))
      add(ComboBoxInfoPanelModel(ThemeModel.getIcon(), "welcome.screen.right.tab.theme.switch.prefix", ThemeModel()))
      add(ComboBoxInfoPanelModel(AllIcons.General.Keyboard, "welcome.screen.right.tab.keymap.switch.prefix", KeymapModel()))
      addAll(contentProvider.getAdditionalInfoButtonModels(project).map { ButtonInfoPanelModel(it) })
    }
  }
}

/** A section of the tab, and the key of the feature that stated it. */
internal class FeatureSection(@JvmField val featureKey: String, @JvmField val content: WelcomeScreenFeatureUI.Content)

/**
 * The default body of the tab: the sections and the feature buttons.
 *
 * [prepareDefaultBody] prepares it before the tab exists. The owner given to [prepareDefaultBody] holds the disposables of the sections.
 */
internal class PreparedBody(
  @JvmField val sections: List<FeatureSection>,
  @JvmField val featureModels: List<WelcomeRightTabContentProvider.FeatureButtonModel>,
)

/**
 * Prepares the default body of the tab off the EDT.
 *
 * The disposable of each section is registered under [owner]. Returns `null` when the preparation fails. The failure is logged.
 */
internal suspend fun prepareDefaultBody(
  project: Project,
  contentProvider: WelcomeRightTabContentProvider,
  owner: Disposable,
): PreparedBody? {
  try {
    return coroutineScope {
      val registeredFeatureIds = async(welcomeScreenStartupTracer.span("welcome right tab body: feature ids")) {
        WelcomeScreenFeatureApi.getInstance().getAvailableFeatureIds().toHashSet()
      }
      val outcomes = prepareFeatures(project, WelcomeScreenFeatureUI.features(), registeredFeatureIds, owner)
      val offeredFeatures = OfferedFeatures(
        registeredFeatureIds = registeredFeatureIds.await(),
        withdrawnFeatureKeys = outcomes.filter { !it.isAvailable }.mapTo(HashSet()) { it.featureKey },
      )
      val sections = outcomes.mapNotNull { it.section }
      val featureModels = visibleFeatureButtonModels(
        models = contentProvider.getFeatureButtonModels(project),
        offeredFeatures = offeredFeatures,
        sectionFeatureKeys = sections.mapTo(HashSet()) { it.featureKey },
        featureKeysReplacingFeatureGrid = contentProvider.featureKeysReplacingFeatureGrid,
      )
      PreparedBody(sections, featureModels)
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error("Cannot prepare the default content of the welcome right tab", e)
    return null
  }
}

/** What one feature answered: whether it is available, and the section it stated when the tab offered it. */
internal class FeatureOutcome(
  @JvmField val featureKey: String,
  @JvmField val isAvailable: Boolean,
  @JvmField val section: FeatureSection?,
)

/**
 * Asks each of [features] at the same time. The outcomes keep the [WelcomeScreenFeatureUI.contentOrder] of their features.
 *
 * A feature answers [WelcomeScreenFeatureUI.isAvailable]. It waits for [registeredFeatureIds] only when it needs a handler.
 * It states its section when the tab offers it. The disposable of a section is registered under [owner] on the thread
 * that returned the section, before any suspension. A feature that fails does not stop the other features.
 */
internal suspend fun prepareFeatures(
  project: Project,
  features: List<WelcomeScreenFeatureUI>,
  registeredFeatureIds: Deferred<Set<String>>,
  owner: Disposable,
): List<FeatureOutcome> {
  return coroutineScope {
    features.sortedBy { it.contentOrder }.map { feature ->
      async { prepareFeature(project, feature, registeredFeatureIds, owner) }
    }.awaitAll()
  }
}

private suspend fun prepareFeature(
  project: Project,
  feature: WelcomeScreenFeatureUI,
  registeredFeatureIds: Deferred<Set<String>>,
  owner: Disposable,
): FeatureOutcome {
  if (!isAvailableOrLog(project, feature)) {
    return FeatureOutcome(feature.featureKey, isAvailable = false, section = null)
  }
  if (!feature.isAlwaysAvailable && feature.featureKey !in registeredFeatureIds.await()) {
    return FeatureOutcome(feature.featureKey, isAvailable = true, section = null)
  }
  val section = withContext(welcomeScreenStartupTracer.span("welcome right tab body: createContent ${feature.featureKey}")) {
    val content = createContentOrLog(project, feature) ?: return@withContext null
    // No suspension point between `createContent` and the registration, so a cancellation cannot lose the section.
    content.disposable?.let { Disposer.register(owner, it) }
    FeatureSection(feature.featureKey, content)
  }
  return FeatureOutcome(feature.featureKey, isAvailable = true, section = section)
}

/** A feature that fails to answer stays available. */
private suspend fun isAvailableOrLog(project: Project, feature: WelcomeScreenFeatureUI): Boolean {
  return try {
    feature.isAvailable(project)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error("Cannot tell if the welcome right tab feature ${feature.featureKey} is available", e)
    true
  }
}

private suspend fun createContentOrLog(project: Project, feature: WelcomeScreenFeatureUI): WelcomeScreenFeatureUI.Content? {
  return try {
    feature.createContent(project)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.error("Cannot create the welcome right tab section of the feature ${feature.featureKey}", e)
    null
  }
}

/**
 * Which features the tab offers while it fills its content. The feature buttons and the sections read this one rule.
 *
 * @param registeredFeatureIds the keys that a frontend or a backend handler registers.
 * @param withdrawnFeatureKeys the keys whose [WelcomeScreenFeatureUI.isAvailable] answered `false`.
 */
internal class OfferedFeatures(
  private val registeredFeatureIds: Set<String>,
  private val withdrawnFeatureKeys: Set<String>,
) {
  /** Tells if the tab offers the feature [featureKey]. [isAlwaysAvailable] skips the check of the handler. */
  fun isOffered(featureKey: String, isAlwaysAvailable: Boolean): Boolean {
    return featureKey !in withdrawnFeatureKeys && (isAlwaysAvailable || featureKey in registeredFeatureIds)
  }
}

/**
 * The feature buttons that the grid shows.
 *
 * A button without a feature key always shows. A button with a key shows while [offeredFeatures] offers its feature.
 * No button shows while [sectionFeatureKeys] holds a key of [featureKeysReplacingFeatureGrid].
 */
internal fun visibleFeatureButtonModels(
  models: List<WelcomeRightTabContentProvider.FeatureButtonModel>,
  offeredFeatures: OfferedFeatures,
  sectionFeatureKeys: Set<String>,
  featureKeysReplacingFeatureGrid: Set<String>,
): List<WelcomeRightTabContentProvider.FeatureButtonModel> {
  if (sectionFeatureKeys.any { it in featureKeysReplacingFeatureGrid }) {
    return emptyList()
  }
  return models.filter {
    it !is WelcomeRightTabContentProvider.FeatureButtonModelWithBackend || offeredFeatures.isOffered(it.featureKey, it.isAlwaysAvailable)
  }
}

private fun getStatisticLogger(comboBoxInfoPanelModel: ComboBoxInfoPanelModel): ((String, Int) -> Unit)? {
  return when (comboBoxInfoPanelModel.model) {
    is ThemeModel -> { _, _ ->
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
    }
    is KeymapModel -> { _, _ ->
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.KEYMAP)
    }
    else -> null
  }
}

private sealed interface InfoPanelModel {
  val icon: Icon
  val itemPrefix: @NlsSafe String
}

private class ComboBoxInfoPanelModel(
  override val icon: Icon,
  val itemPrefixKey: String,
  val model: WelcomeScreenRightTabComboBoxModel<out Any>,
) : InfoPanelModel {
  val afterOnSelectedItemChanged: ((newSelection: String, index: Int) -> Unit)? = getStatisticLogger(this)

  override val itemPrefix: @NlsSafe String
    get() = NonModalWelcomeScreenBundle.message(itemPrefixKey)
}

private class ButtonInfoPanelModel(private val model: WelcomeRightTabContentProvider.InfoButtonModel) : InfoPanelModel {
  override val icon: Icon
    get() = model.icon
  override val itemPrefix: @NlsSafe String
    get() = model.text
  val onClick: (Project, CoroutineScope) -> Unit = model.onClick
}

private class ComboBoxInfoPanelAction(
  private val comboModel: ComboBoxInfoPanelModel,
  private val addBorder: Boolean,
) : ExpandableComboAction(), DumbAware {

  private val model = comboModel.model

  init {
    templatePresentation.text = comboModel.itemPrefix
    templatePresentation.icon = comboModel.icon
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val component = super.createCustomComponent(presentation, place)
    if (addBorder) {
      component.border = JBUI.Borders.emptyRight(16)
    }
    return component
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val selectedItem = model.itemNames().getOrNull(model.currentItemIndex()) ?: ""
    e.presentation.setText(comboModel.itemPrefix + selectedItem, false)
    e.presentation.icon = if (model is ThemeModel) ThemeModel.getIcon() else comboModel.icon
  }

  @Suppress("SplitModeApiUsage")
  override fun createPopup(event: AnActionEvent): JBPopup {
    val step = object : BaseListPopupStep<String>(null, model.itemNames()) {
      override fun isSpeedSearchEnabled(): Boolean = true

      override fun onChosen(selectedValue: String, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep {
          val index = model.itemNames().indexOf(selectedValue)
          if (index != -1 && index != model.currentItemIndex()) {
            model.setByIndex(index, selectedValue)
            comboModel.afterOnSelectedItemChanged?.invoke(selectedValue, index)
          }
        }
      }
    }
    step.defaultOptionIndex = model.currentItemIndex()
    return JBPopupFactory.getInstance().createListPopup(step)
  }
}

private class ButtonInfoPanelAction(
  private val model: ButtonInfoPanelModel,
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val addBorder: Boolean,
) : DumbAwareAction(Supplier { model.itemPrefix }, model.icon), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun iconTextSpace(): Int {
        return JBUI.scale(4)
      }

      override fun setBorder(border: Border?) {
        if (addBorder) {
          if (border == null) {
            super.setBorder(JBUI.Borders.emptyRight(16))
          }
          else {
            super.setBorder(CompoundBorder(JBUI.Borders.emptyRight(16 - JBUI.unscale(border.getBorderInsets(this).right)), border))
          }
        }
        else {
          super.setBorder(border)
        }
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    model.onClick(project, coroutineScope)
  }
}