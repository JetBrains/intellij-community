// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usages.TextChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

class PreviewTreeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                    boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (value instanceof MethodNode methodNode) {
      setIcon(methodNode.getIcon());
      appendText(methodNode);
    }
    else if (value instanceof FragmentNode node) {
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
    if (node.isValid()) {
      append(node.getLineNumberChunk(), node);
    }
    else {
      append(JavaRefactoringBundle.message("extract.method.preview.node.invalid.prefix"), patchMainTextAttrs(UsageTreeColors.INVALID_ATTRIBUTES, node));
    }

    for (TextChunk textChunk : node.getTextChunks()) {
      append(textChunk, node);
    }
  }

  private void append(@Nullable TextChunk textChunk, @NotNull FragmentNode node) {
    if (textChunk != null) {
      SimpleTextAttributes attributes = textChunk.getSimpleAttributesIgnoreBackground();
      append(textChunk.getText(), patchMainTextAttrs(attributes, node), true);
    }
  }

  private static SimpleTextAttributes patchMainTextAttrs(@NotNull SimpleTextAttributes attributes, @NotNull FragmentNode node) {
    if (node.isExcluded()) {
      return attributes.derive(attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
    }
    return attributes;
  }
}
