// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.WelcomeContent
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.StartupSwitchModel
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
import javax.swing.ComboBoxModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.event.ListDataListener
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

    val busConnection = ApplicationManager.getApplication().messageBus.connect(project)
    busConnection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      updateLafIconCallback()
    })

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
        val backendFeatureIds = WelcomeScreenFeatureApi.getInstance().getAvailableFeatureIds().toSet()
        val contents = createFeatureContents(backendFeatureIds)

        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          disposeSingleBanner()
          // The tab can go while the sections build, and a switch to custom content can replace what this fill
          // was built for. A section holds an editor and a scope, so a fill that no tab takes must release it
          // here. Both checks and the disposal run on the EDT, and so does [dispose].
          if (disposed || generation != contentGeneration) {
            disposeContents(contents)
            return@withContext
          }
          createDefaultContent(backendFeatureIds, contents, finish)
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
  private suspend fun createFeatureContents(backendFeatureIds: Set<String>): List<WelcomeScreenFeatureUI.Content> {
    return WelcomeScreenFeatureUI.features()
      .filter { it.featureKey in backendFeatureIds }
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
    backendFeatureIds: Set<String>,
    contents: List<WelcomeScreenFeatureUI.Content>,
    finish: () -> Unit,
  ) {
    if (contents.isEmpty()) {
      createDefaultContent(contentPanel, backendFeatureIds, false)
    }
    else {
      val contentsPanel = JPanel(VerticalLayout(0))
      contentsPanel.isOpaque = false
      contentsPanel.border = JBUI.Borders.emptyBottom(40)
      createFeatureSections(contentsPanel, contents)
      contentPanel.addToCenter(contentsPanel)

      val bottomPanel = BorderLayoutPanel()
      bottomPanel.isOpaque = false
      createDefaultContent(bottomPanel, backendFeatureIds, true)
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

  private fun createDefaultContent(parentPanel: BorderLayoutPanel, backendFeatureIds: Set<String>, extraContent: Boolean) {
    parentPanel.addToCenter(createFeatureGrid(backendFeatureIds, extraContent))

    val additionalPanel = JPanel(VerticalLayout(0))
    additionalPanel.isOpaque = false

    createAdditionalComponents(additionalPanel)
    createSingleBanner(additionalPanel, extraContent)

    if (additionalPanel.componentCount > 0) {
      parentPanel.addToBottom(additionalPanel)
    }
  }

  private fun createFeatureGrid(backendFeatureIds: Set<String>, extraContent: Boolean): JPanel {
    // Show only available backend features (and all non-backend features)
    val featureModels = contentProvider.getFeatureButtonModels(project).filter {
      it !is WelcomeRightTabContentProvider.FeatureButtonModelWithBackend || it.isAlwaysAvailable || it.featureKey in backendFeatureIds
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

  private lateinit var updateLafIconCallback: () -> Unit

  private fun createFooter() {
    val panel = JPanel(GridLayout())
    panel.isOpaque = false
    component.add(panel)

    val gridBuilder = RowsGridBuilder(panel)

    createFooterButtons(gridBuilder)
  }

  private fun createFooterButtons(gridBuilder: RowsGridBuilder) {
    val buttons = createFooterModels()

    val coroutineScope = contentProvider.coroutineScope

    for (row in buttons.chunked(contentProvider.buttonsPerRow)) {
      for (model in row) {
        when (model) {
          is ComboBoxInfoPanelModel -> {
            val cellPanel = JPanel(HorizontalLayout(8))
            cellPanel.isOpaque = false
            val label = JLabel(model.itemPrefix, model.icon, SwingConstants.LEADING)
            cellPanel.add(label)

            val comboBoxModel = model.model

            if (comboBoxModel is ThemeModel) {
              updateLafIconCallback = {
                label.icon = ThemeModel.getIcon()
              }
            }

            val combo = ComboBox(ComboModel(model, comboBoxModel))
            combo.isOpaque = false

            comboBoxModel.externalUpdateListener(project).invoke { index ->
              model.callIfNeeded {
                combo.selectedIndex = index
              }
            }

            cellPanel.add(combo)

            gridBuilder.cell(cellPanel.also { it.border = JBUI.Borders.empty(0, 0, 16, 16) })
          }
          is ButtonInfoPanelModel -> {
            val presentation = Presentation()
            presentation.icon = model.icon
            presentation.text = model.itemPrefix

            val action = object : DumbAwareAction() {
              override fun actionPerformed(e: AnActionEvent) {
                model.onClick(project, coroutineScope)
              }
            }

            val button = ActionButtonWithText(action, presentation, "", ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
            gridBuilder.cell(Wrapper(button).also { it.border = JBUI.Borders.empty(0, 0, 16, 16) })
          }
        }
      }
      gridBuilder.row()
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

private fun getStatisticLogger(comboBoxInfoPanelModel: ComboBoxInfoPanelModel): ((String, Int) -> Unit)? {
  return when (comboBoxInfoPanelModel.model) {
    is ThemeModel -> { _, _ ->
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.THEME)
    }
    is KeymapModel -> { _, _ ->
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.KEYMAP)
    }
    is StartupSwitchModel -> { _, index ->
      WelcomeScreenTabUsageCollector.logComboBoxValueChanged(WelcomeScreenComboBoxKind.STARTUP)
      WelcomeScreenTabUsageCollector.logStartupOptionChanged(comboBoxInfoPanelModel.model.items[index])
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
  private var ignoreEvent = false

  val afterOnSelectedItemChanged: ((newSelection: String, index: Int) -> Unit)? = getStatisticLogger(this)

  override val itemPrefix: @NlsSafe String
    get() = NonModalWelcomeScreenBundle.message(itemPrefixKey)

  fun callIfNeeded(call: () -> Unit) {
    if (ignoreEvent) {
      return
    }
    try {
      ignoreEvent = true
      call()
    }
    finally {
      ignoreEvent = false
    }
  }
}

private class ButtonInfoPanelModel(private val model: WelcomeRightTabContentProvider.InfoButtonModel) : InfoPanelModel {
  override val icon: Icon
    get() = model.icon
  override val itemPrefix: String
    get() = model.text
  val onClick: (Project, CoroutineScope) -> Unit = model.onClick
}

private class ComboModel(
  private val comboModel: ComboBoxInfoPanelModel,
  private val model: WelcomeScreenRightTabComboBoxModel<out Any>,
) : ComboBoxModel<String> {
  override fun setSelectedItem(item: Any?) {
    if (item is String) {
      val index = model.itemNames().indexOf(item)
      if (index != -1) {
        comboModel.callIfNeeded {
          model.setByIndex(index, item)
          comboModel.afterOnSelectedItemChanged?.invoke(item, index)
        }
      }
    }
  }

  override fun getSelectedItem(): String? {
    val index = model.currentItemIndex()
    if (index == -1) {
      return null
    }
    return getElementAt(index)
  }

  override fun getSize(): Int {
    return model.items.size
  }

  override fun getElementAt(index: Int): String {
    return model.itemNames()[index]
  }

  override fun addListDataListener(listener: ListDataListener) {
  }

  override fun removeListDataListener(listener: ListDataListener) {
  }
}

private fun JLabel.centered(): JLabel {
  horizontalAlignment = JLabel.CENTER
  verticalAlignment = JLabel.CENTER
  return this
}
