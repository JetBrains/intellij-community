// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usages.TextChunk;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Pavel.Dolgov
 */
class PreviewTreeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                    boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof MethodNode) {
      MethodNode methodNode = (MethodNode)value;
      setIcon(methodNode.getIcon());
      appendText(methodNode);
    }
    else if (value instanceof FragmentNode) {
      FragmentNode node = (FragmentNode)value;
      appendText(node);
    }
    if (value instanceof DefaultMutableTreeNode) {
      Object object = ((DefaultMutableTreeNode)value).getUserObject();
      if (object instanceof String) {
        append((String)object, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    }
  }

  private void appendText(@NotNull FragmentNode node) {
    TextChunk[] text = node.getTextChunks();
    for (int i = 0; i < text.length; i++) {
      TextChunk textChunk = text[i];
      SimpleTextAttributes attributes = patchMainTextAttrs(textChunk.getSimpleAttributesIgnoreBackground(), node);
      append(textChunk.getText() + (i == 0 ? " " : ""), attributes, true);
    }
  }

  private static SimpleTextAttributes patchMainTextAttrs(@NotNull SimpleTextAttributes attributes, @NotNull FragmentNode node) {
    if (node.isExcluded()) {
      return attributes.derive(attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
    }
    if (!node.isValid()) {
      return attributes.derive(-1, FileStatus.IGNORED.getColor(), null, null);
    }
    return attributes;
  }
}
