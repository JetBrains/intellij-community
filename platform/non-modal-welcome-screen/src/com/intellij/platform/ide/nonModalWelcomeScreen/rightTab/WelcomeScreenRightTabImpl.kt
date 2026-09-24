// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.dnd.FileCopyPasteUtil
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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
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
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.WelcomeContent
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.runSuppressing
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Container
import java.awt.Dimension
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

internal class WelcomeScreenRightTabImpl(
  project: Project,
  contentProvider: WelcomeRightTabContentProvider,
) : WelcomeScreenRightTab(project, contentProvider) {

  override val component = JPanel()

  private val contentPanel = BorderLayoutPanel()

  private var featureContents: List<WelcomeScreenFeatureUI.Content> = emptyList()
  private var singleBanner: Any? = null
  private var disposed: Boolean = false

  /**
   * Which content the tab shows now. [createContent] bumps it, and the default-content fill drops a result
   * that a later switch already replaced. Read and written on the EDT only.
   */
  private var contentGeneration: Int = 0

  init {
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
          if (contentProvider.productIcon != null) {
            val iconHeight = JBUI.scale(48)
            if (topY > iconHeight) {
              topY -= iconHeight
            }
          }
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

    createDefaultContent {
      component.doLayout()
      component.revalidate()
      component.repaint()
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
    return featureContents.firstNotNullOfOrNull { content ->
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

  override fun dispose() {
    disposed = true
    disposeFeatureContents()
  }

  /**
   * Disposes the sections the tab holds now, and forgets them. A second call does nothing.
   */
  private fun disposeFeatureContents() {
    val contents = featureContents
    featureContents = emptyList()
    disposeContents(contents)
    disposeSingleBanner()
  }

  /** Disposes each section that states a disposable. A section that fails does not stop the others. */
  private fun disposeContents(contents: List<WelcomeScreenFeatureUI.Content>) {
    val disposeCalls = contents.mapNotNull { content ->
      val disposable = content.disposable ?: return@mapNotNull null
      { Disposer.dispose(disposable) }
    }
    runSuppressing(*disposeCalls.toTypedArray())
  }

  private fun disposeSingleBanner() {
    (singleBanner as? Disposable)?.let(Disposer::dispose)
    singleBanner = null
  }

  private fun createDefaultContent(finish: () -> Unit) {
    val generation = contentGeneration
    contentProvider.coroutineScope.launch {
      try {
        val availableFeatureIds = WelcomeScreenFeatureApi.getInstance().getAvailableFeatureIds().toSet()
        val contents = createFeatureContents(availableFeatureIds)

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          disposeSingleBanner()
          // The tab can go while the sections build, and a switch to custom content can replace what this fill
          // was built for. A section holds an editor and a scope, so a fill that no tab takes must release it
          // here. Both checks and the disposal run on the EDT, and so does [dispose].
          if (disposed || generation != contentGeneration) {
            disposeContents(contents)
            return@withContext
          }
          createDefaultContent(availableFeatureIds, contents, finish)
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        thisLogger().error("Cannot fill the default content of the welcome right tab", e)
      }
    }
  }

  /**
   * Asks each available feature for its section. A feature that fails does not stop the other features.
   */
  private suspend fun createFeatureContents(availableFeatureIds: Set<String>): List<WelcomeScreenFeatureUI.Content> {
    return WelcomeScreenFeatureUI.features()
      .filter { it.isAlwaysAvailable || it.featureKey in availableFeatureIds }
      .sortedBy { it.contentOrder }
      .mapNotNull { feature ->
        try {
          feature.createContent(project)
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          thisLogger().error("Cannot create the welcome right tab section of the feature ${feature.featureKey}", e)
          null
        }
      }
  }

  private fun createDefaultContent(
    availableFeatureIds: Set<String>,
    contents: List<WelcomeScreenFeatureUI.Content>,
    finish: () -> Unit,
  ) {
    if (contents.isEmpty()) {
      createDefaultContent(contentPanel, availableFeatureIds, false)
    }
    else {
      val contentsPanel = JPanel(VerticalLayout(0))
      contentsPanel.isOpaque = false
      contentsPanel.border = JBUI.Borders.emptyBottom(40)
      createFeatureSections(contentsPanel, contents)
      contentPanel.addToCenter(contentsPanel)

      val bottomPanel = BorderLayoutPanel()
      bottomPanel.isOpaque = false
      createDefaultContent(bottomPanel, availableFeatureIds, true)
      contentPanel.addToBottom(bottomPanel)
    }

    finish()
  }

  private fun createFeatureSections(parentPanel: JPanel, contents: List<WelcomeScreenFeatureUI.Content>) {
    featureContents = contents
    for (content in contents) {
      parentPanel.add(content.component, VerticalLayout.CENTER)
    }
  }

  private fun createDefaultContent(parentPanel: BorderLayoutPanel, availableFeatureIds: Set<String>, extraContent: Boolean) {
    parentPanel.addToCenter(createFeatureGrid(availableFeatureIds, extraContent))

    val additionalPanel = JPanel(VerticalLayout(0))
    additionalPanel.isOpaque = false

    createAdditionalComponents(additionalPanel)
    createSingleBanner(additionalPanel, extraContent)

    if (additionalPanel.componentCount > 0) {
      parentPanel.addToBottom(additionalPanel)
    }
  }

  private fun createFeatureGrid(availableFeatureIds: Set<String>, extraContent: Boolean): JPanel {
    // Show only the features a frontend or a backend handler registers, and every button without a feature key
    val featureModels = contentProvider.getFeatureButtonModels(project).filter {
      it !is WelcomeRightTabContentProvider.FeatureButtonModelWithBackend || it.isAlwaysAvailable || it.featureKey in availableFeatureIds
    }

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
    this.singleBanner = singleBanner
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
    component.add(toolbar.component)
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