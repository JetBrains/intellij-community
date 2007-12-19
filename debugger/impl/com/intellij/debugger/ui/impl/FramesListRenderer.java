/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl;

import com.intellij.xdebugger.ui.DebuggerColors;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueMarkup;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Method;

import javax.swing.*;

class FramesListRenderer extends ColoredListCellRenderer {
  private EditorColorsScheme myColorScheme;

  public FramesListRenderer() {
    myColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
  }

  protected void customizeCellRenderer(final JList list, final Object item, final int index, final boolean selected, final boolean hasFocus) {
    if (!(item instanceof StackFrameDescriptorImpl)) {
      append(item.toString(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
    }
    else {
      final StackFrameDescriptorImpl descriptor = (StackFrameDescriptorImpl)item;
      setIcon(descriptor.getIcon());
      final Object selectedValue = list.getSelectedValue();
      final boolean shouldHighlightAsRecursive = (selectedValue instanceof StackFrameDescriptorImpl) && 
                                                 isOccurrenceOfSelectedFrame((StackFrameDescriptorImpl)selectedValue, descriptor);

      final ValueMarkup markup = descriptor.getValueMarkup();
      if (markup != null) {
        append(markup.getText(), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }

      if (selected) {
        setBackground(com.intellij.util.ui.UIUtil.getListSelectionBackground());
      }
      else {
        setBackground(shouldHighlightAsRecursive ? myColorScheme.getColor(DebuggerColors.RECURSIVE_CALL_ATTRIBUTES) : com.intellij.util.ui.UIUtil.getListBackground());
      }

      final String label = descriptor.getLabel();
      final int openingBrace = label.indexOf("{");
      final int closingBrace = (openingBrace < 0) ? -1 : label.indexOf("}");
      final SimpleTextAttributes attributes = getAttributes(descriptor);
      if (openingBrace < 0 || closingBrace < 0) {
        append(label, attributes);
      }
      else {
        append(label.substring(0, openingBrace - 1), attributes);

        final StringBuilder builder = StringBuilderSpinAllocator.alloc();
        try {
          builder.append(" (");
          builder.append(label.substring(openingBrace + 1, closingBrace));
          builder.append(")");
          append(builder.toString(), SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);
        }
        finally {
          StringBuilderSpinAllocator.dispose(builder);
        }

        append(label.substring(closingBrace + 1, label.length()), attributes);
        if (shouldHighlightAsRecursive && descriptor.isRecursiveCall()) {
          final StringBuilder _builder = StringBuilderSpinAllocator.alloc();
          try {
            _builder.append(" [").append(descriptor.getOccurrenceIndex()).append("]");
            append(_builder.toString(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          finally {
            StringBuilderSpinAllocator.dispose(_builder);
          }
        }
      }
    }
  }

  private static boolean isOccurrenceOfSelectedFrame(final StackFrameDescriptorImpl selectedDescriptor, StackFrameDescriptorImpl descriptor) {
    final Method currentMethod = descriptor.getMethod();
    if (currentMethod != null) {
      if (selectedDescriptor != null) {
        final Method selectedMethod = selectedDescriptor.getMethod();
        if (selectedMethod != null) {
          if (Comparing.equal(selectedMethod, currentMethod)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static SimpleTextAttributes getAttributes(final StackFrameDescriptorImpl descriptor) {
    if (descriptor.isSynthetic() || descriptor.isInLibraryContent()) {
      return SimpleTextAttributes.GRAYED_ATTRIBUTES;
    }
    return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }
}
