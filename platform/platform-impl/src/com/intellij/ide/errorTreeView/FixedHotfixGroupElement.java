// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.errorTreeView;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FixedHotfixGroupElement extends GroupingElement {
  private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

  public FixedHotfixGroupElement(String name, Object data, VirtualFile file) {
    super(name, data, file);
    myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(SimpleColoredComponent renderer,
                                        JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        renderer.setIcon(AllIcons.General.Information);
        renderer.append(IdeBundle.message("fixed.problem.prefix"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        renderer.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        final String[] text = getText();
        final String checkedText = ((text != null) && (text.length > 0)) ? text[0] : "";
        renderer.append(checkedText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    };
  }

  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return myCustomizeColoredTreeCellRenderer;
  }
}
