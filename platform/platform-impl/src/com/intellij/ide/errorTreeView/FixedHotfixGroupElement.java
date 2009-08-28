package com.intellij.ide.errorTreeView;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FixedHotfixGroupElement extends GroupingElement {
  private final static Icon ourIcon = IconLoader.getIcon("/compiler/information.png");
  private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

  public FixedHotfixGroupElement(String name, Object data, VirtualFile file) {
    super(name, data, file);
    myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
      public void customizeCellRenderer(SimpleColoredComponent renderer,
                                        JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        renderer.setIcon(ourIcon);
        renderer.append("Fixed: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
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
