// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.TableActions;
import com.intellij.ui.plaf.beg.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public final class IdeaLaf extends MetalLookAndFeel {

  public static final ColorUIResource TOOLTIP_BACKGROUND_COLOR = new ColorUIResource(255, 255, 231);

  @Override
  public void initComponentDefaults(UIDefaults defaults) {
    super.initComponentDefaults(defaults);
    LafManagerImpl.initInputMapDefaults(defaults);
    initIdeaDefaults(defaults);

    Pair<String, Integer> systemFont = JBUIScale.getSystemFontData();
    LafManagerImpl.initFontDefaults(defaults, UIUtil.getFontWithFallback(systemFont.first, Font.PLAIN, systemFont.second));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  static void initIdeaDefaults(UIDefaults defaults) {
    defaults.put("Menu.maxGutterIconWidth", 18);
    defaults.put("MenuItem.maxGutterIconWidth", 18);
    // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
    defaults.put("MenuItem.acceleratorDelimiter", "-");

    defaults.put("TitledBorder.titleColor", IdeaBlueMetalTheme.primary1);
    ColorUIResource col = new ColorUIResource(230, 230, 230);
    defaults.put("ScrollBar.background", col);
    defaults.put("ScrollBar.track", col);

    defaults.put("TextField.border", BegBorders.getTextFieldBorder());
    defaults.put("PasswordField.border", BegBorders.getTextFieldBorder());
    Border popupMenuBorder = new BegPopupMenuBorder();
    defaults.put("PopupMenu.border", popupMenuBorder);
    defaults.put("ScrollPane.border", BegBorders.getScrollPaneBorder());

    defaults.put("ToggleButtonUI", BegToggleButtonUI.class.getName());
    defaults.put("RadioButtonUI", BegRadioButtonUI.class.getName());
    defaults.put("TabbedPaneUI", BegTabbedPaneUI.class.getName());
    defaults.put("TableUI", BegTableUI.class.getName());
    defaults.put("TreeUI", BegTreeUI.class.getName());

    defaults.put("TabbedPane.tabInsets", new Insets(0, 4, 0, 4));
    defaults.put("ToolTip.background", TOOLTIP_BACKGROUND_COLOR);
    defaults.put("ToolTip.border", new ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1));
    defaults.put("Tree.ancestorInputMap", null);
    defaults.put("FileView.directoryIcon", AllIcons.Nodes.Folder);
    defaults.put("FileChooser.upFolderIcon", AllIcons.Nodes.UpFolder);
    defaults.put("FileChooser.newFolderIcon", AllIcons.Nodes.Folder);
    defaults.put("FileChooser.homeFolderIcon", AllIcons.Nodes.HomeFolder);
    defaults.put("OptionPane.errorIcon", AllIcons.General.ErrorDialog);
    defaults.put("OptionPane.informationIcon", AllIcons.General.InformationDialog);
    defaults.put("OptionPane.warningIcon", AllIcons.General.WarningDialog);
    defaults.put("OptionPane.questionIcon", AllIcons.General.QuestionDialog);
    //defaults.put("Tree.openIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeOpen.gif"));
    //defaults.put("Tree.closedIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeClosed.gif"));
    //defaults.put("Tree.leafIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeLeaf.gif"));
    //defaults.put("Tree.expandedIcon", WindowsTreeUI.ExpandedIcon.createExpandedIcon());
    //defaults.put("Tree.collapsedIcon", WindowsTreeUI.CollapsedIcon.createCollapsedIcon());
    defaults.put("Table.ancestorInputMap", new UIDefaults.LazyInputMap(new Object[] {
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
                    "shift TAB", "selectPreviousColumnCell",
                        //"ENTER", "selectNextRowCell",
                  "shift ENTER", "selectPreviousRowCell",
                       "ctrl A", "selectAll",
                       //"ESCAPE", "cancel",
                           "F2", "startEditing"
         }));
  }
}
