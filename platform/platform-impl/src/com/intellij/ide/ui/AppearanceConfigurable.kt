// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.icons.AllIcons
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.ui.*
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.components.Link
import com.intellij.ui.layout.*
import com.intellij.ui.layout.Cell
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Window
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

// @formatter:off
private val settings get() = UISettings.instance
private val generalSettings get() = GeneralSettings.getInstance()
private val lafManager get() = LafManager.getInstance()

private val cdShowToolWindowBars                      get() = CheckboxDescriptor(message("checkbox.show.tool.window.bars"), PropertyBinding({ !settings.hideToolStripes }, { settings.hideToolStripes = !it }), groupName = windowOptionGroupName)
private val cdShowToolWindowNumbers                   get() = CheckboxDescriptor(message("checkbox.show.tool.window.numbers"), settings::showToolWindowsNumbers, groupName = windowOptionGroupName)
private val cdEnableMenuMnemonics                     get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.menu.check.box"), PropertyBinding({ !settings.disableMnemonics }, { settings.disableMnemonics = !it }), groupName = windowOptionGroupName)
private val cdEnableControlsMnemonics                 get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.controls.check.box"), PropertyBinding({ !settings.disableMnemonicsInControls }, { settings.disableMnemonicsInControls = !it }), groupName = windowOptionGroupName)
private val cdSmoothScrolling                         get() = CheckboxDescriptor(message("checkbox.smooth.scrolling"), settings::smoothScrolling, groupName = uiOptionGroupName)
private val cdLeftToolWindowLayout                    get() = CheckboxDescriptor(message("checkbox.left.toolwindow.layout"), settings::leftHorizontalSplit, groupName = windowOptionGroupName)
private val cdRightToolWindowLayout                   get() = CheckboxDescriptor(message("checkbox.right.toolwindow.layout"), settings::rightHorizontalSplit, groupName = windowOptionGroupName)
private val cdUseCompactTreeIndents                   get() = CheckboxDescriptor(message("checkbox.compact.tree.indents"), settings::compactTreeIndents, groupName = uiOptionGroupName)
private val cdShowTreeIndents                         get() = CheckboxDescriptor(message("checkbox.show.tree.indent.guides"), settings::showTreeIndentGuides, groupName = uiOptionGroupName)
private val cdDnDWithAlt                              get() = CheckboxDescriptor(message("dnd.with.alt.pressed.only"), settings::dndWithPressedAltOnly, groupName = uiOptionGroupName)

private val cdUseTransparentMode                      get() = CheckboxDescriptor(message("checkbox.use.transparent.mode.for.floating.windows"), PropertyBinding({ settings.state.enableAlphaMode }, { settings.state.enableAlphaMode = it }))
private val cdOverrideLaFFont                         get() = CheckboxDescriptor(message("checkbox.override.default.laf.fonts"), settings::overrideLafFonts)
private val cdUseContrastToolbars                     get() = CheckboxDescriptor(message("checkbox.acessibility.contrast.scrollbars"), settings::useContrastScrollbars)
private val cdMergeMainMenuWithWindowTitle            get() = CheckboxDescriptor(message("checkbox.merge.main.menu.with.window.title"), settings::mergeMainMenuWithWindowTitle, groupName = windowOptionGroupName)
private val cdFullPathsInTitleBar                     get() = CheckboxDescriptor(message("checkbox.full.paths.in.window.header"), settings::fullPathsInWindowHeader)
private val cdShowMenuIcons                           get() = CheckboxDescriptor(message("checkbox.show.icons.in.menu.items"), settings::showIconsInMenus, groupName = windowOptionGroupName)

// @formatter:on

internal val appearanceOptionDescriptors: List<OptionDescription>
  get() = listOf(
    cdShowToolWindowBars,
    cdShowToolWindowNumbers,
    cdEnableMenuMnemonics,
    cdEnableControlsMnemonics,
    cdSmoothScrolling,
    cdLeftToolWindowLayout,
    cdRightToolWindowLayout,
    cdUseCompactTreeIndents,
    cdShowTreeIndents,
    cdDnDWithAlt,
    cdFullPathsInTitleBar
  ).map(CheckboxDescriptor::asUiOptionDescriptor)

internal class AppearanceConfigurable : BoundSearchableConfigurable(message("title.appearance"), "preferences.lookFeel") {
  private var shouldUpdateLaF = false

  override fun createPanel(): DialogPanel {
    return panel {
      blockRow {
        fullRow {
          label(message("combobox.look.and.feel"))
          val theme = comboBox(lafManager.lafComboBoxModel,
                   { lafManager.lookAndFeelReference },
                   { QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(it), true) },
                   lafManager.lookAndFeelCellRenderer).shouldUpdateLaF()

          val syncCheckBox = checkBox(message("preferred.theme.autodetect.selector"),
                                      { lafManager.autodetect },
                                      { lafManager.autodetect = it }).withLargeLeftGap().shouldUpdateLaF().
                              apply { component.isVisible = lafManager.autodetectSupported }

          theme.enableIf(syncCheckBox.selected.not())
          component(lafManager.settingsToolbar).visibleIf(syncCheckBox.selected).withLeftGap()
        }.largeGapAfter()
        fullRow {
          val overrideLaF = checkBox(cdOverrideLaFFont)
            .shouldUpdateLaF()
          component(FontComboBox())
            .withBinding(
              { it.fontName },
              { it, value -> it.fontName = value },
              PropertyBinding({ if (settings.overrideLafFonts) settings.fontFace else JBFont.label().family },
                              { settings.fontFace = it })
            )
            .shouldUpdateLaF()
            .enableIf(overrideLaF.selected)
          component(Label(message("label.font.size")))
          .withLargeLeftGap()
            .enableIf(overrideLaF.selected)
          fontSizeComboBox({ if (settings.overrideLafFonts) settings.fontSize else JBFont.label().size },
                           { settings.fontSize = it },
                           settings.fontSize)
            .shouldUpdateLaF()
            .enableIf(overrideLaF.selected)
        }
      }
      titledRow(message("title.accessibility")) {
        fullRow {
          val isOverridden = GeneralSettings.isSupportScreenReadersOverridden()
          checkBox(message("checkbox.support.screen.readers"),
                   generalSettings::isSupportScreenReaders, generalSettings::setSupportScreenReaders,
                   comment = if (isOverridden) message("option.is.overridden.by.jvm.property", GeneralSettings.SUPPORT_SCREEN_READERS)
                   else null)
            .enabled(!isOverridden)

          commentNoWrap(message("support.screen.readers.comment"))
            .withLargeLeftGap()
        }
        fullRow { checkBox(cdUseContrastToolbars) }

        val supportedValues = ColorBlindness.values().filter { ColorBlindnessSupport.get(it) != null }
        if (supportedValues.isNotEmpty()) {
          val modelBinding = PropertyBinding({ settings.colorBlindness }, { settings.colorBlindness = it })
          val onApply = {
            // callback executed not when all changes are applied, but one component by one, so, reload later when everything were applied
            ApplicationManager.getApplication().invokeLater(Runnable {
              DefaultColorSchemesManager.getInstance().reload()
              (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
            })
          }

          fullRow {
            if (supportedValues.size == 1) {
              component(JBCheckBox(UIBundle.message("color.blindness.checkbox.text")))
                .comment(UIBundle.message("color.blindness.checkbox.comment"))
                .withBinding({ if (it.isSelected) supportedValues.first() else null },
                             { it, value -> it.isSelected = value != null },
                             modelBinding)
                .onApply(onApply)
            }
            else {
              val enableColorBlindness = component(JBCheckBox(UIBundle.message("color.blindness.combobox.text")))
                .applyToComponent { isSelected = modelBinding.get() != null }
              component(ComboBox(supportedValues.toTypedArray()))
                .enableIf(enableColorBlindness.selected)
                .applyToComponent { renderer = SimpleListCellRenderer.create<ColorBlindness>("") { PlatformEditorBundle.message(it.key) } }
                .comment(UIBundle.message("color.blindness.combobox.comment"))
                .withBinding({ if (enableColorBlindness.component.isSelected) it.selectedItem as? ColorBlindness else null },
                             { it, value -> it.selectedItem = value ?: supportedValues.first() },
                             modelBinding)
                .onApply(onApply)
            }

            component(Link(UIBundle.message("color.blindness.link.to.help"))
                      { HelpManager.getInstance().invokeHelp("Colorblind_Settings") })
              .withLargeLeftGap()
          }
        }
      }
      @Suppress("MoveLambdaOutsideParentheses") // this suggestion is wrong, see KT-40969
      titledRow(message("group.ui.options")) {
        val leftColumnControls = sequence<InnerCell.() -> Unit> {
          yield({ checkBox(cdShowTreeIndents) })
          yield({ checkBox(cdUseCompactTreeIndents) })
          yield({ checkBox(cdEnableMenuMnemonics) })
          yield({ checkBox(cdEnableControlsMnemonics) })
        }
        val rightColumnControls = sequence<InnerCell.() -> Unit> {
          yield({
                  checkBox(cdSmoothScrolling)
                  ContextHelpLabel.create(message("checkbox.smooth.scrolling.description"))()
                })
          yield({ checkBox(cdDnDWithAlt) })
          if (IdeFrameDecorator.isCustomDecorationAvailable()) {
            yield({
                    val overridden = UISettings.isMergeMainMenuWithWindowTitleOverridden
                    checkBox(cdMergeMainMenuWithWindowTitle).enabled(!overridden)
                    if (overridden) {
                      ContextHelpLabel.create(
                        message("option.is.overridden.by.jvm.property", UISettings.MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY))()
                    }
                    commentNoWrap(message("checkbox.merge.main.menu.with.window.title.comment")).withLargeLeftGap()
                  })
          }
          yield({ checkBox(cdFullPathsInTitleBar) })
          yield({ checkBox(cdShowMenuIcons) })
        }

        // Since some of the columns have variable number of items, enumerate them in a loop, while moving orphaned items from the right
        // column to the left one:
        val leftIt = leftColumnControls.iterator()
        val rightIt = rightColumnControls.iterator()
        while (leftIt.hasNext() || rightIt.hasNext()) {
          when {
            leftIt.hasNext() && rightIt.hasNext() -> twoColumnRow(leftIt.next(), rightIt.next())
            leftIt.hasNext() -> twoColumnRow(leftIt.next()) { placeholder() }
            rightIt.hasNext() -> twoColumnRow(rightIt.next()) { placeholder() } // move from right to left
          }
        }
        val backgroundImageAction = ActionManager.getInstance().getAction("Images.SetBackgroundImage")
        if (backgroundImageAction != null) {
          fullRow {
            buttonFromAction(message("background.image.button"), ActionPlaces.UNKNOWN, backgroundImageAction)
              .applyToComponent { isEnabled = ProjectManager.getInstance().openProjects.isNotEmpty() }
          }
        }
      }
      if (Registry.`is`("ide.transparency.mode.for.windows") &&
          WindowManagerEx.getInstanceEx().isAlphaModeSupported) {
        val settingsState = settings.state
        titledRow(message("group.transparency")) {
          lateinit var checkbox: CellBuilder<JBCheckBox>
          fullRow { checkbox = checkBox(cdUseTransparentMode) }
          fullRow {
            label(message("label.transparency.delay.ms"))
            intTextField(settingsState::alphaModeDelay, columns = 4)
          }.enableIf(checkbox.selected)
          fullRow {
            label(message("label.transparency.ratio"))
            slider(0, 100, 10, 50)
              .labelTable {
                put(0, JLabel("0%"))
                put(50, JLabel("50%"))
                put(100, JLabel("100%"))
              }
              .withValueBinding(
                PropertyBinding(
                  { (settingsState.alphaModeRatio * 100f).toInt() },
                  { settingsState.alphaModeRatio = it / 100f }
                )
              )
              .applyToComponent {
                addChangeListener { toolTipText = "${value}%" }
              }
          }.enableIf(checkbox.selected)
        }
      }
      titledRow(message("group.antialiasing.mode")) {
        twoColumnRow(
          {
            label(message("label.text.antialiasing.scope.ide"))
            val ideAAOptions =
              if (!AntialiasingType.canUseSubpixelAAForIDE())
                arrayOf(AntialiasingType.GREYSCALE, AntialiasingType.OFF)
              else
                AntialiasingType.values()
            comboBox(DefaultComboBoxModel(ideAAOptions), settings::ideAAType, renderer = AAListCellRenderer(false))
              .shouldUpdateLaF()
              .onApply {
                for (w in Window.getWindows()) {
                  for (c in UIUtil.uiTraverser(w).filter(JComponent::class.java)) {
                    GraphicsUtil.setAntialiasingType(c, AntialiasingType.getAAHintForSwingComponent())
                  }
                }
              }
          },
          {
            label(message("label.text.antialiasing.scope.editor"))
            comboBox(DefaultComboBoxModel(AntialiasingType.values()), settings::editorAAType, renderer = AAListCellRenderer(true))
              .shouldUpdateLaF()
          }
        )
      }
      titledRow(message("group.window.options")) {
        twoColumnRow(
          { checkBox(cdShowToolWindowBars) },
          { checkBox(cdShowToolWindowNumbers) }
        )
        twoColumnRow(
          { checkBox(cdLeftToolWindowLayout) },
          { checkBox(cdRightToolWindowLayout) }
        )
        fullRow {
          label(message("tool.window.layout"))

          val group = DefaultActionGroup(
            ToolWindowsLayoutAction(ToolWindowsLayoutAction.Anchor.TOP_LEFT),
            ToolWindowsLayoutAction(ToolWindowsLayoutAction.Anchor.TOP_RIGHT),
            ToolWindowsLayoutAction(ToolWindowsLayoutAction.Anchor.BOTTOM_LEFT),
            ToolWindowsLayoutAction(ToolWindowsLayoutAction.Anchor.BOTTOM_RIGHT)
          )
          val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLBAR, group, true)
          toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
          toolbar.component.isOpaque = false
          component(toolbar.component).withLeftGap()

          ContextHelpLabel.create(message("tool.window.layout.description"))().withLeftGap()
        }
      }
      titledRow(message("group.presentation.mode")) {
        fullRow {
          label(message("presentation.mode.fon.size"))
          fontSizeComboBox({ settings.presentationModeFontSize },
                           { settings.presentationModeFontSize = it },
                           settings.presentationModeFontSize)
            .shouldUpdateLaF()
        }
      }
    }
  }

  override fun apply() {
    val uiSettingsChanged = isModified
    shouldUpdateLaF = false

    super.apply()

    if (shouldUpdateLaF) {
      LafManager.getInstance().updateUI()
    }
    if (uiSettingsChanged) {
      UISettings.instance.fireUISettingsChanged()
      EditorFactory.getInstance().refreshAllEditors()
    }
  }

  private fun <T : JComponent> CellBuilder<T>.shouldUpdateLaF(): CellBuilder<T> = onApply { shouldUpdateLaF = true }
}

fun Cell.fontSizeComboBox(getter: () -> Int, setter: (Int) -> Unit, defaultValue: Int): CellBuilder<ComboBox<String>> {
  val model = DefaultComboBoxModel(UIUtil.getStandardFontSizes())
  val modelBinding: PropertyBinding<String?> = PropertyBinding({ getter().toString() }, { setter(getIntValue(it, defaultValue)) })
  return component(ComboBox(model))
    .applyToComponent {
      isEditable = true
      renderer = SimpleListCellRenderer.create("") { it.toString() }
      selectedItem = modelBinding.get()
    }
    .withBinding(
      { component -> component.editor.item as String? },
      { component, value -> component.setSelectedItem(value) },
      modelBinding
    )
}

fun RowBuilder.fullRow(init: InnerCell.() -> Unit): Row = row { cell(isFullWidth = true, init = init) }
private fun RowBuilder.twoColumnRow(column1: InnerCell.() -> Unit, column2: InnerCell.() -> Unit): Row = row {
  cell {
    column1()
  }
  placeholder().withLeftGap(JBUI.scale(60))
  cell {
    column2()
  }
  placeholder().constraints(growX, pushX)
}

private fun getIntValue(text: String?, defaultValue: Int): Int {
  if (text != null && text.isNotBlank()) {
    val value = text.toIntOrNull()
    if (value != null && value > 0) return value
  }
  return defaultValue
}

private class AAListCellRenderer(private val myUseEditorFont: Boolean) : SimpleListCellRenderer<AntialiasingType>() {
  private val SUBPIXEL_HINT = GraphicsUtil.createAATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
  private val GREYSCALE_HINT = GraphicsUtil.createAATextInfo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

  override fun customize(list: JList<out AntialiasingType>, value: AntialiasingType, index: Int, selected: Boolean, hasFocus: Boolean) {
    val aaType = when (value) {
      AntialiasingType.SUBPIXEL -> SUBPIXEL_HINT
      AntialiasingType.GREYSCALE -> GREYSCALE_HINT
      AntialiasingType.OFF -> null
    }
    GraphicsUtil.setAntialiasingType(this, aaType)

    if (myUseEditorFont) {
      val scheme = EditorColorsManager.getInstance().globalScheme
      font = Font(scheme.editorFontName, Font.PLAIN, scheme.editorFontSize)
    }

    text = value.presentableName
  }
}

/**
 * @author Matt Coster
 */
private class ToolWindowsLayoutAction(var anchor: Anchor): DumbAwareAction() {
  var isVerticalByHorizontal = false

  override fun update(e: AnActionEvent) {
    val uiSettings = UISettings.instance
    isVerticalByHorizontal = when (anchor) {
      Anchor.TOP_LEFT -> uiSettings.toolWindowsLeftByTop
      Anchor.TOP_RIGHT -> uiSettings.toolWindowsRightByTop
      Anchor.BOTTOM_LEFT -> uiSettings.toolWindowsLeftByBottom
      Anchor.BOTTOM_RIGHT -> uiSettings.toolWindowsRightByBottom
    }
    updateAppearance(e.presentation)
  }

  override fun actionPerformed(e: AnActionEvent) {
    isVerticalByHorizontal = !isVerticalByHorizontal
    val uiSettings = UISettings.instance
    when (anchor) {
      Anchor.TOP_LEFT -> uiSettings.toolWindowsLeftByTop = isVerticalByHorizontal
      Anchor.TOP_RIGHT -> uiSettings.toolWindowsRightByTop = isVerticalByHorizontal
      Anchor.BOTTOM_LEFT -> uiSettings.toolWindowsLeftByBottom = isVerticalByHorizontal
      Anchor.BOTTOM_RIGHT -> uiSettings.toolWindowsRightByBottom = isVerticalByHorizontal
    }
    uiSettings.fireUISettingsChanged()
    updateAppearance(e.presentation)
  }

  private fun updateAppearance(presentation: Presentation) {
    if (isVerticalByHorizontal) {
      when (anchor) {
        Anchor.TOP_LEFT -> {
          presentation.icon = AllIcons.General.TwLeftByTop
          presentation.text = message("tool.window.layout.left.by.top")
        }
        Anchor.TOP_RIGHT -> {
          presentation.icon = AllIcons.General.TwRightByTop
          presentation.text = message("tool.window.layout.right.by.top")
        }
        Anchor.BOTTOM_LEFT -> {
          presentation.icon = AllIcons.General.TwLeftByBottom
          presentation.text = message("tool.window.layout.left.by.bottom")
        }
        Anchor.BOTTOM_RIGHT -> {
          presentation.icon = AllIcons.General.TwRightByBottom
          presentation.text = message("tool.window.layout.right.by.bottom")
        }
      }
    } else {
      when (anchor) {
        Anchor.TOP_LEFT -> {
          presentation.icon = AllIcons.General.TwLeftUnderTop
          presentation.text = message("tool.window.layout.left.under.top")
        }
        Anchor.TOP_RIGHT -> {
          presentation.icon = AllIcons.General.TwRightUnderTop
          presentation.text = message("tool.window.layout.right.under.top")
        }
        Anchor.BOTTOM_LEFT -> {
          presentation.icon = AllIcons.General.TwLeftOnBottom
          presentation.text = message("tool.window.layout.left.on.bottom")
        }
        Anchor.BOTTOM_RIGHT -> {
          presentation.icon = AllIcons.General.TwRightOnBottom
          presentation.text = message("tool.window.layout.right.on.bottom")
        }
      }
    }
  }

  enum class Anchor {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
  }
}
