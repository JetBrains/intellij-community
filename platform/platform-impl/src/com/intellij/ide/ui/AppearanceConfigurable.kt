// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.SchemesPanel
import com.intellij.application.options.colors.SchemesPanelFactory
import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.ide.DataManager
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle.message
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.actions.IdeScaleTransformer
import com.intellij.ide.actions.QuickChangeLookAndFeel
import com.intellij.ide.isSupportScreenReadersOverridden
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.ui.laf.LafManagerImpl
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.internal.statistic.service.fus.collectors.IdeZoomEventFields
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.IdeZoomChanged
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger.ThemeAutodetectSelector
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.PlatformEditorBundle
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager
import com.intellij.openapi.editor.colors.impl.EditorColorsManagerImpl
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.keymap.KeyMapBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.openapi.wm.impl.isMergeMainMenuWithWindowTitleOverridden
import com.intellij.toolWindow.ResizeStripeManager
import com.intellij.ui.*
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.ui.layout.and
import com.intellij.ui.layout.not
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

private val settings: UISettings
  get() = UISettings.getInstance()
private val generalSettings: GeneralSettings
  get() = GeneralSettings.getInstance()
private val lafManager: LafManager
  get() = LafManager.getInstance()

private val cdShowToolWindowBars
  get() = CheckboxDescriptor(message("checkbox.show.tool.window.bars"),
                             { !settings.hideToolStripes }, { settings.hideToolStripes = !it },
                             groupName = windowOptionGroupName)
private val cdShowToolWindowNames
  get() = CheckboxDescriptor(message("checkbox.show.tool.window.names"), settings::showToolWindowsNames,
                             groupName = windowOptionGroupName)
private val cdShowToolWindowNumbers
  get() = CheckboxDescriptor(message("checkbox.show.tool.window.numbers"), settings::showToolWindowsNumbers,
                             groupName = windowOptionGroupName)
private val cdEnableMenuMnemonics
  get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.menu.check.box"),
                             { !settings.disableMnemonics }, { settings.disableMnemonics = !it },
                             groupName = windowOptionGroupName)
private val cdEnableControlsMnemonics
  get() = CheckboxDescriptor(KeyMapBundle.message("enable.mnemonic.in.controls.check.box"),
                             { !settings.disableMnemonicsInControls }, { settings.disableMnemonicsInControls = !it },
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
private val cdRememberSizeForEachToolWindowOldUI
  get() = CheckboxDescriptor(message("checkbox.remember.size.for.each.tool.window"), settings::rememberSizeForEachToolWindowOldUI,
                             groupName = windowOptionGroupName)
private val cdRememberSizeForEachToolWindowNewUI
  get() = CheckboxDescriptor(message("checkbox.remember.size.for.each.tool.window"), settings::rememberSizeForEachToolWindowNewUI,
                             groupName = windowOptionGroupName)
private val cdUseCompactTreeIndents
  get() = CheckboxDescriptor(message("checkbox.compact.tree.indents"), settings::compactTreeIndents, groupName = uiOptionGroupName)
private val cdShowTreeIndents
  get() = CheckboxDescriptor(message("checkbox.show.tree.indent.guides"), settings::showTreeIndentGuides, groupName = uiOptionGroupName)
private val cdExpandNodesWithSingleClick
  get() = CheckboxDescriptor(message("checkbox.expand.node.with.single.click"), settings::expandNodesWithSingleClick,
                             comment = message("checkbox.expand.node.with.single.click.comment"), groupName = uiOptionGroupName)
private val cdDnDWithAlt
  get() = CheckboxDescriptor(message("dnd.with.alt.pressed.only"), settings::dndWithPressedAltOnly, groupName = uiOptionGroupName)
private val cdSeparateMainMenu
  get() = CheckboxDescriptor(message("checkbox.main.menu.separate.toolbar"), settings::separateMainMenu, groupName = uiOptionGroupName)

private val cdUseTransparentMode
  get() = CheckboxDescriptor(message("checkbox.use.transparent.mode.for.floating.windows"), settings.state::enableAlphaMode)
private val cdUseContrastToolbars
  get() = CheckboxDescriptor(message("checkbox.acessibility.contrast.scrollbars"), settings::useContrastScrollbars)
private val cdMergeMainMenuWithWindowTitle
  get() = CheckboxDescriptor(message("checkbox.merge.main.menu.with.window.title"), settings::mergeMainMenuWithWindowTitle,
                             groupName = windowOptionGroupName)
private val cdFullPathsInTitleBar
  get() = CheckboxDescriptor(message("checkbox.full.paths.in.window.header"), settings::fullPathsInWindowHeader)
private val cdShowMenuIcons
  get() = CheckboxDescriptor(message("checkbox.show.icons.in.menu.items"), settings::showIconsInMenus, groupName = windowOptionGroupName)
private val cdKeepPopupsForToggles
  get() = CheckboxDescriptor(message("checkbox.keep.popups.for.toggles"), settings::keepPopupsForToggles, groupName = uiOptionGroupName)
private val cdDifferentiateProjects
  get() = CheckboxDescriptor(message("checkbox.use.solution.colors.in.main.toolbar"), settings::differentiateProjects,
                             message("text.use.solution.colors.in.main.toolbar"), groupName = uiOptionGroupName)

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
    cdSeparateMainMenu.takeUnless { SystemInfo.isMac },
    cdDifferentiateProjects,
    cdShowMenuIcons
  ).filterNotNull().map(CheckboxDescriptor::asUiOptionDescriptor)
}

internal class AppearanceConfigurable : BoundSearchableConfigurable(message("title.appearance"), "preferences.lookFeel") {
  private val propertyGraph = PropertyGraph()
  private val lafProperty = propertyGraph.lazyProperty { lafManager.lookAndFeelReference }
  private val syncThemeProperty = propertyGraph.lazyProperty { lafManager.autodetect }

  override fun createPanel(): DialogPanel {
    lafProperty.afterChange(disposable!!) {
      ApplicationManager.getApplication().invokeLater {
        QuickChangeLookAndFeel.switchLafAndUpdateUI(lafManager, lafManager.findLaf(it.themeId), true)
      }
    }
    syncThemeProperty.afterChange(disposable!!) {
      lafManager.autodetect = it
      ThemeAutodetectSelector.log(it)
    }

    return panel {
      val autodetectSupportedPredicate = ComponentPredicate.fromValue(lafManager.autodetectSupported)
      val syncThemeAndEditorSchemePredicate = autodetectSupportedPredicate.and(ComponentPredicate.fromObservableProperty(syncThemeProperty, disposable))

      panel {
        row(message("combobox.look.and.feel")) {
          val lafComboBoxModelWrapper = LafComboBoxModelWrapper { lafManager.lafComboBoxModel }
          val theme = comboBox(lafComboBoxModelWrapper)
            .bindItem(lafProperty)
            .accessibleName(message("combobox.look.and.feel"))

          theme.component.isSwingPopup = false
          theme.component.renderer = lafManager.getLookAndFeelCellRenderer(theme.component)
          lafComboBoxModelWrapper.comboBoxComponent = theme.component

          checkBox(message("preferred.theme.autodetect.selector"))
            .bindSelected(syncThemeProperty)
            .visible(lafManager.autodetectSupported)
            .gap(RightGap.SMALL)

          theme.enabledIf(syncThemeAndEditorSchemePredicate.not())
          cell(lafManager.createSettingsToolbar())
            .visible(lafManager.autodetectSupported)
        }
      }

      indent {
        val colorAndFontsOptions = ColorAndFontOptions().apply {
          setShouldChangeLafIfNecessary(false)
          setSchemesPanelFactory(object : SchemesPanelFactory {
            override fun createSchemesPanel(options: ColorAndFontOptions): SchemesPanel {
              return EditorSchemesPanel(options)
            }
          })
        }
        val editorSchemeCombo = colorAndFontsOptions.createComponent(true)

        row {
          cell(editorSchemeCombo).onIsModified {
            colorAndFontsOptions.isModified
          }.onApply {
            colorAndFontsOptions.apply()
          }.onReset {
            colorAndFontsOptions.reset()
          }.enabledIf(syncThemeAndEditorSchemePredicate.not())

          syncThemeAndEditorSchemePredicate.addListener { isSyncOn ->
            if (isSyncOn) {
              colorAndFontsOptions.reset()
            }
          }
        }

        disposable?.whenDisposed {
          colorAndFontsOptions.disposeUIResources()
        }
      }

      group(message("title.accessibility")) {
        row(message("combobox.ide.scale.percent")) {
          val defaultScale = UISettingsUtils.defaultScale(false)
          var resetZoom: Cell<ActionLink>? = null

          val model = IdeScaleTransformer.Settings.createIdeScaleComboboxModel()
          comboBox(model, textListCellRenderer { it })
            .bindItem({ settings.ideScale.percentStringValue }, { })
            .onChanged {
              if (IdeScaleTransformer.Settings.validatePercentScaleInput(it.item, false) != null) return@onChanged

              IdeScaleTransformer.Settings.scaleFromPercentStringValue(it.item, false)?.let { scale ->
                logIdeZoomChanged(scale, false)
                resetZoom?.visible(scale.percentValue != defaultScale.percentValue)
                settings.ideScale = scale
                invokeLater {
                  // Invoke later to avoid NPE in JComboBox.repaint()
                  settings.fireUISettingsChanged()
                }
              }
            }
            .applyToComponent {
              isEditable = true
            }
            .validationOnInput {
              IdeScaleTransformer.Settings.validatePercentScaleInput(this, it, false)
            }
            .gap(RightGap.SMALL)

          val zoomInString = KeymapUtil.getShortcutTextOrNull("ZoomInIdeAction")
          val zoomOutString = KeymapUtil.getShortcutTextOrNull("ZoomOutIdeAction")
          val resetScaleString = KeymapUtil.getShortcutTextOrNull("ResetIdeScaleAction")

          if (zoomInString != null && zoomOutString != null && resetScaleString != null) {
            comment(message("combobox.ide.scale.comment.format", zoomInString, zoomOutString, resetScaleString))
          }

          resetZoom = link(message("ide.scale.reset.link")) {
            model.selectedItem = defaultScale.percentStringValue
          }.apply { visible(settings.ideScale.percentValue != defaultScale.percentValue) }
        }.topGap(TopGap.SMALL)

        row {
          var resetCustomFont: (() -> Unit)? = null

          val useCustomCheckbox = checkBox(message("checkbox.override.default.laf.fonts"))
            .gap(RightGap.SMALL)
            .bindSelected(settings::overrideLafFonts) {
              NotRoamableUiSettings.getInstance().overrideLafFonts = it
              if (!it) {
                getDefaultFont().let { defaultFont ->
                  settings.fontFace = defaultFont.family
                  settings.fontSize = defaultFont.size
                }
              }
            }
            .onChanged { checkbox ->
              if (!checkbox.isSelected) resetCustomFont?.invoke()
            }

          val fontFace = cell(FontComboBox())
            .bind({ it.fontName }, { it, value -> it.fontName = value },
                  MutableProperty({ if (settings.overrideLafFonts) getFontFamily(settings.fontFace) else getDefaultFont().family },
                                  { settings.fontFace = it }))
            .enabledIf(useCustomCheckbox.selected)
            .accessibleName(message("label.font.name"))
            .component

          val fontSize = fontSizeComboBox({ if (settings.overrideLafFonts) settings.fontSize else getDefaultFont().size },
                                          { settings.fontSize = it },
                                          settings.fontSize)
            .label(message("label.font.size"))
            .enabledIf(useCustomCheckbox.selected)
            .accessibleName(message("label.font.size"))
            .component

          resetCustomFont = {
            val defaultFont = getDefaultFont()
            fontFace.fontName = defaultFont.family
            val fontSizeValue = defaultFont.size.toString()
            fontSize.selectedItem = fontSizeValue
            fontSize.editor.item = fontSizeValue
          }
        }.topGap(TopGap.SMALL)

        row {
          val isOverridden = isSupportScreenReadersOverridden()
          val ctrlTab = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK))
          val ctrlShiftTab = KeymapUtil.getKeystrokeText(
            KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK))
          checkBox(message("checkbox.support.screen.readers"))
            .bindSelected(generalSettings::isSupportScreenReaders) { generalSettings.isSupportScreenReaders = it }
            .comment(message("support.screen.readers.tab", ctrlTab, ctrlShiftTab))
            .enabled(!isOverridden)

          comment(if (isOverridden) message("overridden.by.jvm.property", GeneralSettings.SUPPORT_SCREEN_READERS)
                  else message("ide.restart.required.comment"))
        }

        row {
          checkBox(cdUseContrastToolbars)
        }

        val supportedValues = ColorBlindness.entries.filter { ColorBlindnessSupport.get(it) != null }
        if (supportedValues.isNotEmpty()) {
          val colorBlindnessProperty = MutableProperty({ settings.colorBlindness }, { settings.colorBlindness = it })
          val onApply = {
            // callback executed not when all changes are applied, but one component by one, so, reload later when everything was applied
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
                      colorBlindnessProperty)
                .onApply(onApply)
            }
            else {
              val enableColorBlindness = checkBox(UIBundle.message("color.blindness.combobox.text"))
                .selected(colorBlindnessProperty.get() != null)
              comboBox(supportedValues)
                .enabledIf(enableColorBlindness.selected)
                .applyToComponent { renderer = SimpleListCellRenderer.create("") { PlatformEditorBundle.message(it.key) } }
                .comment(UIBundle.message("color.blindness.combobox.comment"))
                .bind({ if (enableColorBlindness.component.isSelected) it.selectedItem as? ColorBlindness else null },
                      { it, value -> it.selectedItem = value ?: supportedValues.first() },
                      colorBlindnessProperty)
                .onApply(onApply)
                .accessibleName(UIBundle.message("color.blindness.checkbox.text"))
            }

            link(UIBundle.message("color.blindness.link.to.help")
            ) { HelpManager.getInstance().invokeHelp("Colorblind_Settings") }
          }
        }
      }

      groupRowsRange(message("group.ui.options")) {
        val leftColumnControls = sequence<Row.() -> Unit> {
          if (ExperimentalUI.isNewUI()) {
            yield {
              checkBox(message("checkbox.compact.mode"))
                .bindSelected(UISettings.getInstance()::compactMode)
                .comment(message("checkbox.compact.mode.description"))
                .onApply {
                  LafManager.getInstance().applyDensity()
                }
            }
          }
          yield { checkBox(cdFullPathsInTitleBar) }
          if (!ExperimentalUI.isNewUI()) {
            yield { checkBox(cdDnDWithAlt) }
            yield {
              checkBox(cdSmoothScrolling)
                .gap(RightGap.SMALL)
              contextHelp(message("checkbox.smooth.scrolling.description"))
            }
          }
          if (ProjectWindowCustomizerService.getInstance().isAvailable()) {
            yield {
              checkBox(cdDifferentiateProjects)
                .enabledIf(AtomicBooleanProperty(ExperimentalUI.isNewUI()))
                .comment(cdDifferentiateProjects.comment, 30)
            }
          }
          if (!SystemInfo.isMac && ExperimentalUI.isNewUI()) {
            yield {
              checkBox(cdSeparateMainMenu).apply {
                if (!SystemInfo.isWindows) {
                  comment(message("ide.restart.required.comment"))
                }
              }
            }
          }
          if (SystemInfo.isMac && MacCustomAppIcon.available()) {
            yield {
              checkBox(message("checkbox.ide.mac.app.icon")).comment(message("ide.restart.required.comment"))
                .bindSelected({ MacCustomAppIcon.isCustom() }, { MacCustomAppIcon.setCustom(it, true) })
            }
          }
        }
        val rightColumnControls = sequence<Row.() -> Unit> {
          if (ExperimentalUI.isNewUI()) {
            yield { checkBox(cdDnDWithAlt) }
            yield {
              checkBox(cdSmoothScrolling)
                .gap(RightGap.SMALL)
              contextHelp(message("checkbox.smooth.scrolling.description"))
            }
          }
          yield { checkBox(cdEnableControlsMnemonics) }
          yield { checkBox(cdEnableMenuMnemonics) }
          yield { checkBox(cdShowMenuIcons) }
          yield { checkBox(cdKeepPopupsForToggles) }
          if (SystemInfoRt.isWindows && IdeFrameDecorator.isCustomDecorationAvailable || CustomWindowHeaderUtil.hideNativeLinuxTitleAvailable) {
            yield {
              val checkBox = checkBox(cdMergeMainMenuWithWindowTitle)
                .gap(RightGap.SMALL)
              if (SystemInfoRt.isWindows && isMergeMainMenuWithWindowTitleOverridden) {
                checkBox.enabled(false)
                contextHelp(message("option.is.overridden.by.jvm.property", MERGE_MAIN_MENU_WITH_WINDOW_TITLE_PROPERTY))
              }
              if (SystemInfo.isUnix && !SystemInfo.isMac && !CustomWindowHeaderUtil.hideNativeLinuxTitleSupported) {
                checkBox.enabled(false)
                checkBox.comment(message("checkbox.merge.main.menu.with.window.not.supported.comment"), 30)
              }
              else {
                comment(message("ide.restart.required.comment"))
              }
            }
          }
        }

        // Since some of the columns have variable number of items, enumerate them in a loop, while moving orphaned items from the right
        // column to the left one:
        val leftIt = leftColumnControls.iterator()
        val rightIt = rightColumnControls.iterator()

        if (ExperimentalUI.isNewUI()) {
          twoColumnsRow(
            {
              panel {
                while (leftIt.hasNext()) {
                  row {
                    leftIt.next().invoke(this)
                  }
                }
              }
            },
            {
              panel {
                while (rightIt.hasNext()) {
                  row {
                    rightIt.next().invoke(this)
                  }
                }
              }
            })
        }
        else {
          while (leftIt.hasNext() || rightIt.hasNext()) {
            when {
              leftIt.hasNext() && rightIt.hasNext() -> twoColumnsRow(leftIt.next(), rightIt.next())
              leftIt.hasNext() -> twoColumnsRow(leftIt.next())
              rightIt.hasNext() -> twoColumnsRow(rightIt.next()) // move from right to left
            }
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

      groupRowsRange(message("group.trees")) {
        twoColumnsRow(
          { checkBox(cdShowTreeIndents) },
          { checkBox(cdUseCompactTreeIndents) },
        )
        twoColumnsRow(
          { checkBox(cdExpandNodesWithSingleClick).visible(Tree.isExpandWithSingleClickSettingEnabled()) },
        )
      }

      groupRowsRange(message("group.window.options")) {
        twoColumnsRow(
          { checkBox(cdShowToolWindowBars) },
          { checkBox(cdLeftToolWindowLayout) },
        )
        if (ExperimentalUI.isNewUI()) {
          if (ResizeStripeManager.enabled()) {
            twoColumnsRow(
              {
                checkBox(cdShowToolWindowNames).gap(RightGap.SMALL).onApply {
                  ResizeStripeManager.applyShowNames()
                }
              },
              { checkBox(cdRightToolWindowLayout) },
            )
            twoColumnsRow(
              {
                checkBox(cdWidescreenToolWindowLayout)
                  .gap(RightGap.SMALL)
                contextHelp(message("checkbox.widescreen.tool.window.layout.description"))
              },
              { checkBox(cdRememberSizeForEachToolWindowNewUI) },
            )
          }
          else {
            twoColumnsRow(
              {
                checkBox(cdWidescreenToolWindowLayout)
                  .gap(RightGap.SMALL)
                contextHelp(message("checkbox.widescreen.tool.window.layout.description"))
              },
              { checkBox(cdRightToolWindowLayout) },
            )
            twoColumnsRow(
              { checkBox(cdRememberSizeForEachToolWindowNewUI) },
            )
          }
        }
        else {
          twoColumnsRow(
            {
              checkBox(cdWidescreenToolWindowLayout)
                .gap(RightGap.SMALL)
              contextHelp(message("checkbox.widescreen.tool.window.layout.description"))
            },
            { checkBox(cdRightToolWindowLayout) },
          )
          twoColumnsRow(
            { checkBox(cdShowToolWindowNumbers) },
            { checkBox(cdRememberSizeForEachToolWindowOldUI) },
          )
        }
      }

      group(message("group.presentation.mode")) {
        row(message("presentation.mode.ide.scale")) {
          comboBox(IdeScaleTransformer.Settings.createPresentationModeScaleComboboxModel(), textListCellRenderer { it })
            .bindItem({ settings.presentationModeIdeScale.percentStringValue }, { })
            .applyToComponent {
              isEditable = true
            }
            .validationOnInput {
              IdeScaleTransformer.Settings.validatePercentScaleInput(this, it, true)
            }
            .onChanged {
              if (IdeScaleTransformer.Settings.validatePercentScaleInput(it.item, true) != null) return@onChanged

              IdeScaleTransformer.Settings.scaleFromPercentStringValue(it.item, true)?.let { scale ->
                logIdeZoomChanged(scale, true)
                settings.presentationModeIdeScale = scale
                if (settings.presentationMode) {
                  settings.fireUISettingsChanged()
                }
              }
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
              if (!AntialiasingType.canUseSubpixelAAForIDE()) {
                arrayOf(AntialiasingType.GREYSCALE, AntialiasingType.OFF)
              }
              else {
                AntialiasingType.entries.toTypedArray()
              }
            comboBox(DefaultComboBoxModel(ideAAOptions), renderer = AAListCellRenderer(false))
              .label(message("label.text.antialiasing.scope.ide"))
              .bindItem(settings::ideAAType.toNullableProperty())
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
              if (!AntialiasingType.canUseSubpixelAAForEditor()) {
                arrayOf(AntialiasingType.GREYSCALE, AntialiasingType.OFF)
              }
              else {
                AntialiasingType.entries.toTypedArray()
              }
            comboBox(DefaultComboBoxModel(editorAAOptions), renderer = AAListCellRenderer(true))
              .label(message("label.text.antialiasing.scope.editor"))
              .bindItem(settings::editorAAType.toNullableProperty())
              .accessibleName(message("label.text.antialiasing.scope.editor"))
          }
        )
      }
    }
  }

  override fun apply() {
    val uiSettingsChanged = isModified
    super.apply()
    if (uiSettingsChanged) {
      UISettings.getInstance().fireUISettingsChanged()
      EditorFactory.getInstance().refreshAllEditors()
    }
  }
}

private fun getFontFamily(fontFace: String?): String {
  val defaultFontFamily = JBUIScale.getSystemFontDataIfInitialized()?.first
  if (fontFace == null || fontFace == defaultFontFamily) {
    return Font(defaultFontFamily, Font.PLAIN, 13).family
  }
  else {
    return fontFace
  }
}

private fun getDefaultFont(): Font {
  val lafManager = LafManager.getInstance() as? LafManagerImpl
  return lafManager?.defaultFont ?: JBFont.label()
}

private fun Row.fontSizeComboBox(getter: () -> Int, setter: (Int) -> Unit, defaultValue: Int): Cell<ComboBox<String>> {
  return fontSizeComboBox(MutableProperty({ getter().toString() }, { setter(getIntValue(it, defaultValue)) }))
}

private fun Row.fontSizeComboBox(prop: MutableProperty<@Nls String?>): Cell<ComboBox<String>> {
  val model = DefaultComboBoxModel(UIUtil.getStandardFontSizes())
  return comboBox(model)
    .accessibleName(message("presentation.mode.fon.size"))
    .applyToComponent {
      isEditable = true
      renderer = textListCellRenderer { it }
      selectedItem = prop.get()
    }
    .bind(
      { component -> component.editor.item as String? },
      { component, value -> component.setSelectedItem(value) },
      prop
    )
}

private fun getIntValue(text: String?, defaultValue: Int): Int {
  if (!text.isNullOrBlank()) {
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

private fun logIdeZoomChanged(value: Float, isPresentation: Boolean) {
  val oldScale = if (isPresentation) settings.presentationModeIdeScale else settings.ideScale

  IdeZoomChanged.log(
    IdeZoomEventFields.zoomMode.with(if (value.percentValue > oldScale.percentValue) IdeZoomEventFields.ZoomMode.ZOOM_IN
                                     else IdeZoomEventFields.ZoomMode.ZOOM_OUT),
    IdeZoomEventFields.place.with(IdeZoomEventFields.Place.SETTINGS),
    IdeZoomEventFields.zoomScalePercent.with(value.percentValue),
    IdeZoomEventFields.presentationMode.with(isPresentation)
  )
}

@Internal
class LafComboBoxModelWrapper(private val lafComboBoxModelProvider: () -> CollectionComboBoxModel<LafReference>): ComboBoxModel<LafReference> {
  private var lafComboBoxModel: CollectionComboBoxModel<LafReference> = lafComboBoxModelProvider()
  private val moreAction = LafReference(name = message("link.get.more.themes"), themeId = "")
  private val additionalItems = listOf(moreAction)
  var comboBoxComponent: ComboBox<LafReference>? = null
  private val listDataListener = object : ListDataListener {
    override fun intervalAdded(e: ListDataEvent?) {
      recreateModelIfNeeded()
    }

    override fun intervalRemoved(e: ListDataEvent?) {
      recreateModelIfNeeded()
    }

    override fun contentsChanged(e: ListDataEvent?) {
      recreateModelIfNeeded()
    }
  }

  init {
    lafComboBoxModel.addListDataListener(listDataListener)
  }

  private fun recreateModelIfNeeded() {
    if (lafComboBoxModel === lafComboBoxModelProvider()) return
    val comboBoxComponent = comboBoxComponent ?: return

    lafComboBoxModel.removeListDataListener(comboBoxComponent)
    lafComboBoxModel.removeListDataListener(listDataListener)

    lafComboBoxModel = lafComboBoxModelProvider()

    comboBoxComponent.model = this
    lafComboBoxModel.addListDataListener(listDataListener)
  }

  override fun getSize(): Int = lafComboBoxModel.size.let { if (it > 0) it + additionalItems.size else it }

  override fun getElementAt(index: Int): LafReference =
    if (index < lafComboBoxModel.size) lafComboBoxModel.getElementAt(index)
    else additionalItems[index - lafComboBoxModel.size]

  override fun addListDataListener(l: ListDataListener?) {
    lafComboBoxModel.addListDataListener(l)
  }

  override fun removeListDataListener(l: ListDataListener?) {
    lafComboBoxModel.removeListDataListener(l)
  }

  override fun setSelectedItem(anItem: Any?) {
    if (anItem == moreAction) {
      val themeTag = "/tag:Theme"
      val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(comboBoxComponent))

      if (settings == null) {
        ShowSettingsUtil.getInstance().showSettingsDialog(ProjectManager.getInstance().defaultProject,
                                                          PluginManagerConfigurable::class.java) { c: PluginManagerConfigurable ->
          c.enableSearch(themeTag)
        }
      }
      else {
        settings.select(settings.find("preferences.pluginManager"), themeTag)
      }
    }
    else lafComboBoxModel.selectedItem = anItem
  }

  override fun getSelectedItem(): Any? = lafComboBoxModel.selectedItem
}
