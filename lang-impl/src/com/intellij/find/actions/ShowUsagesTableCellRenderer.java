package com.intellij.find.actions;

import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsagePresentation;
import com.intellij.usages.impl.GroupNode;
import com.intellij.usages.impl.NullUsage;
import com.intellij.usages.impl.UsageNode;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
* @author cdr
*/
class ShowUsagesTableCellRenderer implements TableCellRenderer {
  private final UsageViewImpl myUsageView;

  ShowUsagesTableCellRenderer(UsageViewImpl usageView) {
    myUsageView = usageView;
  }

  public Component getTableCellRendererComponent(JTable list, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    if (!(value instanceof UsageNode)) {
      JLabel label = new JLabel("<html><body><b>" + value + "</b></body></html>", SwingConstants.CENTER);
      //label.setBorder(new LineBorder(Color.red));
      return label;
    }
    UsageNode usageNode = (UsageNode)value;

    JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0,0));
    panel.setBackground(isSelected ? UIUtil.getListSelectionBackground() : list.getBackground());
    panel.setForeground(isSelected ? UIUtil.getListSelectionForeground() : list.getForeground());
    GroupNode parent = (GroupNode)usageNode.getParent();
    SimpleColoredComponent textChunks = new SimpleColoredComponent();
    textChunks.setIpad(new Insets(0,0,0,0));
    textChunks.setBorder(null);
    Usage usage = usageNode.getUsage();
    if (column == 0) {
      appendGroupText(parent, panel);
      if (usage == NullUsage.INSTANCE) {
          textChunks.append("...<");
          textChunks.append("more usages", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          textChunks.append(">...");
      }
    }
    else if (usage != NullUsage.INSTANCE) {
      UsagePresentation presentation = usage.getPresentation();
      TextChunk[] text = presentation.getText();
      if (column == 1) {
        textChunks.setIcon(presentation.getIcon());
        if (text.length != 0) {
          SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(text[0].getAttributes());
          if (isSelected) attributes = attributes.derive(-1,null,UIUtil.getListSelectionBackground(),null);
          textChunks.append(text[0].getText(), attributes);
        }
      }
      else if (column == 2) {
        for (int i = 1; i < text.length; i++) {
          TextChunk textChunk = text[i];
          SimpleTextAttributes attributes = SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes());
          if (isSelected) attributes = attributes.derive(-1,null,UIUtil.getListSelectionBackground(),null);
          textChunks.append(textChunk.getText(), attributes);
        }
      }
      else {
        assert false : column;
      }
    }
    panel.add(textChunks);
    return panel;
  }

  private void appendGroupText(final GroupNode node, JPanel panel) {
    UsageGroup group = node == null ? null : node.getGroup();
    if (group == null) return;
    GroupNode parentGroup = (GroupNode)node.getParent();
    appendGroupText(parentGroup, panel);
    if (node.canNavigateToSource()) {
      SimpleColoredComponent renderer = new SimpleColoredComponent();

      renderer.setIcon(group.getIcon(false));
      renderer.append(group.getText(myUsageView), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
      renderer.setIpad(new Insets(0,0,0,0));
      renderer.setBorder(null);
      panel.add(renderer);
    }
  }
}