// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.ide.ui.laf

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredSideBorder
import com.intellij.ui.TableActions
import com.intellij.ui.plaf.beg.*
import com.intellij.ui.scale.JBUIScale.getSystemFontData
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import javax.swing.UIDefaults
import javax.swing.UIDefaults.LazyValue
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
class IdeaLaf(private val customFontDefaults: Map<Any, Any?>?) : MetalLookAndFeel() {
  public override fun initComponentDefaults(defaults: UIDefaults) {
    super.initComponentDefaults(defaults)

    initInputMapDefaults(defaults)
    initIdeaDefaults(defaults)
    if (customFontDefaults == null) {
      val systemFont = getSystemFontData(uiDefaults = { defaults })
      initFontDefaults(defaults, StartupUiUtil.getFontWithFallback(familyName = systemFont.first, style = Font.PLAIN, size = systemFont.second.toFloat()))
    }
    else {
      defaults.putAll(customFontDefaults)
    }
  }
}

@Suppress("UseDPIAwareInsets", "UseJBColor")
private fun initIdeaDefaults(defaults: UIDefaults) {
  defaults.put("Menu.maxGutterIconWidth", 18)
  defaults.put("MenuItem.maxGutterIconWidth", 18)
  // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
  defaults.put("MenuItem.acceleratorDelimiter", "-")
  defaults.put("TitledBorder.titleColor", ColorUIResource(10, 36, 106))
  val colorUiResource = ColorUIResource(230, 230, 230)
  defaults.put("ScrollBar.background", colorUiResource)
  defaults.put("ScrollBar.track", colorUiResource)
  defaults.put("TextField.border", LazyValue { BegBorders.getTextFieldBorder() })
  defaults.put("PasswordField.border", LazyValue { BegBorders.getTextFieldBorder() })
  defaults.put("PopupMenu.border", LazyValue { BegPopupMenuBorder() })
  defaults.put("ScrollPane.border", LazyValue { BegBorders.getScrollPaneBorder() })
  defaults.put("ToggleButtonUI", LazyValue { BegToggleButtonUI::class.java.getName() })
  defaults.put("RadioButtonUI", LazyValue { BegRadioButtonUI::class.java.getName() })
  defaults.put("TabbedPaneUI", LazyValue { BegTabbedPaneUI::class.java.getName() })
  defaults.put("TableUI", LazyValue { BegTableUI::class.java.getName() })
  defaults.put("TreeUI", LazyValue { BegTreeUI::class.java.getName() })
  defaults.put("TabbedPane.tabInsets", Insets(0, 4, 0, 4))
  defaults.put("ToolTip.background", ColorUIResource(255, 255, 231))
  defaults.put("ToolTip.border", ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1))
  defaults.put("Tree.ancestorInputMap", null)
  defaults.put("FileView.directoryIcon", LazyValue { AllIcons.Nodes.Folder })
  defaults.put("FileChooser.upFolderIcon", LazyValue { AllIcons.Nodes.UpFolder })
  defaults.put("FileChooser.newFolderIcon", LazyValue { AllIcons.Nodes.Folder })
  defaults.put("FileChooser.homeFolderIcon", LazyValue { AllIcons.Nodes.HomeFolder })
  defaults.put("OptionPane.errorIcon", LazyValue { AllIcons.General.ErrorDialog })
  defaults.put("OptionPane.informationIcon", LazyValue { AllIcons.General.InformationDialog })
  defaults.put("OptionPane.warningIcon", LazyValue { AllIcons.General.WarningDialog })
  defaults.put("OptionPane.questionIcon", LazyValue { AllIcons.General.QuestionDialog })
  defaults.put("Table.ancestorInputMap", LazyValue {
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
  })
}
