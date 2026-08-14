// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.platform.ide.nonModalWelcomeScreen.NON_MODAL_WELCOME_SCREEN_SETTING_ID
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenComboBoxKind
import com.intellij.platform.ide.nonModalWelcomeScreen.WelcomeScreenTabUsageCollector
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider.WelcomeContent
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.KeymapModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.StartupSwitchModel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.ui.JBColor
import com.intellij.ui.components.DisclosureButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.AbstractLayoutManager
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyboardFocusManager
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.util.function.Supplier
import javax.swing.ComboBoxModel
import javax.swing.Icon
import javax.swing.JButton
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
  suppressInitialContentFocus: Boolean = false,
) : WelcomeScreenRightTab(project, contentProvider, suppressInitialContentFocus) {

  override val component: JComponent = object : JPanel() {
    private val gradientPainterLight = IdeBackgroundUtil.createImagePainter(
      Supplier { contentProvider.backgroundImageVectorLight },
      IdeBackgroundUtil.Fill.PLAIN,
      IdeBackgroundUtil.Anchor.TOP_LEFT,
      1f,
      JBInsets.emptyInsets())

    private val gradientPainterDark = IdeBackgroundUtil.createImagePainter(
      Supplier { contentProvider.backgroundImageVectorDark },
      IdeBackgroundUtil.Fill.PLAIN,
      IdeBackgroundUtil.Anchor.TOP_LEFT,
      1f,
      JBInsets.emptyInsets())

    override fun paintComponent(g: Graphics) {
      super.paintComponent(g)
      (if (JBColor.isBright()) gradientPainterLight else gradientPainterDark).executePaint(this, g as Graphics2D)
    }
  }

  private val secondaryTitleLabel = JLabel().centered()

  private val contentPanel = BorderLayoutPanel()

  private val backButton =
    HoveredButton(NonModalWelcomeScreenBundle.message("welcome.screen.right.tab.back.to.default"), AllIcons.Actions.Back)

  init {
    val headerPanel = BorderLayoutPanel()
    headerPanel.isOpaque = false

    val productIcon = contentProvider.productIcon
    if (productIcon != null) {
      val label = JLabel(productIcon).centered()
      label.border = JBUI.Borders.emptyBottom(32)
      headerPanel.addToTop(label)
    }

    val titleLabel = JLabel(contentProvider.title.get())
    titleLabel.font = JBFont.label().biggerOn(9f)
    titleLabel.border = JBUI.Borders.emptyBottom(8)
    headerPanel.addToCenter(titleLabel.centered())

    secondaryTitleLabel.foreground = JBUI.CurrentTheme.ActionsList.MNEMONIC_FOREGROUND
    secondaryTitleLabel.border = JBUI.Borders.emptyBottom(32)
    headerPanel.addToBottom(secondaryTitleLabel)

    contentPanel.isOpaque = false

    val centeredComponent = BorderLayoutPanel()
    centeredComponent.isOpaque = false
    centeredComponent.addToTop(headerPanel)
    centeredComponent.addToCenter(contentPanel)

    component.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    component.isFocusTraversalPolicyProvider = true
    component.isFocusCycleRoot = true
    component.isRequestFocusEnabled = true

    component.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        if (focusOwner == null || !UIUtil.isAncestor(component, focusOwner)) {
          val newFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(component)
          if (newFocus != null) {
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
        if (container.componentCount > 0) {
          return container.getComponent(0).preferredSize
        }
        return JBDimension(0, 0)
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
          centeredChild.bounds = Rectangle((fullSize.width - centeredSize.width) / 2,
                                           topY, centeredSize.width, centeredSize.height)

          val bottomY = max(fullSize.height - bottomSize.height - offset, centeredChild.y + centeredSize.height + offset)
          bottomChild.bounds = Rectangle((fullSize.width - bottomSize.width) / 2,
                                         bottomY, bottomSize.width, bottomSize.height)
        }

        if (count == 3) {
          val button = container.getComponent(2)
          val offset = JBUI.scale(30)
          val size = button.preferredSize
          button.bounds = Rectangle(offset, offset, size.width, size.height)
        }
      }
    }
    component.add(centeredComponent)

    backButton.addActionListener { switchToDefaultContent() }

    createDefaultContent {}

    val busConnection = ApplicationManager.getApplication().messageBus.connect(project)
    createFooter(busConnection)

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
    if (contentFocusSuppressed) {
      return component
    }
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(component) ?: component
  }

  override fun switchToDefaultContent() {
    createContent(::createDefaultContent)
  }

  override fun switchToCustomContent(provider: WelcomeRightCustomTabProvider) {
    createContent { createCustomContent(provider) }
  }

  private fun createContent(builder: (() -> Unit) -> Unit) {
    backButton.parent?.remove(backButton)
    contentPanel.removeAll()

    builder {
      component.doLayout()
      component.revalidate()
      component.repaint()
    }
  }

  private fun createDefaultContent(finish: () -> Unit) {
    contentProvider.coroutineScope.async {
      val backendFeatureIds = WelcomeScreenFeatureApi.getInstance().getAvailableFeatureIds().toSet()

      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        createDefaultContent(backendFeatureIds, finish)
      }
    }
  }

  private fun createCustomContent(provider: WelcomeRightCustomTabProvider) {
    secondaryTitleLabel.text = provider.customSubtitle?.get() ?: contentProvider.secondaryTitle.get()

    component.add(backButton)
    contentPanel.addToCenter(provider.createTabContent(project))
  }

  private fun createDefaultContent(backendFeatureIds: Set<String>, finish: () -> Unit) {
    secondaryTitleLabel.text = contentProvider.secondaryTitle.get()

    createFeatureGrid(backendFeatureIds)

    val additionalPanel = JPanel(VerticalLayout(0))
    additionalPanel.isOpaque = false

    createAdditionalComponents(additionalPanel)
    //createSingleBanner(additionalPanel) // TODO: enable after sync design

    if (additionalPanel.componentCount > 0) {
      contentPanel.addToBottom(additionalPanel)
    }

    finish()
  }

  private fun createFeatureGrid(backendFeatureIds: Set<String>) {
    // Show only available backend features (and all non-backend features)
    val featureModels = contentProvider.getFeatureButtonModels(project).filter {
      it !is WelcomeRightTabContentProvider.FeatureButtonModelWithBackend || it.isAlwaysAvailable || it.featureKey in backendFeatureIds
    }

    val buttonPanel = JPanel(GridLayout())
    buttonPanel.isOpaque = false

    val wrapper = Wrapper(true)
    wrapper.add(buttonPanel)
    contentPanel.addToCenter(wrapper)

    val gridBuilder = RowsGridBuilder(buttonPanel)

    for (row in featureModels.chunked(contentProvider.buttonsPerRow)) {
      for (model in row) {
        val button = DisclosureButton()
        button.arrowIcon = null
        button.buttonHeight = JBUI.scale(90)
        button.leftMargin = JBUI.scale(51)
        button.rightMargin = JBUI.scale(51)
        button.isOpaque = false
        button.layout = BorderLayout()

        val innerPanel = JPanel(VerticalLayout(0, SwingConstants.CENTER))
        innerPanel.isOpaque = false
        button.add(innerPanel)

        innerPanel.add(JLabel(model.icon).centered(), VerticalLayout.CENTER)

        val titleLabel = JLabel(model.text)
        titleLabel.border = JBUI.Borders.emptyTop(8)
        titleLabel.font = JBFont.medium()
        innerPanel.add(titleLabel.centered(), VerticalLayout.CENTER)

        button.addActionListener { model.onClick(project, contentProvider.coroutineScope) }

        val buttonPanel = BorderLayoutPanel()
        buttonPanel.isOpaque = false
        buttonPanel.border = JBUI.Borders.empty(5)
        buttonPanel.addToCenter(button)

        gridBuilder.cell(buttonPanel)
      }
      gridBuilder.row()
    }
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

  private fun createSingleBanner(parentPanel: JPanel) {
    val singleBanner = WelcomeScreenRightTabBannerProvider.createSingleBanner(project)
    if (singleBanner != null) {
      parentPanel.add(Wrapper(singleBanner).also { it.border = JBUI.Borders.emptyTop(24) }, VerticalLayout.CENTER)
    }
  }

  private lateinit var updateLafIconCallback: () -> Unit

  private fun createFooter(busConnection: MessageBusConnection) {
    val panel = JPanel(GridLayout())
    panel.isOpaque = false
    component.add(panel)

    val gridBuilder = RowsGridBuilder(panel)

    createFooterButtons(gridBuilder)
    createDisableOptionAction(gridBuilder, busConnection)
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
                label.icon = if (JBColor.isBright()) AllIcons.MeetNewUi.LightTheme else AllIcons.MeetNewUi.DarkTheme
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

            gridBuilder.cell(cellPanel.also { it.border = JBUI.Borders.empty(0, 0, 12, 16) })
          }
          is ButtonInfoPanelModel -> {
            val button = HoveredButton(model.itemPrefix, model.icon)
            button.addActionListener { model.onClick(project, coroutineScope) }
            gridBuilder.cell(Wrapper(button).also { it.border = JBUI.Borders.empty(0, 0, 12, 16) })
          }
        }
      }
      gridBuilder.row()
    }
  }

  private fun createFooterModels(): List<InfoPanelModel> {
    return buildList {
      add(ComboBoxInfoPanelModel(AllIcons.MeetNewUi.LightTheme,
                                 "welcome.screen.right.tab.theme.switch.prefix",
                                 ThemeModel()))
      add(ComboBoxInfoPanelModel(AllIcons.General.Keyboard,
                                 "welcome.screen.right.tab.keymap.switch.prefix",
                                 KeymapModel()))
      if (contentProvider.isStartupSwitchPanelOptionVisible) {
        add(ComboBoxInfoPanelModel(AllIcons.General.Settings,
                                   "welcome.screen.right.tab.startup.switch.prefix",
                                   StartupSwitchModel()))
      }
      addAll(contentProvider.getAdditionalInfoButtonModels(project).map { ButtonInfoPanelModel(it) })
    }
  }

  private fun createDisableOptionAction(gridBuilder: RowsGridBuilder, busConnection: MessageBusConnection) {
    if (contentProvider.isDisableOptionVisible) {
      val checkbox = JBCheckBox(NonModalWelcomeScreenBundle.message("welcome.screen.enabled.checkbox"))
      checkbox.isSelected = isRightTabEnabled
      checkbox.addItemListener { isRightTabEnabled = checkbox.isSelected }

      gridBuilder.cell(Wrapper(checkbox).also { it.border = JBUI.Borders.empty(0, 0, 12, 16) },
                       horizontalAlign = HorizontalAlign.CENTER, width = contentProvider.buttonsPerRow)

      busConnection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          if (id == NON_MODAL_WELCOME_SCREEN_SETTING_ID) {
            checkbox.isSelected = isRightTabEnabled
          }
        }
      })
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
      comboModel.callIfNeeded {
        val index = model.itemNames().indexOf(item)
        model.setByIndex(index, item)
        comboModel.afterOnSelectedItemChanged?.invoke(item, index)
      }
    }
  }

  override fun getSelectedItem(): String {
    return getElementAt(model.currentItemIndex())
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

private class HoveredButton(text: @NlsSafe String, icon: Icon) : JButton(text, icon) {
  private var hoverColor: Color? = null

  init {
    isFocusPainted = false
    isBorderPainted = false
    isRolloverEnabled = true
    isContentAreaFilled = false
    background = null

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(evt: MouseEvent) {
        hoverColor = JBUI.CurrentTheme.ActionButton.hoverBackground()
      }

      override fun mouseExited(evt: MouseEvent) {
        hoverColor = null
      }
    })
  }

  override fun setBackground(bg: Color?) {
    super.setBackground(null)
  }

  override fun paintComponent(g: Graphics) {
    val hoverColor = hoverColor
    if (hoverColor != null) {
      val g2 = g.create() as Graphics2D

      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
        g2.color = hoverColor

        val rect = Rectangle(size)
        JBInsets.removeFrom(rect, insets)

        val arc = scale(JBUI.getInt("Button.arc", 6))
        g2.fill(RoundRectangle2D.Float(rect.x.toFloat(),
                                       rect.y.toFloat(),
                                       rect.width.toFloat(),
                                       rect.height.toFloat(),
                                       arc.toFloat(),
                                       arc.toFloat()))
      }
      finally {
        g2.dispose()
      }
    }
    super.paintComponent(g)
  }
}

private fun JLabel.centered(): JLabel {
  horizontalAlignment = JLabel.CENTER
  verticalAlignment = JLabel.CENTER
  return this
}