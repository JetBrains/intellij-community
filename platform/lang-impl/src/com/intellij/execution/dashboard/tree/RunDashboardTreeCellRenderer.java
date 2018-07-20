// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardContributor;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ui.tree.TreeUtil;

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
  private final ColoredTreeCellRenderer myNodeRender = new NodeRenderer();
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
    if (node != null) {
      RunDashboardContributor contributor = node.getContributor();
      if (contributor != null) {
        if (contributor.customizeCellRenderer(myNodeRender, myLabel, node, selected, expanded, leaf, row, hasFocus)) {
          this.add(myNodeRender, BorderLayout.CENTER);
          return this;
        }
      }
    }
    return myNodeRender;
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
