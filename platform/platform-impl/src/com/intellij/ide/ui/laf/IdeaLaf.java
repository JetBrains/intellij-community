// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredSideBorder;
import com.intellij.ui.TableActions;
import com.intellij.ui.plaf.beg.*;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.StartupUiUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.util.Map;
import java.util.function.Supplier;

/**
* @author Konstantin Bulenkov
*/
public final class IdeaLaf extends MetalLookAndFeel {
  public static final ColorUIResource TOOLTIP_BACKGROUND_COLOR = new ColorUIResource(255, 255, 231);

  private final Map<Object, Object> myCustomFontDefaults;

  public IdeaLaf(@Nullable Map<Object, Object> customFontDefaults) {
    myCustomFontDefaults = customFontDefaults;
  }

  @Override
  public void initComponentDefaults(UIDefaults defaults) {
    super.initComponentDefaults(defaults);
    StartupUiUtil.initInputMapDefaults(defaults);
    initIdeaDefaults(defaults);

    if (myCustomFontDefaults != null) {
      defaults.putAll(myCustomFontDefaults);
    }
    else {
      Pair<String, Integer> systemFont = JBUIScale.getSystemFontData(() -> defaults);
      StartupUiUtil.initFontDefaults(defaults, StartupUiUtil.getFontWithFallback(systemFont.getFirst(), Font.PLAIN, systemFont.getSecond()));
    }
  }

  @SuppressWarnings("HardCodedStringLiteral")
  static void initIdeaDefaults(UIDefaults defaults) {
    defaults.put("Menu.maxGutterIconWidth", 18);
    defaults.put("MenuItem.maxGutterIconWidth", 18);
    // TODO[vova,anton] REMOVE!!! INVESTIGATE??? Borland???
    defaults.put("MenuItem.acceleratorDelimiter", "-");

    defaults.put("TitledBorder.titleColor", new ColorUIResource(10, 36, 106));
    ColorUIResource col = new ColorUIResource(230, 230, 230);
    defaults.put("ScrollBar.background", col);
    defaults.put("ScrollBar.track", col);

    defaults.put("TextField.border", createLazyValue(() -> BegBorders.getTextFieldBorder()));
    defaults.put("PasswordField.border", createLazyValue(() -> BegBorders.getTextFieldBorder()));
    defaults.put("PopupMenu.border", createLazyValue(() -> new BegPopupMenuBorder()));
    defaults.put("ScrollPane.border", createLazyValue(() -> BegBorders.getScrollPaneBorder()));

    defaults.put("ToggleButtonUI", createLazyValue(() -> BegToggleButtonUI.class.getName()));
    defaults.put("RadioButtonUI", createLazyValue(() -> BegRadioButtonUI.class.getName()));
    defaults.put("TabbedPaneUI", createLazyValue(() -> BegTabbedPaneUI.class.getName()));
    defaults.put("TableUI", createLazyValue(() -> BegTableUI.class.getName()));
    defaults.put("TreeUI", createLazyValue(() -> BegTreeUI.class.getName()));

    defaults.put("TabbedPane.tabInsets", new Insets(0, 4, 0, 4));
    defaults.put("ToolTip.background", TOOLTIP_BACKGROUND_COLOR);
    defaults.put("ToolTip.border", new ColoredSideBorder(Color.gray, Color.gray, Color.black, Color.black, 1));
    defaults.put("Tree.ancestorInputMap", null);
    defaults.put("FileView.directoryIcon", createLazyValue(() -> AllIcons.Nodes.Folder));
    defaults.put("FileChooser.upFolderIcon", createLazyValue(() -> AllIcons.Nodes.UpFolder));
    defaults.put("FileChooser.newFolderIcon", createLazyValue(() -> AllIcons.Nodes.Folder));
    defaults.put("FileChooser.homeFolderIcon", createLazyValue(() -> AllIcons.Nodes.HomeFolder));
    defaults.put("OptionPane.errorIcon", createLazyValue(() -> AllIcons.General.ErrorDialog));
    defaults.put("OptionPane.informationIcon", createLazyValue(() -> AllIcons.General.InformationDialog));
    defaults.put("OptionPane.warningIcon", createLazyValue(() -> AllIcons.General.WarningDialog));
    defaults.put("OptionPane.questionIcon", createLazyValue(() -> AllIcons.General.QuestionDialog));
    defaults.put("Table.ancestorInputMap", new UIDefaults.LazyValue() {
      @Override
      public Object createValue(UIDefaults table) {
        return LookAndFeel.makeInputMap(new Object[]{
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
        });
      }
    });
  }

  static void fillFallbackDefaults(UIDefaults defaults) {
    // These icons are only needed to prevent Swing from trying to fetch defaults with AWT ImageFetcher threads (IDEA-322089),
    // but might as well just put something sensibly-looking there, just in case they show up due to some bug:
    UIDefaults.LazyValue folderIcon = createLazyValue(() -> AllIcons.Nodes.Folder);
    defaults.put("Tree.openIcon", folderIcon);
    defaults.put("Tree.closedIcon", folderIcon);
    defaults.put("Tree.leafIcon", createLazyValue(() -> AllIcons.FileTypes.Any_type));
    // These two are actually set by our themes, so we don't want to override them here:
    //defaults.put("Tree.expandedIcon", AllIcons.Toolbar.Expand);
    //defaults.put("Tree.collapsedIcon", AllIcons.Actions.ArrowExpand);
  }

  private static @NotNull UIDefaults.LazyValue createLazyValue(@NotNull Supplier<Object> supplier) {
    return new UIDefaults.LazyValue() {
      @Override
      public Object createValue(UIDefaults table) {
        return supplier.get();
      }
    };
  }
}
