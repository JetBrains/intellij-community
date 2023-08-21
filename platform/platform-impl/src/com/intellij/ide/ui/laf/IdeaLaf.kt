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
import java.util.function.Supplier
import javax.swing.UIDefaults
import javax.swing.plaf.ColorUIResource
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * @author Konstantin Bulenkov
 */
internal class IdeaLaf(private val myCustomFontDefaults: Map<Any, Any>?) : MetalLookAndFeel() {
  public override fun initComponentDefaults(defaults: UIDefaults) {
    super.initComponentDefaults(defaults)
    initInputMapDefaults(defaults)
    initIdeaDefaults(defaults)
    if (myCustomFontDefaults != null) {
      defaults.putAll(myCustomFontDefaults)
    }
    else {
      val systemFont = getSystemFontData(
        Supplier { defaults })
      initFontDefaults(defaults, getFontWithFallback(systemFont.first, Font.PLAIN, systemFont.second.toFloat()))
    }
  }

  companion object {
    val TOOLTIP_BACKGROUND_COLOR: ColorUIResource = ColorUIResource(255, 255, 231)
    @Suppress("HardCodedStringLiteral")
    fun initIdeaDefaults(defaults: UIDefaults) {
      defaults["Menu.maxGutterIconWidth"] = 18
      defaults["MenuItem.maxGutterIconWidth"] = 18
      // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
      defaults["MenuItem.acceleratorDelimiter"] = "-"
      defaults["TitledBorder.titleColor"] = ColorUIResource(10, 36, 106)
      val col = ColorUIResource(230, 230, 230)
      defaults["ScrollBar.background"] = col
      defaults["ScrollBar.track"] = col
      defaults["TextField.border"] = createLazyValue(Supplier { BegBorders.getTextFieldBorder() })
      defaults["PasswordField.border"] = createLazyValue(Supplier { BegBorders.getTextFieldBorder() })
      defaults["PopupMenu.border"] = createLazyValue(Supplier { BegPopupMenuBorder() })
      defaults["ScrollPane.border"] = createLazyValue(Supplier { BegBorders.getScrollPaneBorder() })
      defaults["ToggleButtonUI"] = createLazyValue(Supplier { BegToggleButtonUI::class.java.getName() })
      defaults["RadioButtonUI"] = createLazyValue(Supplier { BegRadioButtonUI::class.java.getName() })
      defaults["TabbedPaneUI"] = createLazyValue(Supplier { BegTabbedPaneUI::class.java.getName() })
      defaults["TableUI"] = createLazyValue(Supplier { BegTableUI::class.java.getName() })
      defaults["TreeUI"] = createLazyValue(Supplier { BegTreeUI::class.java.getName() })
      defaults["TabbedPane.tabInsets"] = Insets(0, 4, 0, 4)
      defaults["ToolTip.background"] = TOOLTIP_BACKGROUND_COLOR
      defaults["ToolTip.border"] = ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1)
      defaults["Tree.ancestorInputMap"] = null
      defaults["FileView.directoryIcon"] = createLazyValue(Supplier { AllIcons.Nodes.Folder })
      defaults["FileChooser.upFolderIcon"] = createLazyValue(Supplier { AllIcons.Nodes.UpFolder })
      defaults["FileChooser.newFolderIcon"] = createLazyValue(Supplier { AllIcons.Nodes.Folder })
      defaults["FileChooser.homeFolderIcon"] = createLazyValue(Supplier { AllIcons.Nodes.HomeFolder })
      defaults["OptionPane.errorIcon"] = createLazyValue(Supplier { AllIcons.General.ErrorDialog })
      defaults["OptionPane.informationIcon"] = createLazyValue(Supplier { AllIcons.General.InformationDialog })
      defaults["OptionPane.warningIcon"] = createLazyValue(Supplier { AllIcons.General.WarningDialog })
      defaults["OptionPane.questionIcon"] = createLazyValue(Supplier { AllIcons.General.QuestionDialog })
      defaults["Table.ancestorInputMap"] = UIDefaults.LazyValue {
        makeInputMap(arrayOf<Any>(
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

    fun fillFallbackDefaults(defaults: UIDefaults) {
      // These icons are only needed to prevent Swing from trying to fetch defaults with AWT ImageFetcher threads (IDEA-322089),
      // but might as well just put something sensibly-looking there, just in case they show up due to some bug:
      val folderIcon = createLazyValue(Supplier { AllIcons.Nodes.Folder })
      defaults["Tree.openIcon"] = folderIcon
      defaults["Tree.closedIcon"] = folderIcon
      defaults["Tree.leafIcon"] = createLazyValue(Supplier { AllIcons.FileTypes.Any_type })
      // These two are actually set by our themes, so we don't want to override them here:
      //defaults.put("Tree.expandedIcon", AllIcons.Toolbar.Expand);
      //defaults.put("Tree.collapsedIcon", AllIcons.Actions.ArrowExpand);
    }

    private fun createLazyValue(supplier: Supplier<Any>): UIDefaults.LazyValue {
      return UIDefaults.LazyValue { supplier.get() }
    }
  }
}
