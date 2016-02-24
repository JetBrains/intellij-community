/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.awt.*;

/**
 * @author Dmitry Batkovich
 */
public class InspectionViewNavigationPanel extends JPanel {
  public InspectionViewNavigationPanel(InspectionTreeNode node, InspectionTree tree) {
    setLayout(new BorderLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(5, 7, 0, 0));
    final String titleLabelText = getTitleText(node instanceof InspectionRootNode, true);
    add(new JBLabel(titleLabelText), BorderLayout.NORTH);
    final JPanel links = new JPanel();
    links.setLayout(new BoxLayout(links, BoxLayout.Y_AXIS));
    links.add(Box.createVerticalStrut(JBUI.scale(10)));
    add(BorderLayout.CENTER, links);
    for (int i = 0; i < node.getChildCount(); i++) {
      final TreeNode child = node.getChildAt(i);
      final LinkLabel link = new LinkLabel(child.toString(), null) {
        @Override
        public void doClick() {
          TreeUtil.selectInTree((DefaultMutableTreeNode)child, true, tree);
        }
      };
      link.setBorder(IdeBorderFactory.createEmptyBorder(1, 17, 3, 1));
      links.add(link);
    }
  }

  @NotNull
  public static String getTitleText(boolean addGroupWord, boolean addColon) {
    return "Select inspection " + (addGroupWord ? "group" : "") + " to see problems" + (addColon ? ":" : ".");
  }
}
