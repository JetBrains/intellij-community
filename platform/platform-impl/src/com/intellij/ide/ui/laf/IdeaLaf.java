/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.ui.laf;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.plaf.beg.*;
import com.intellij.util.ui.UIUtil;
import com.sun.java.swing.plaf.windows.WindowsLookAndFeel;
import com.sun.java.swing.plaf.windows.WindowsTreeUI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;

/**
* @author Konstantin Bulenkov
*/
public final class IdeaLaf extends MetalLookAndFeel {

  public static final ColorUIResource TOOLTIP_BACKGROUND_COLOR = new ColorUIResource(255, 255, 231);

  public void initComponentDefaults(UIDefaults defaults) {
    super.initComponentDefaults(defaults);
    LafManagerImpl.initInputMapDefaults(defaults);
    initIdeaDefaults(defaults);

    Pair<String, Integer> systemFont = UIUtil.getSystemFontData();
    if (systemFont != null) {
      LafManagerImpl.initFontDefaults(defaults, new FontUIResource(systemFont.first, Font.PLAIN, systemFont.second));
    }
    else {
      LafManagerImpl.initFontDefaults(defaults, new FontUIResource("Tahoma", Font.PLAIN, 11));
    }
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

    //Border scrollPaneBorder = new BorderUIResource(new BegBorders.ScrollPaneBorder());
    //defaults.put("ScrollPane.border", scrollPaneBorder);
    defaults.put("TextField.border", BegBorders.getTextFieldBorder());
    defaults.put("PasswordField.border", BegBorders.getTextFieldBorder());
    Border popupMenuBorder = new BegPopupMenuBorder();
    defaults.put("PopupMenu.border", popupMenuBorder);
    defaults.put("ScrollPane.border", BegBorders.getScrollPaneBorder());

    defaults.put("ButtonUI", BegButtonUI.class.getName());
    defaults.put("ToggleButtonUI", BegToggleButtonUI.class.getName());
    defaults.put("ComboBoxUI", BegComboBoxUI.class.getName());
    defaults.put("RadioButtonUI", BegRadioButtonUI.class.getName());
    defaults.put("CheckBoxUI", BegCheckBoxUI.class.getName());
    defaults.put("TabbedPaneUI", BegTabbedPaneUI.class.getName());
    defaults.put("TableUI", BegTableUI.class.getName());
    defaults.put("TreeUI", BegTreeUI.class.getName());
    //defaults.put("ScrollPaneUI", BegScrollPaneUI.class.getName());

    defaults.put("TabbedPane.tabInsets", new Insets(0, 4, 0, 4));
    defaults.put("ToolTip.background", TOOLTIP_BACKGROUND_COLOR);
    defaults.put("ToolTip.border", new ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1));
    defaults.put("Tree.ancestorInputMap", null);
    defaults.put("FileView.directoryIcon", AllIcons.Nodes.Folder);
    defaults.put("FileChooser.upFolderIcon", AllIcons.Nodes.UpFolder);
    defaults.put("FileChooser.newFolderIcon", AllIcons.Nodes.NewFolder);
    defaults.put("FileChooser.homeFolderIcon", AllIcons.Nodes.HomeFolder);
    defaults.put("OptionPane.errorIcon", AllIcons.General.ErrorDialog);
    defaults.put("OptionPane.informationIcon", AllIcons.General.InformationDialog);
    defaults.put("OptionPane.warningIcon", AllIcons.General.WarningDialog);
    defaults.put("OptionPane.questionIcon", AllIcons.General.QuestionDialog);
    defaults.put("Tree.openIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeOpen.gif"));
    defaults.put("Tree.closedIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeClosed.gif"));
    defaults.put("Tree.leafIcon", LookAndFeel.makeIcon(WindowsLookAndFeel.class, "icons/TreeLeaf.gif"));
    defaults.put("Tree.expandedIcon", WindowsTreeUI.ExpandedIcon.createExpandedIcon());
    defaults.put("Tree.collapsedIcon", WindowsTreeUI.CollapsedIcon.createCollapsedIcon());
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
                        "RIGHT", "selectNextColumn",
                     "KP_RIGHT", "selectNextColumn",
                         "LEFT", "selectPreviousColumn",
                      "KP_LEFT", "selectPreviousColumn",
                         "DOWN", "selectNextRow",
                      "KP_DOWN", "selectNextRow",
                           "UP", "selectPreviousRow",
                        "KP_UP", "selectPreviousRow",
                  "shift RIGHT", "selectNextColumnExtendSelection",
               "shift KP_RIGHT", "selectNextColumnExtendSelection",
                   "shift LEFT", "selectPreviousColumnExtendSelection",
                "shift KP_LEFT", "selectPreviousColumnExtendSelection",
                   "shift DOWN", "selectNextRowExtendSelection",
                "shift KP_DOWN", "selectNextRowExtendSelection",
                     "shift UP", "selectPreviousRowExtendSelection",
                  "shift KP_UP", "selectPreviousRowExtendSelection",
                      "PAGE_UP", "scrollUpChangeSelection",
                    "PAGE_DOWN", "scrollDownChangeSelection",
                         "HOME", "selectFirstColumn",
                          "END", "selectLastColumn",
                "shift PAGE_UP", "scrollUpExtendSelection",
              "shift PAGE_DOWN", "scrollDownExtendSelection",
                   "shift HOME", "selectFirstColumnExtendSelection",
                    "shift END", "selectLastColumnExtendSelection",
                 "ctrl PAGE_UP", "scrollLeftChangeSelection",
               "ctrl PAGE_DOWN", "scrollRightChangeSelection",
                    "ctrl HOME", "selectFirstRow",
                     "ctrl END", "selectLastRow",
           "ctrl shift PAGE_UP", "scrollRightExtendSelection",
         "ctrl shift PAGE_DOWN", "scrollLeftExtendSelection",
              "ctrl shift HOME", "selectFirstRowExtendSelection",
               "ctrl shift END", "selectLastRowExtendSelection",
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
