/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.StringBuilderSpinAllocator;

import javax.swing.*;

public class FramesListRenderer extends ColoredListCellRenderer {

  protected void customizeCellRenderer(final JList list, final Object item, final int index, final boolean selected, final boolean hasFocus) {
    final StackFrameDescriptorImpl descriptor = (StackFrameDescriptorImpl)item;
    setIcon(descriptor.getIcon());
    final String label = descriptor.getLabel();

    final int openingBrace = label.indexOf("{");
    final int closingBrace = (openingBrace < 0) ? -1 : label.indexOf("}");
    if (openingBrace < 0 || closingBrace < 0) {
      append(label, SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
    else {
      append(label.substring(0, openingBrace - 1), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);

      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(" (");
        builder.append(label.substring(openingBrace + 1, closingBrace));
        builder.append(")");
        append(builder.toString(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }

      append(label.substring(closingBrace + 1, label.length()), SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
    }
  }
}
