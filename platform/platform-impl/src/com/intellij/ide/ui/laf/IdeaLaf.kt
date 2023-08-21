// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredSideBorder
import com.intellij.ui.TableActions
import com.intellij.ui.plaf.beg.*
import com.intellij.ui.scale.JBUIScale.getSystemFontData
import com.intellij.util.ui.StartupUiUtil.getFontWithFallback
import com.intellij.util.ui.StartupUiUtil.initFontDefaults
import com.intellij.util.ui.StartupUiUtil.initInputMapDefaults
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import javax.swing.UIDefaults
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * @author Konstantin Bulenkov
 */
internal class IdeaLaf(private val customFontDefaults: Map<Any, Any>?) : MetalLookAndFeel() {
  public override fun initComponentDefaults(defaults: UIDefaults) {
    super.initComponentDefaults(defaults)

    initInputMapDefaults(defaults)
    initIdeaDefaults(defaults)
    if (customFontDefaults == null) {
      val systemFont = getSystemFontData(uiDefaults = { defaults })
      initFontDefaults(defaults, getFontWithFallback(familyName = systemFont.first, style = Font.PLAIN, size = systemFont.second.toFloat()))
    }
    else {
      defaults.putAll(customFontDefaults)
    }
  }
}

private val TOOLTIP_BACKGROUND_COLOR = ColorUIResource(255, 255, 231)

@Suppress("UseDPIAwareInsets", "UseJBColor")
private fun initIdeaDefaults(defaults: UIDefaults) {
  defaults["Menu.maxGutterIconWidth"] = 18
  defaults["MenuItem.maxGutterIconWidth"] = 18
  // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
  defaults["MenuItem.acceleratorDelimiter"] = "-"
  defaults["TitledBorder.titleColor"] = ColorUIResource(10, 36, 106)
  val colorUiResource = ColorUIResource(230, 230, 230)
  defaults["ScrollBar.background"] = colorUiResource
  defaults["ScrollBar.track"] = colorUiResource
  defaults["TextField.border"] = createLazyValue { BegBorders.getTextFieldBorder() }
  defaults["PasswordField.border"] = createLazyValue { BegBorders.getTextFieldBorder() }
  defaults["PopupMenu.border"] = createLazyValue { BegPopupMenuBorder() }
  defaults["ScrollPane.border"] = createLazyValue { BegBorders.getScrollPaneBorder() }
  defaults["ToggleButtonUI"] = createLazyValue { BegToggleButtonUI::class.java.getName() }
  defaults["RadioButtonUI"] = createLazyValue { BegRadioButtonUI::class.java.getName() }
  defaults["TabbedPaneUI"] = createLazyValue { BegTabbedPaneUI::class.java.getName() }
  defaults["TableUI"] = createLazyValue { BegTableUI::class.java.getName() }
  defaults["TreeUI"] = createLazyValue { BegTreeUI::class.java.getName() }
  defaults["TabbedPane.tabInsets"] = Insets(0, 4, 0, 4)
  defaults["ToolTip.background"] = TOOLTIP_BACKGROUND_COLOR
  defaults["ToolTip.border"] = ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1)
  defaults["Tree.ancestorInputMap"] = null
  defaults["FileView.directoryIcon"] = createLazyValue { AllIcons.Nodes.Folder }
  defaults["FileChooser.upFolderIcon"] = createLazyValue { AllIcons.Nodes.UpFolder }
  defaults["FileChooser.newFolderIcon"] = createLazyValue { AllIcons.Nodes.Folder }
  defaults["FileChooser.homeFolderIcon"] = createLazyValue { AllIcons.Nodes.HomeFolder }
  defaults["OptionPane.errorIcon"] = createLazyValue { AllIcons.General.ErrorDialog }
  defaults["OptionPane.informationIcon"] = createLazyValue { AllIcons.General.InformationDialog }
  defaults["OptionPane.warningIcon"] = createLazyValue { AllIcons.General.WarningDialog }
  defaults["OptionPane.questionIcon"] = createLazyValue { AllIcons.General.QuestionDialog }
  defaults["Table.ancestorInputMap"] = UIDefaults.LazyValue {
    MetalLookAndFeel.makeInputMap(arrayOf<Any>(
      "ctrl C", "copy",
      "ctrl V", "paste",
      "ctrl X", "cut",
      "COPY", "copy",
      "PASTE", "paste",
      "CUT", "cut",
      "control INSERT", "copy",
      "shift INSERT", "paste",
      "shift DELETE", "cut",
      "RIGHT", TableActions.Right.ID,
      "KP_RIGHT", TableActions.Right.ID,
      "LEFT", TableActions.Left.ID,
      "KP_LEFT", TableActions.Left.ID,
      "DOWN", TableActions.Down.ID,
      "KP_DOWN", TableActions.Down.ID,
      "UP", TableActions.Up.ID,
      "KP_UP", TableActions.Up.ID,
      "shift RIGHT", TableActions.ShiftRight.ID,
      "shift KP_RIGHT", TableActions.ShiftRight.ID,
      "shift LEFT", TableActions.ShiftLeft.ID,
      "shift KP_LEFT", TableActions.ShiftLeft.ID,
      "shift DOWN", TableActions.ShiftDown.ID,
      "shift KP_DOWN", TableActions.ShiftDown.ID,
      "shift UP", TableActions.ShiftUp.ID,
      "shift KP_UP", TableActions.ShiftUp.ID,
      "PAGE_UP", TableActions.PageUp.ID,
      "PAGE_DOWN", TableActions.PageDown.ID,
      "HOME", "selectFirstColumn",
      "END", "selectLastColumn",
      "shift PAGE_UP", TableActions.ShiftPageUp.ID,
      "shift PAGE_DOWN", TableActions.ShiftPageDown.ID,
      "shift HOME", "selectFirstColumnExtendSelection",
      "shift END", "selectLastColumnExtendSelection",
      "ctrl PAGE_UP", "scrollLeftChangeSelection",
      "ctrl PAGE_DOWN", "scrollRightChangeSelection",
      "ctrl HOME", TableActions.CtrlHome.ID,
      "ctrl END", TableActions.CtrlEnd.ID,
      "ctrl shift PAGE_UP", "scrollRightExtendSelection",
      "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
      "ctrl shift HOME", TableActions.CtrlShiftHome.ID,
      "ctrl shift END", TableActions.CtrlShiftEnd.ID,
      "TAB", "selectNextColumnCell",
      "shift TAB", "selectPreviousColumnCell",  //"ENTER", "selectNextRowCell",
      "shift ENTER", "selectPreviousRowCell",
      "ctrl A", "selectAll",  //"ESCAPE", "cancel",
      "F2", "startEditing"
    ))
  }
}

internal fun fillFallbackDefaults(defaults: UIDefaults) {
  // These icons are only needed to prevent Swing from trying to fetch defaults with AWT ImageFetcher threads (IDEA-322089),
  // but might as well just put something sensibly-looking there, just in case they show up due to some bug:
  val folderIcon = createLazyValue { AllIcons.Nodes.Folder }
  defaults["Tree.openIcon"] = folderIcon
  defaults["Tree.closedIcon"] = folderIcon
  defaults["Tree.leafIcon"] = createLazyValue { AllIcons.FileTypes.Any_type }
  // Our themes actually set these two, so we don't want to override them here:
  //defaults.put("Tree.expandedIcon", AllIcons.Toolbar.Expand);
  //defaults.put("Tree.collapsedIcon", AllIcons.Actions.ArrowExpand);
}

private inline fun createLazyValue(crossinline supplier: () -> Any): UIDefaults.LazyValue {
  return UIDefaults.LazyValue { supplier() }
}

