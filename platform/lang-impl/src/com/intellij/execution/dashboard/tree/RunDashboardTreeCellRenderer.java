// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.RunDashboardGroup;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkComponent;
import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkIconComponent;
import com.intellij.execution.dashboard.hyperlink.RunDashboardHyperlinkTextComponent;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Aleev
 */
public class RunDashboardTreeCellRenderer extends JPanel implements TreeCellRenderer {
  public static final Key<Pair<RunDashboardHyperlinkTextComponent, RunDashboardHyperlinkIconComponent>> NODE_LINKS =
    new Key<>("RunDashboardNodeLink");

  private final ColoredTreeCellRenderer myNodeRender = new NodeRenderer() {
    @Nullable
    @Override
    protected ItemPresentation getPresentation(Object node) {
      if (node instanceof RunDashboardGroup) {
        RunDashboardGroup group = (RunDashboardGroup)node;
        return new PresentationData(group.getName(), null, group.getIcon(), null);
      }
      return super.getPresentation(node);
    }
  };
  private final JLabel myLabel = new JLabel();

  public RunDashboardTreeCellRenderer() {
    super(new BorderLayout());
    add(myLabel, BorderLayout.EAST);
  }

  @Override
  public Component getTreeCellRendererComponent(JTree tree,
                                                Object value,
                                                boolean selected,
                                                boolean expanded,
                                                boolean leaf,
                                                int row,
                                                boolean hasFocus) {
    myNodeRender.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
    RunDashboardRunConfigurationNode node = TreeUtil.getUserObject(RunDashboardRunConfigurationNode.class, value);
    if (node != null && customizeCellRenderer(tree, node, selected)) {
      this.add(myNodeRender, BorderLayout.CENTER);
      return this;
    }
    return myNodeRender;
  }

  private boolean customizeCellRenderer(JTree tree, RunDashboardRunConfigurationNode node, boolean selected) {
    Pair<RunDashboardHyperlinkTextComponent, RunDashboardHyperlinkIconComponent> links = node.getUserData(NODE_LINKS);
    if (links == null || links.first.getText().isEmpty()) return false;

    links.first.setSelected(selected);
    links.first.render(myNodeRender);

    if (links.second == null) return false;

    myLabel.setText(null);
    if (selected || node.equals(UIUtil.getClientProperty(tree, RunDashboardHyperlinkComponent.AIMED_OBJECT))) {
      myLabel.setIcon(links.second);
    }
    else {
      // Use empty icon to avoid node's preferred size changes depending on a node state.
      myLabel.setIcon(EmptyIcon.ICON_16);
    }
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 2));

    return true;
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    return myNodeRender.getToolTipText(event);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new MyAccessibleContext();
    }
    return accessibleContext;
  }

  private class MyAccessibleContext extends JPanel.AccessibleJPanel {
    @Override
    public String getAccessibleName() {
      return myNodeRender.getAccessibleContext().getAccessibleName();
    }

    @Override
    public String getAccessibleDescription() {
      return myNodeRender.getAccessibleContext().getAccessibleDescription();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return myNodeRender.getAccessibleContext().getAccessibleRole();
    }
  }
}
