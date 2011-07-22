/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection.impl.ui;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class DetectedFrameworksTree extends CheckboxTree {
  public DetectedFrameworksTree(List<DetectedFrameworkDescription> detectedFrameworks, final FrameworkDetectionContext context) {
    super(new DetectedFrameworksTreeRenderer(), new CheckedTreeNode(null), new CheckPolicy(false, true, true, false));
    setShowsRootHandles(false);
    setRootVisible(false);
    createNodes(detectedFrameworks, context);
    TreeUtil.expandAll(this);
  }

  private void createNodes(List<DetectedFrameworkDescription> frameworks, FrameworkDetectionContext context) {
    CheckedTreeNode root = ((CheckedTreeNode)getModel().getRoot());
    Map<FrameworkType, FrameworkTypeNode> groupNodes = new HashMap<FrameworkType, FrameworkTypeNode>();
    for (DetectedFrameworkDescription framework : frameworks) {
      final FrameworkType type = framework.getFrameworkType();
      FrameworkTypeNode group = groupNodes.get(type);
      if (group == null) {
        group = new FrameworkTypeNode(type);
        groupNodes.put(type, group);
        root.add(group);
      }
      group.add(new DetectedFrameworkNode(framework, context));
    }
  }

  private static class DetectedFrameworksTreeRenderer extends CheckboxTreeCellRenderer {
    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (value instanceof DetectedFrameworkTreeNodeBase) {
        ((DetectedFrameworkTreeNodeBase)value).renderNode(getTextRenderer());
      }
    }
  }
}
