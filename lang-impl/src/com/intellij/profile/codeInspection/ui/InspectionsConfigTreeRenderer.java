/*
 * User: anna
 * Date: 14-May-2009
 */
package com.intellij.profile.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.Descriptor;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

abstract class InspectionsConfigTreeRenderer extends CheckboxTree.CheckboxTreeCellRenderer {
  protected abstract String getFilter();

  public void customizeCellRenderer(final JTree tree,
                                    final Object value,
                                    final boolean selected,
                                    final boolean expanded,
                                    final boolean leaf,
                                    final int row,
                                    final boolean hasFocus) {

    InspectionConfigTreeNode node = (InspectionConfigTreeNode)value;

    Object object = node.getUserObject();

    final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
    setBackground(background);
    Color foreground =
      selected ? UIUtil.getTreeSelectionForeground() : node.isProperSetting ? Color.BLUE : UIUtil.getTreeTextForeground();

    @NonNls String text = null;
    int style = Font.PLAIN;
    String hint = null;
    if (object instanceof String) {
      text = (String)object;
      style = Font.BOLD;
    }
    else {
      final Descriptor descriptor = node.getDesriptor();
      final String scopeName = node.getScopeName();
      if (scopeName != null) {
        if (node.isByDefault()) {
          text = "Everywhere except";
        }
        else {
          text = "In scope \'" + scopeName + "\'";
          if (node.getScope() == null) {
            foreground = Color.RED;
          }
        }
      } else {
        text = descriptor.getText();
      }
      hint = getHint(descriptor);
    }

    if (text != null) {
      SearchUtil.appendFragments(getFilter(), text, style, foreground, background,
                                 getTextRenderer());
    }
    if (hint != null) {
      getTextRenderer()
        .append(" " + hint, selected ? new SimpleTextAttributes(Font.PLAIN, foreground) : SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    setForeground(foreground);
  }

  @Nullable
private static String getHint(Descriptor descriptor) {
  if (descriptor.getTool() == null) {
    return InspectionsBundle.message("inspection.tool.availability.in.tree.node");
  }
  if (descriptor.getTool()instanceof LocalInspectionToolWrapper) {
    return null;
  }
  return InspectionsBundle.message("inspection.tool.availability.in.tree.node1");
}

}