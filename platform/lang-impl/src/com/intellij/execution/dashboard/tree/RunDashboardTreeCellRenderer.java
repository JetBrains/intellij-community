/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
    Object userObject = TreeUtil.getUserObject(value);
    if (userObject instanceof RunDashboardRunConfigurationNode) {
      RunDashboardRunConfigurationNode node = (RunDashboardRunConfigurationNode)userObject;
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
