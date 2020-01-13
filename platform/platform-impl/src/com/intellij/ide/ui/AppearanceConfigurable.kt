// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.FontComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.layout.*
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Window
import java.util.*
import javax.swing.*

private val settings get() = UISettings.instance
private val generalSettings get() = GeneralSettings.getInstance()
private val lafManager get() = LafManager.getInstance()

private val cdAnimateWindows
  get() = CheckboxDescriptor(
    message("checkbox.animate.windows"),
    settings::animateWindows)
private val cdShowToolWindowBars
  get() = CheckboxDescriptor(
    message("checkbox.show.tool.window.bars"),
    PropertyBinding({ !settings.hideToolStripes }, { settings.hideToolStripes = !it }))
private val cdShowToolWindowNumbers
  get() = CheckboxDescriptor(
    message("checkbox.show.tool.window.numbers"),
    settings::showToolWindowsNumbers)
private val cdShowMemoryIndicator
  get() = CheckboxDescriptor(
    message("checkbox.show.memory.indicator"),
    settings::showMemoryIndicator)
private val cdDisableMenuMnemonics
  get() = CheckboxDescriptor(
    KeyMapBundle.message("disable.mnemonic.in.menu.check.box"),
    settings::disableMnemonics)
private val cdDisableControlsMnemonics
  get() = CheckboxDescriptor(
    KeyMapBundle.message("disable.mnemonic.in.controls.check.box"),
    settings::disableMnemonicsInControls)
private val cdAllowMergingButtons
  get() = CheckboxDescriptor(
    "Allow merging buttons on dialogs",
    settings::allowMergeButtons)
private val cdSmoothScrolling
  get() = CheckboxDescriptor(
    message("checkbox.smooth.scrolling"),
    settings::smoothScrolling)
private val cdShowMenuIcons
  get() = CheckboxDescriptor(
    message("checkbox.show.icons.in.menu.items"),
    settings::showIconsInMenus)
private val cdWidescreenToolWindowLayout
  get() = CheckboxDescriptor(
    message("checkbox.widescreen.tool.window.layout"),
    settings::wideScreenSupport)
private val cdLeftToolWindowLayout
  get() = CheckboxDescriptor(
    message("checkbox.left.toolwindow.layout"),
    settings::leftHorizontalSplit)
private val cdRightToolWindowLayout
  get() = CheckboxDescriptor(
    message("checkbox.right.toolwindow.layout"),
    settings::rightHorizontalSplit)
private val cdCyclicListScrolling
  get() = CheckboxDescriptor(
    message("checkboox.cyclic.scrolling.in.lists"),
    settings::cycleScrolling)
private val cdShowQuickNavigationIcons
  get() = CheckboxDescriptor(
    message("checkbox.show.icons.in.quick.navigation"),
    settings::showIconInQuickNavigation)
private val cdUseCompactTreeIndents
  get() = CheckboxDescriptor(
    message("checkbox.compact.tree.indents"),
    settings::compactTreeIndents)
private val cdShowTreeIndents
  get() = CheckboxDescriptor(
    message("checkbox.show.tree.indent.guides"),
    settings::showTreeIndentGuides)
private val cdMoveCursorOnButton
  get() = CheckboxDescriptor(
    message("checkbox.position.cursor.on.default.button"),
    settings::moveMouseOnDefaultButton)
private val cdHideNavigationPopups
  get() = CheckboxDescriptor(
    "Hide navigation popups on focus loss",
    settings::hideNavigationOnFocusLoss)
private val cdDnDWithAlt
  get() = CheckboxDescriptor(
    "Drag-n-Drop with ALT pressed only",
    settings::dndWithPressedAltOnly)
private val cdUseTransparentMode
  get() = CheckboxDescriptor(
    message("checkbox.use.transparent.mode.for.floating.windows"),
    settings::enableAlphaMode)
private val cdOverrideLaFFont
  get() = CheckboxDescriptor(
    message("checkbox.override.default.laf.fonts"),
    settings::overrideLafFonts)
private val cdUseContrastToolbars
  get() = CheckboxDescriptor(
    message("checkbox.acessibility.contrast.scrollbars"),
    settings::useContrastScrollbars)

class AppearanceConfigurable : BoundConfigurable(message("title.appearance"), "preferences.lookFeel") {
  private var shouldUpdateLaF = false

  override fun createPanel(): DialogPanel {
    return panel {
      fullRow {
        label(message("combobox.look.and.feel"))
        comboBox(lafManager.lafComboBoxModel,
                 { lafManager.currentLookAndFeelReference },
                 { QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(it), true) })
          .shouldUpdateLaF()
      }
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
      titledRow(message("title.accessibility")) {
        fullRow {
          val isOverridden = GeneralSettings.isSupportScreenReadersOverridden()
          checkBox(message("checkbox.support.screen.readers"),
                   generalSettings::isSupportScreenReaders, generalSettings::setSupportScreenReaders,
                   comment = if (isOverridden) """The option is overridden by the JVM property: "${GeneralSettings.SUPPORT_SCREEN_READERS}"""" else null)
            .enabled(!isOverridden)
        }
        fullRow { checkBox(cdUseContrastToolbars) }
        fullRow {
          component(ColorBlindnessPanel()) // FIXME: UI DSL
            .withBinding({ it.colorBlindness },
                         { it, value -> it.colorBlindness = value },
                         PropertyBinding({ settings.colorBlindness },
                                         { settings.colorBlindness = it }))
            .onApply {
              DefaultColorSchemesManager.getInstance().reload()
              (EditorColorsManager.getInstance() as EditorColorsManagerImpl).schemeChangedOrSwitched(null)
            }
        }
      }
      titledRow(message("group.ui.options")) {
        fullRow {
          buttonFromAction("Background Image...", ActionPlaces.UNKNOWN, ActionManager.getInstance().getAction("Images.SetBackgroundImage"))
            .apply { isEnabled = ProjectManager.getInstance().openProjects.isNotEmpty() }
        }
        fullRow { checkBox(cdCyclicListScrolling) }
        fullRow { checkBox(cdShowQuickNavigationIcons) }
        fullRow { checkBox(cdUseCompactTreeIndents) }
        fullRow { checkBox(cdShowTreeIndents) }
        fullRow { checkBox(cdMoveCursorOnButton) }
        fullRow { checkBox(cdHideNavigationPopups) }
        fullRow { checkBox(cdDnDWithAlt) }
        fullRow {
          label("Tooltip initial delay (ms):")
          slider(0, 1200, 100, 1200)
            .labelTable {
              put(0, JLabel("0"))
              put(1200, JLabel("1200"))
            }
            .withBinding({ it.value.coerceAtMost(5000) },
                         { it, value -> it.value = value },
                         PropertyBinding({ Registry.intValue("ide.tooltip.initialDelay") },
                                         { Registry.get("ide.tooltip.initialDelay").setValue(it) }))
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
              .withBinding({ it.value },
                           { it, value -> it.value = value },
                           PropertyBinding({ (settingsState.alphaModeRatio * 100f).toInt() },
                                           { settingsState.alphaModeRatio = it / 100f })
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
            comboBox(DefaultComboBoxModel(AntialiasingType.values()), settings::ideAAType, renderer = AAListCellRenderer(false))
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
          { checkBox(cdAnimateWindows) },
          { checkBox(cdShowToolWindowBars) }
        )
        twoColumnRow(
          { checkBox(cdShowMemoryIndicator) },
          { checkBox(cdShowToolWindowNumbers) }
        )
        twoColumnRow(
          { checkBox(cdDisableMenuMnemonics) },
          { checkBox(cdAllowMergingButtons) }
        )
        twoColumnRow(
          { checkBox(cdDisableControlsMnemonics) },
          {
            checkBox(cdSmoothScrolling)
            ContextHelpLabel.create(
              "When using the mouse wheel/touchpad, the entire interface will scroll smoothly instead of line by line")()
          }
        )
        twoColumnRow(
          { checkBox(cdShowMenuIcons) },
          { checkBox(cdWidescreenToolWindowLayout) }
        )
        twoColumnRow(
          { checkBox(cdLeftToolWindowLayout) },
          { checkBox(cdRightToolWindowLayout) }
        )
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

private fun Cell.fontSizeComboBox(getter: () -> Int, setter: (Int) -> Unit, defaultValue: Int): CellBuilder<ComboBox<String>> {
  val model = DefaultComboBoxModel(UIUtil.getStandardFontSizes())
  return comboBox(model, { getter().toString() }, { setter(getIntValue(it, defaultValue)) })
    .applyToComponent { isEditable = true }
}

private fun Cell.slider(min: Int, max: Int, minorTick: Int, majorTick: Int): CellBuilder<JSlider> {
  val slider = JSlider()
  UIUtil.setSliderIsFilled(slider, true)
  slider.paintLabels = true
  slider.paintTicks = true
  slider.paintTrack = true
  slider.minimum = min
  slider.maximum = max
  slider.minorTickSpacing = minorTick
  slider.majorTickSpacing = majorTick
  return slider()
}

private fun CellBuilder<JSlider>.labelTable(table: Hashtable<Int, JComponent>.() -> Unit): CellBuilder<JSlider> {
  component.labelTable = Hashtable<Int, JComponent>().apply(table)
  return this
}

private fun <T : JComponent> CellBuilder<T>.applyToComponent(task: T.() -> Unit): CellBuilder<T> = also { task(component) }
private fun RowBuilder.fullRow(init: InnerCell.() -> Unit): Row = row { cell(isFullWidth = true, init = init) }
private fun RowBuilder.twoColumnRow(column1: InnerCell.() -> Unit, column2: InnerCell.() -> Unit): Row = row {
  cell {
    column1()
  }
  JPanel().apply { preferredSize = Dimension(0, 0) }(gapLeft = JBUI.scale(60))
  cell {
    column2()
  }
  JPanel().apply { preferredSize = Dimension(0, 0) }(growX, pushX)
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

    text = value.toString()
  }
}
