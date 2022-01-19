// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.DataManager
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.FontComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.UIBundle
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*

private val settings: UISettings
  get() = UISettings.instance
private val generalSettings
  get() = GeneralSettings.getInstance()
private val lafManager
  get() = LafManager.getInstance()

private val cdShowToolWindowBars
  get() = CheckboxDescriptor(message("checkbox.show.tool.window.bars"), PropertyBinding({ !settings.hideToolStripes },
                                                                                        { settings.hideToolStripes = !it }),
                             groupName = windowOptionGroupName)
private val cdShowToolWindowNumbers
  get() = CheckboxDescriptor(message("checkbox.show.tool.window.numbers"), settings::showToolWindowsNumbers,
                             groupName = windowOptionGroupName)
private val cdEnableMenuMnemonics
  get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.menu.check.box"), PropertyBinding({ !settings.disableMnemonics },
                                                                                                        { settings.disableMnemonics = !it }),
                             groupName = windowOptionGroupName)
private val cdEnableControlsMnemonics
  get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.controls.check.box"),
                             PropertyBinding({ !settings.disableMnemonicsInControls }, { settings.disableMnemonicsInControls = !it }),
                             groupName = windowOptionGroupName)
private val cdSmoothScrolling
  get() = CheckboxDescriptor(message("checkbox.smooth.scrolling"), settings::smoothScrolling, groupName = uiOptionGroupName)
private val cdWidescreenToolWindowLayout
  get() = CheckboxDescriptor(message("checkbox.widescreen.tool.window.layout"), settings::wideScreenSupport,
                             groupName = windowOptionGroupName)
private val cdLeftToolWindowLayout
  get() = CheckboxDescriptor(message("checkbox.left.toolwindow.layout"), settings::leftHorizontalSplit, groupName = windowOptionGroupName)
private val cdRightToolWindowLayout
  get() = CheckboxDescriptor(message("checkbox.right.toolwindow.layout"), settings::rightHorizontalSplit, groupName = windowOptionGroupName)
private val cdUseCompactTreeIndents
  get() = CheckboxDescriptor(message("checkbox.compact.tree.indents"), settings::compactTreeIndents, groupName = uiOptionGroupName)
private val cdShowTreeIndents
  get() = CheckboxDescriptor(message("checkbox.show.tree.indent.guides"), settings::showTreeIndentGuides, groupName = uiOptionGroupName)
private val cdDnDWithAlt
  get() = CheckboxDescriptor(message("dnd.with.alt.pressed.only"), settings::dndWithPressedAltOnly, groupName = uiOptionGroupName)
private val cdSeparateMainMenu
  get() = CheckboxDescriptor(message("checkbox.main.menu.separate.toolbar"), settings::separateMainMenu, groupName = uiOptionGroupName)

private val cdUseTransparentMode
  get() = CheckboxDescriptor(message("checkbox.use.transparent.mode.for.floating.windows"),
                             PropertyBinding({ settings.state.enableAlphaMode }, { settings.state.enableAlphaMode = it }))
private val cdOverrideLaFFont get() = CheckboxDescriptor(message("checkbox.override.default.laf.fonts"), settings::overrideLafFonts)
private val cdUseContrastToolbars
  get() = CheckboxDescriptor(message("checkbox.acessibility.contrast.scrollbars"), settings::useContrastScrollbars)
private val cdMergeMainMenuWithWindowTitle
  get() = CheckboxDescriptor(message("checkbox.merge.main.menu.with.window.title"), settings::mergeMainMenuWithWindowTitle, groupName = windowOptionGroupName)
private val cdFullPathsInTitleBar
  get() = CheckboxDescriptor(message("checkbox.full.paths.in.window.header"), settings::fullPathsInWindowHeader)
private val cdShowMenuIcons
  get() = CheckboxDescriptor(message("checkbox.show.icons.in.menu.items"), settings::showIconsInMenus, groupName = windowOptionGroupName)

internal fun getAppearanceOptionDescriptors(): Sequence<OptionDescription> {
  return sequenceOf(
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
    cdFullPathsInTitleBar,
    cdSeparateMainMenu
  ).map(CheckboxDescriptor::asUiOptionDescriptor)
}

internal class AppearanceConfigurable : BoundSearchableConfigurable(message("title.appearance"), "preferences.lookFeel") {
  private var shouldUpdateLaF = false

  private val propertyGraph = PropertyGraph()
  private val lafProperty = propertyGraph.graphProperty { lafManager.lookAndFeelReference }
  private val syncThemeProperty = propertyGraph.graphProperty { lafManager.autodetect }

  override fun createPanel(): DialogPanel {
    lafProperty.afterChange({ QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(it), true) }, disposable!!)
    syncThemeProperty.afterChange({ lafManager.autodetect = it }, disposable!!)

    return panel {
      row(message("combobox.look.and.feel")) {
        val theme = comboBox(lafManager.lafComboBoxModel, lafManager.lookAndFeelCellRenderer)
          .bindItem(lafProperty)
          .accessibleName(message("combobox.look.and.feel"))

        val syncCheckBox = checkBox(message("preferred.theme.autodetect.selector"))
          .bindSelected(syncThemeProperty)
          .visible(lafManager.autodetectSupported)
          .gap(RightGap.SMALL)

        theme.enabledIf(syncCheckBox.selected.not())
        cell(lafManager.settingsToolbar)
          .visibleIf(syncCheckBox.selected)
      }.layout(RowLayout.INDEPENDENT)

      row {
        link(message("link.get.more.themes")) {
          val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(it.source as ActionLink))
          settings?.select(settings.find("preferences.pluginManager"), "/tag:theme")
        }
      }

      row {
        val overrideLaF = checkBox(cdOverrideLaFFont)
          .shouldUpdateLaF()
          .gap(RightGap.SMALL)
        cell(FontComboBox())
          .bind(
            { it.fontName },
            { it, value -> it.fontName = value },
            PropertyBinding({ if (settings.overrideLafFonts) settings.fontFace else JBFont.label().family },
                            { settings.fontFace = it })
          )
          .shouldUpdateLaF()
          .enabledIf(overrideLaF.selected)
          .accessibleName(cdOverrideLaFFont.name)

        fontSizeComboBox({ if (settings.overrideLafFonts) settings.fontSize else JBFont.label().size },
                         { settings.fontSize = it },
                         settings.fontSize)
          .label(message("label.font.size"))
          .shouldUpdateLaF()
          .enabledIf(overrideLaF.selected)
          .accessibleName(message("label.font.size"))
      }.topGap(TopGap.SMALL)

      group(message("title.accessibility")) {
        row {
          val isOverridden = GeneralSettings.isSupportScreenReadersOverridden()
          checkBox(message("checkbox.support.screen.readers"))
            .bindSelected(generalSettings::isSupportScreenReaders, generalSettings::setSupportScreenReaders)
            .comment(if (isOverridden) message("option.is.overridden.by.jvm.property", GeneralSettings.SUPPORT_SCREEN_READERS) else null)
            .enabled(!isOverridden)

          comment(message("support.screen.readers.comment"))

          val mask = if (SystemInfo.isMac) InputEvent.META_MASK else InputEvent.CTRL_MASK
          val ctrlTab = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, mask))
          val ctrlShiftTab = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, mask + InputEvent.SHIFT_MASK))
          rowComment(message("support.screen.readers.tab", ctrlTab, ctrlShiftTab))
        }

        row {
          checkBox(cdUseContrastToolbars)
        }

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

          row {
            if (supportedValues.size == 1) {
              checkBox(UIBundle.message("color.blindness.checkbox.text"))
                .comment(UIBundle.message("color.blindness.checkbox.comment"))
                .bind({ if (it.isSelected) supportedValues.first() else null },
                      { it, value -> it.isSelected = value != null },
                      modelBinding)
                .onApply(onApply)
            }
            else {
              val enableColorBlindness = checkBox(UIBundle.message("color.blindness.combobox.text"))
                .applyToComponent { isSelected = modelBinding.get() != null }
              comboBox(supportedValues)
                .enabledIf(enableColorBlindness.selected)
                .applyToComponent { renderer = SimpleListCellRenderer.create("") { PlatformEditorBundle.message(it.key) } }
                .comment(UIBundle.message("color.blindness.combobox.comment"))
                .bind({ if (enableColorBlindness.component.isSelected) it.selectedItem as? ColorBlindness else null },
                      { it, value -> it.selectedItem = value ?: supportedValues.first() },
                      modelBinding)
                .onApply(onApply)
                .accessibleName(UIBundle.message("color.blindness.checkbox.text"))
            }

            link(UIBundle.message("color.blindness.link.to.help")
            ) { HelpManager.getInstance().invokeHelp("Colorblind_Settings") }
          }
        }
      }

      group(message("group.ui.options")) {
        val leftColumnControls = sequence<Row.() -> Unit> {
          yield({ checkBox(cdShowTreeIndents) })
          yield({ checkBox(cdUseCompactTreeIndents) })
          yield({ checkBox(cdEnableMenuMnemonics) })
          yield({ checkBox(cdEnableControlsMnemonics) })
          if (SystemInfo.isWindows && ExperimentalUI.isNewToolbar()) {
            yield({ checkBox(cdSeparateMainMenu) })
          }
        }
        val rightColumnControls = sequence<Row.() -> Unit> {
          yield({
                  checkBox(cdSmoothScrolling)
                    .gap(RightGap.SMALL)
                  contextHelp(message("checkbox.smooth.scrolling.description"))
                })
          yield({ checkBox(cdDnDWithAlt) })
          if (IdeFrameDecorator.isCustomDecorationAvailable()) {
            yield({
                    val overridden = UISettings.isMergeMainMenuWithWindowTitleOverridden
                    checkBox(cdMergeMainMenuWithWindowTitle)
                      .enabled(!overridden)
                      .gap(RightGap.SMALL)
                    if (overridden) {
                      contextHelp(message("option.is.overridden.by.jvm.property", UISettings.MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY))
                    }
                    comment(message("checkbox.merge.main.menu.with.window.title.comment"))
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
            leftIt.hasNext() && rightIt.hasNext() -> twoColumnsRow(leftIt.next(), rightIt.next())
            leftIt.hasNext() -> twoColumnsRow(leftIt.next())
            rightIt.hasNext() -> twoColumnsRow(rightIt.next()) // move from right to left
          }
        }

        val backgroundImageAction = ActionManager.getInstance().getAction("Images.SetBackgroundImage")
        if (backgroundImageAction != null) {
          row {
            button(message("background.image.button"), backgroundImageAction)
              .enabled(ProjectManager.getInstance().openProjects.isNotEmpty())
          }
        }
      }

      if (Registry.`is`("ide.transparency.mode.for.windows") &&
          WindowManagerEx.getInstanceEx().isAlphaModeSupported) {
        val settingsState = settings.state
        group(message("group.transparency")) {
          lateinit var checkbox: Cell<JBCheckBox>
          row {
            checkbox = checkBox(cdUseTransparentMode)
          }
          row(message("label.transparency.delay.ms")) {
            intTextField()
              .bindIntText(settingsState::alphaModeDelay)
              .columns(4)
          }.enabledIf(checkbox.selected)
          row(message("label.transparency.ratio")) {
            slider(0, 100, 10, 50)
              .labelTable(mapOf(
                0 to JLabel("0%"),
                50 to JLabel("50%"),
                100 to JLabel("100%")))
              .bindValue({ (settingsState.alphaModeRatio * 100f).toInt() }, { settingsState.alphaModeRatio = it / 100f })
              .showValueHint()
          }.enabledIf(checkbox.selected)
            .layout(RowLayout.INDEPENDENT)
        }
      }

      groupRowsRange(message("group.antialiasing.mode")) {
        twoColumnsRow(
          {
            val ideAAOptions =
              if (!AntialiasingType.canUseSubpixelAAForIDE())
                arrayOf(AntialiasingType.GREYSCALE, AntialiasingType.OFF)
              else
                AntialiasingType.values()
            comboBox(DefaultComboBoxModel(ideAAOptions), renderer = AAListCellRenderer(false))
              .label(message("label.text.antialiasing.scope.ide"))
              .bindItem(settings::ideAAType)
              .shouldUpdateLaF()
              .accessibleName(message("label.text.antialiasing.scope.ide"))
              .onApply {
                for (w in Window.getWindows()) {
                  for (c in UIUtil.uiTraverser(w).filter(JComponent::class.java)) {
                    GraphicsUtil.setAntialiasingType(c, AntialiasingType.getAAHintForSwingComponent())
                  }
                }
              }
          },
          {
            val editorAAOptions =
              if (!AntialiasingType.canUseSubpixelAAForEditor())
                arrayOf(AntialiasingType.GREYSCALE, AntialiasingType.OFF)
              else
                AntialiasingType.values()
            comboBox(DefaultComboBoxModel(editorAAOptions), renderer = AAListCellRenderer(true))
              .label(message("label.text.antialiasing.scope.editor"))
              .bindItem(settings::editorAAType)
              .shouldUpdateLaF()
              .accessibleName(message("label.text.antialiasing.scope.editor"))
          }
        )
      }

      groupRowsRange(message("group.window.options")) {
        twoColumnsRow(
          { checkBox(cdShowToolWindowBars) },
          { checkBox(cdShowToolWindowNumbers) }
        )
        twoColumnsRow(
          { checkBox(cdLeftToolWindowLayout) },
          { checkBox(cdRightToolWindowLayout) }
        )
        twoColumnsRow(
          {
            checkBox(cdWidescreenToolWindowLayout)
              .gap(RightGap.SMALL)
            contextHelp(message("checkbox.widescreen.tool.window.layout.description"))
          })
      }

      group(message("group.presentation.mode")) {
        row(message("presentation.mode.fon.size")) {
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

  private fun <T : JComponent> Cell<T>.shouldUpdateLaF(): Cell<T> = onApply { shouldUpdateLaF = true }
}

internal fun Row.fontSizeComboBox(getter: () -> Int, setter: (Int) -> Unit, defaultValue: Int): Cell<ComboBox<String>> {
  val model = DefaultComboBoxModel(UIUtil.getStandardFontSizes())
  val modelBinding: PropertyBinding<String?> = PropertyBinding({ getter().toString() }, { setter(getIntValue(it, defaultValue)) })
  return comboBox(model)
    .accessibleName(message("presentation.mode.fon.size"))
    .applyToComponent {
      isEditable = true
      renderer = SimpleListCellRenderer.create("") { it.toString() }
      selectedItem = modelBinding.get()
    }
    .bind(
      { component -> component.editor.item as String? },
      { component, value -> component.setSelectedItem(value) },
      modelBinding
    )
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
      font = UIUtil.getFontWithFallback(scheme.getFont(EditorFontType.PLAIN))
    }

    text = value.presentableName
  }
}
