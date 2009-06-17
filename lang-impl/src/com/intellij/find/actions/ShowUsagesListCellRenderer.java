package com.intellij.find.actions;

import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;

import javax.swing.*;
import java.awt.*;

/**
* @author cdr
*/
class ShowUsagesListCellRenderer implements ListCellRenderer {
  private final UsageViewImpl myUsageView;
  private static final ColoredListCellRenderer USAGE_RENDERER = new ColoredListCellRenderer() {
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      UsageNode usageNode = (UsageNode)value;
      Usage usage = usageNode.getUsage();
      UsagePresentation presentation = usage.getPresentation();
      setIcon(presentation.getIcon());

      TextChunk[] text = presentation.getText();
      for (TextChunk textChunk : text) {
        append(textChunk.getText(), SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes()));
      }
    }
  };

  ShowUsagesListCellRenderer(UsageViewImpl usageView) {
    myUsageView = usageView;
  }

  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.setBackground(list.getBackground());
    if (!(value instanceof UsageNode)) {
      JLabel label = new JLabel("<html><body><b>"+value.toString(), SwingConstants.CENTER);
      //panel.add(new JLabel("<html><body><table><tr><td align=center><b>"+value.toString()+"</b></td></tr></table></body></html>"),
      //          new GridBagConstraints(0, 0, GridBagConstraints.REMAINDER, 0, 1, 0, GridBagConstraints.CENTER,
      //                                 GridBagConstraints.HORIZONTAL, new Insets(3, 3, 3, 3), 0, 1));
      return label;
    }
    UsageNode usageNode = (UsageNode)value;
    int seq = appendGroupText((GroupNode)usageNode.getParent(), panel,list, value, index, isSelected);

    USAGE_RENDERER.setIpad(new Insets(0,0,0,0));
    USAGE_RENDERER.setBorder(null);
    USAGE_RENDERER.getListCellRendererComponent(list, value, index, isSelected, false);
    panel.add(USAGE_RENDERER, new GridBagConstraints(seq, 0, GridBagConstraints.REMAINDER, 0, 1, 0,
                                                    GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0,0,0,0), 0, 1));
    return panel;
  }

  private int appendGroupText(final GroupNode node, JPanel panel, JList list, Object value, int index, boolean isSelected) {
    if (node != null && node.getGroup() != null) {
      int seq = appendGroupText((GroupNode)node.getParent(), panel, list, value, index, isSelected);
      if (node.canNavigateToSource()) {
        ColoredListCellRenderer renderer = new ColoredListCellRenderer() {
          protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
            UsageGroup group = node.getGroup();
            setIcon(group.getIcon(false));
            append(group.getText(myUsageView), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        };
        renderer.setIpad(new Insets(0,0,0,0));
        renderer.setBorder(null);
        renderer.getListCellRendererComponent(list, value, index, isSelected, false);
        panel.add(renderer, new GridBagConstraints(seq, 0, 1, 0, 0, 0,
                                                   GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0,0,0,0), 0, 1));
        return seq+1;
      }
    }
    return 0;
  }
}
