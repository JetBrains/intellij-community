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
package com.intellij.ide.util.treeView;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

public class NodeRenderer extends ColoredTreeCellRenderer {

  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    Color color = null;
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        color = descriptor.getColor();
        if (expanded) {
          setIcon(descriptor.getOpenIcon());
        }
        else {
          setIcon(descriptor.getClosedIcon());
        }
      }
    }
    final Object valueToGetText = value instanceof AbstractTreeNode ? value.toString() : value;
    String text = tree.convertValueToText(valueToGetText,selected, expanded, leaf, row, hasFocus);

    if (text == null) text = "";

    SimpleTextAttributes simpleTextAttributes = getSimpleTextAttributes(value, color);

    append(text, simpleTextAttributes);

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();

      if (userObject instanceof AbstractTreeNode) {
        AbstractTreeNode treeNode = (AbstractTreeNode)userObject;
        String locationString = treeNode.getPresentation().getLocationString();
        if (locationString != null && locationString.length() > 0) {
          append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        setToolTipText(treeNode.getToolTip());
      }
    }
  }

  protected static SimpleTextAttributes getSimpleTextAttributes(final Object value, final Color color) {
    SimpleTextAttributes simpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (value instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof AbstractTreeNode) {
        simpleTextAttributes = getSimpleTextAttributes(((AbstractTreeNode)userObject).getPresentation());
      }
    }
    if (color != null) {
      final TextAttributes textAttributes = simpleTextAttributes.toTextAttributes();
      textAttributes.setForegroundColor(color);
      simpleTextAttributes = SimpleTextAttributes.fromTextAttributes(textAttributes);
    }
    return simpleTextAttributes;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(final ItemPresentation presentation) {
    final TextAttributesKey textAttributesKey = presentation.getTextAttributesKey();
    if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(textAttributesKey);
    return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
  }

}