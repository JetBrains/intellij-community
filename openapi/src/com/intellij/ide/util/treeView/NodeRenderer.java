/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.ide.util.treeView;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.navigation.ItemPresentation;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class NodeRenderer extends ColoredTreeCellRenderer {
  public void customizeCellRenderer(JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();
      if (userObject instanceof NodeDescriptor) {
        NodeDescriptor descriptor = (NodeDescriptor)userObject;
        if (expanded) {
          setIcon(descriptor.getOpenIcon());
        }
        else {
          setIcon(descriptor.getClosedIcon());
        }
      }
    }
    String text =
      tree.convertValueToText(value instanceof AbstractTreeNode
        ? ((AbstractTreeNode)value).toString()
                                                                                                                            : value,
        selected, expanded, leaf, row, hasFocus);
    if (text == null) text = "";
    append(text, getSimpleTextAttributes(value));

    if (value instanceof DefaultMutableTreeNode) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object userObject = node.getUserObject();

      if (userObject instanceof AbstractTreeNode) {
        AbstractTreeNode treeNode = (AbstractTreeNode)userObject;
        String locationString = treeNode.getPresentation().getLocationString();
        if (locationString != null && locationString.length() > 0) {
          append(" (" + locationString + ")", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
        final String toolTip = treeNode.getToolTip();
        setToolTipText(toolTip);
      }
    }
  }

  public static SimpleTextAttributes getSimpleTextAttributes(final Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      final Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof AbstractTreeNode) {
        return getSimpleTextAttributes(((AbstractTreeNode)userObject).getPresentation());
      }
    }
    return SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  public static SimpleTextAttributes getSimpleTextAttributes(final ItemPresentation presentation) {
    final TextAttributesKey textAttributesKey = presentation.getTextAttributesKey();
    if (textAttributesKey == null) return SimpleTextAttributes.REGULAR_ATTRIBUTES;
    final TextAttributes textAttributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(textAttributesKey);
    return textAttributes == null ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.fromTextAttributes(textAttributes);
  }

}