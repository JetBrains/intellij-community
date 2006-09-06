/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.*;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author max
 */
class UsageViewTreeCellRenderer extends ColoredTreeCellRenderer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.usages.impl.UsageViewTreeCellRenderer");
  private static EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  private static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));
  private static final SimpleTextAttributes ourReadOnlyAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.READONLY_PREFIX));

  private SimpleTextAttributes myNumberOfUsagesAttribute;
  private UsageViewPresentation myPresentation;
  private UsageView myView;

  public UsageViewTreeCellRenderer(UsageView view) {
    myView = view;
    myPresentation = view.getPresentation();
    EditorColorsScheme colorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
    myNumberOfUsagesAttribute = SimpleTextAttributes.fromTextAttributes(colorsScheme.getAttributes(UsageTreeColors.NUMBER_OF_USAGES));
  }

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    boolean showAsReadOnly = false;
    if (value instanceof Node && value != tree.getModel().getRoot()) {
      Node node = (Node)value;
      if (!node.isValid()) {
        append(UsageViewBundle.message("node.invalid") + " ", ourInvalidAttributes);
      }
      if (myPresentation.isShowReadOnlyStatusAsRed() && node.isReadOnly()) {
        showAsReadOnly = true;
      }
    }

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      Object userObject = treeNode.getUserObject();

      if (userObject instanceof UsageTarget) {
        UsageTarget usageTarget = (UsageTarget)userObject;
        final ItemPresentation presentation = usageTarget.getPresentation();
        LOG.assertTrue(presentation != null);
        if (showAsReadOnly) {
          append(UsageViewBundle.message("node.readonly") + " ", ourReadOnlyAttributes);
        }
        final String text = presentation.getPresentableText();
        append(text != null? text : "", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        setIcon(presentation.getIcon(expanded));
      }
      else if (treeNode instanceof GroupNode) {
        GroupNode node = (GroupNode)treeNode;

        if (node.isRoot()) {
          append(StringUtil.capitalize(myPresentation.getUsagesWord()), patchAttrs(node, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES));
        }
        else {
          append(node.getGroup().getText(myView),
                 patchAttrs(node, showAsReadOnly ? ourReadOnlyAttributes : SimpleTextAttributes.REGULAR_ATTRIBUTES));
          setIcon(node.getGroup().getIcon(expanded));
        }

        int count = node.getRecursiveUsageCount();
        append(" (" + StringUtil.pluralize(count + " " + myPresentation.getUsagesWord(), count) + ")", patchAttrs(node, myNumberOfUsagesAttribute));
      }
      else if (treeNode instanceof UsageNode) {
        UsageNode node = (UsageNode)treeNode;
        setIcon(node.getUsage().getPresentation().getIcon());
        if (showAsReadOnly) {
          append(UsageViewBundle.message("node.readonly") + " ", patchAttrs(node, ourReadOnlyAttributes));
        }

        TextChunk[] text = node.getUsage().getPresentation().getText();
        for (TextChunk textChunk : text) {
          append(textChunk.getText(), patchAttrs(node, SimpleTextAttributes.fromTextAttributes(textChunk.getAttributes())));
        }
      }
      else if (userObject instanceof String) {
        append((String)userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      else {
        append(userObject == null ? "" : userObject.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
    else {
      append(value.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }

  private static SimpleTextAttributes patchAttrs(Node node, SimpleTextAttributes original) {
    if (node.isExcluded()) {
      original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, original.getFgColor(), original.getWaveColor());
    }
    if (node instanceof GroupNode) {
      UsageGroup group = ((GroupNode)node).getGroup();
      FileStatus fileStatus = group != null ? group.getFileStatus() : null;
      if (fileStatus != null && fileStatus != FileStatus.NOT_CHANGED) {
        original = new SimpleTextAttributes(original.getStyle(), fileStatus.getColor(), original.getWaveColor());
      }

      DefaultMutableTreeNode parent = (DefaultMutableTreeNode)node.getParent();
      if (parent != null && parent.isRoot()) {
        original = new SimpleTextAttributes(original.getStyle() | SimpleTextAttributes.STYLE_BOLD, original.getFgColor(), original.getWaveColor());
      }
    }
    return original;
  }

  public String getTooltipText(final Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
      if (treeNode instanceof UsageNode) {
        UsageNode node = (UsageNode)treeNode;
        return node.getUsage().getPresentation().getTooltipText();
      }
    }
    return null;
  }
}
