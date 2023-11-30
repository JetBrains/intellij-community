// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.LoadingNode;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
final class InspectionTreeCellRenderer extends ColoredTreeCellRenderer {
  private final InspectionTreeTailRenderer<RuntimeException> myTailRenderer;

  InspectionTreeCellRenderer(InspectionResultsView view) {
    myTailRenderer = new InspectionTreeTailRenderer<>(view.getGlobalInspectionContext()) {
      @Override
      protected void appendText(String text, SimpleTextAttributes attributes) {
        append(text, attributes);
      }

      @Override
      protected void appendText(String text) {
        append(text);
      }
    };
  }

  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    if (value instanceof LoadingNode) {
      append(LoadingNode.getText());
      return;
    }
    InspectionTreeNode node = (InspectionTreeNode)value;

    append(node.getPresentableText(),
           patchMainTextAttrs(node, node.appearsBold()
                                    ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                                    : getMainForegroundAttributes(node)));
    myTailRenderer.appendTailText(node);
    setIcon(node.getIcon(expanded));
  }

  private static SimpleTextAttributes patchMainTextAttrs(InspectionTreeNode node, SimpleTextAttributes attributes) {
    if (node.isExcluded()) {
      return attributes.derive(attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT, null, null, null);
    }
    if (node instanceof SuppressableInspectionTreeNode && ((SuppressableInspectionTreeNode)node).isQuickFixAppliedFromView()) {
      return attributes.derive(-1, SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor(), null, null);
    }
    if (!node.isValid()) {
      return attributes.derive(-1, FileStatus.IGNORED.getColor(), null, null);
    }
    return attributes;
  }


  private static SimpleTextAttributes getMainForegroundAttributes(InspectionTreeNode node) {
    SimpleTextAttributes foreground = SimpleTextAttributes.REGULAR_ATTRIBUTES;
    if (node instanceof RefElementNode) {
      RefEntity refElement = ((RefElementNode)node).getElement();

      if (refElement instanceof RefElement) {
        refElement = ((RefElement)refElement).getContainingEntry();
        if (((RefElement)refElement).isEntry() && ((RefElement)refElement).isPermanentEntry()) {
          foreground = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.blue);
        }
      }
    }
    return foreground;
  }
}
