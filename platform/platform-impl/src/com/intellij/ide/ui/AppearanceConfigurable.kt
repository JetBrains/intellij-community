// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.FontComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.components.Link
import com.intellij.ui.layout.*
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
private val cdWidescreenToolWindowLayout              get() = CheckboxDescriptor(message("checkbox.widescreen.tool.window.layout"), settings::wideScreenSupport, groupName = windowOptionGroupName)
private val cdLeftToolWindowLayout                    get() = CheckboxDescriptor(message("checkbox.left.toolwindow.layout"), settings::leftHorizontalSplit, groupName = windowOptionGroupName)
private val cdRightToolWindowLayout                   get() = CheckboxDescriptor(message("checkbox.right.toolwindow.layout"), settings::rightHorizontalSplit, groupName = windowOptionGroupName)
private val cdUseCompactTreeIndents                   get() = CheckboxDescriptor(message("checkbox.compact.tree.indents"), settings::compactTreeIndents, groupName = uiOptionGroupName)
private val cdShowTreeIndents                         get() = CheckboxDescriptor(message("checkbox.show.tree.indent.guides"), settings::showTreeIndentGuides, groupName = uiOptionGroupName)
private val cdDnDWithAlt                              get() = CheckboxDescriptor(message("dnd.with.alt.pressed.only"), settings::dndWithPressedAltOnly, groupName = uiOptionGroupName)

private val cdUseTransparentMode                      get() = CheckboxDescriptor(message("checkbox.use.transparent.mode.for.floating.windows"), PropertyBinding({ settings.state.enableAlphaMode }, { settings.state.enableAlphaMode = it }))
private val cdOverrideLaFFont                         get() = CheckboxDescriptor(message("checkbox.override.default.laf.fonts"), settings::overrideLafFonts)
private val cdUseContrastToolbars                     get() = CheckboxDescriptor(message("checkbox.acessibility.contrast.scrollbars"), settings::useContrastScrollbars)
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
    cdWidescreenToolWindowLayout,
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
        if (lafManager.isAutoDetect) {
          titledRow(message("label.preferred.theme")) {
            fullRow {
              label(message("combobox.preferred.light.laf"))
              comboBox(lafManager.getLafComboBoxModel(LafManager.LafType.LIGHT),
                       { lafManager.getLookAndFeelReference(LafManager.LafType.LIGHT) },
                       { lafManager.setLookAndFeelReference(LafManager.LafType.LIGHT, it) })

              label(message("combobox.preferred.dark.laf")).withLargeLeftGap()
              comboBox(lafManager.getLafComboBoxModel(LafManager.LafType.DARK),
                       { lafManager.getLookAndFeelReference(LafManager.LafType.DARK) },
                       { lafManager.setLookAndFeelReference(LafManager.LafType.DARK, it)})
            }.largeGapAfter()
          }
        }
        else {
          fullRow {
            label(message("combobox.look.and.feel"))
            comboBox(lafManager.getLafComboBoxModel(LafManager.LafType.ALL),
                     { lafManager.getLookAndFeelReference(LafManager.LafType.ALL) },
                     { QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(it), true) })
              .shouldUpdateLaF()
          }.largeGapAfter()
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
      titledRow(message("group.ui.options")) {
        twoColumnRow(
          { checkBox(cdShowTreeIndents) },
          {
            checkBox(cdSmoothScrolling)
            ContextHelpLabel.create(message("checkbox.smooth.scrolling.description"))()
          }
        )
        twoColumnRow(
          { checkBox(cdUseCompactTreeIndents) },
          { checkBox(cdDnDWithAlt) }
        )
        twoColumnRow(
          { checkBox(cdEnableMenuMnemonics) },
          { checkBox(cdFullPathsInTitleBar) }
        )
        twoColumnRow(
          { checkBox(cdEnableControlsMnemonics) },
          { checkBox(cdShowMenuIcons) }
        )
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
        row {
          checkBox(cdWidescreenToolWindowLayout)
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
  return comboBox(model, { getter().toString() }, { setter(getIntValue(it, defaultValue)) })
    .applyToComponent { isEditable = true }
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

    text = value.toString()
  }
}
