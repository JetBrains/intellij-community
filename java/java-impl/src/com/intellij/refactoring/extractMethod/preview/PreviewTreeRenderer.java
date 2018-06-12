// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.DarculaColors;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageTreeColors;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usages.TextChunk;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Pavel.Dolgov
 */
class PreviewTreeRenderer extends ColoredTreeCellRenderer {
  private static final EditorColorsScheme ourColorsScheme = UsageTreeColorsScheme.getInstance().getScheme();
  private static final SimpleTextAttributes ourInvalidAttributes = SimpleTextAttributes.fromTextAttributes(ourColorsScheme.getAttributes(UsageTreeColors.INVALID_PREFIX));
  private static final SimpleTextAttributes ourInvalidAttributesDarcula = new SimpleTextAttributes(null, DarculaColors.RED, null, ourInvalidAttributes.getStyle());

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
    if (node.isValid()) {
      append(node.getLineNumberChunk(), node);
    }
    else {
      SimpleTextAttributes attributes = UIUtil.isUnderDarcula() ? ourInvalidAttributesDarcula : ourInvalidAttributes;
      append("Invalid ", patchMainTextAttrs(attributes, node));
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
