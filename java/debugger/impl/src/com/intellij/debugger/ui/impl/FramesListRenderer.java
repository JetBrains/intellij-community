/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.ui.DebuggerColors;
import com.sun.jdi.Method;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;

class FramesListRenderer extends ColoredListCellRenderer {
  private final EditorColorsScheme myColorScheme;

  public FramesListRenderer() {
    myColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
  }

  @Override
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
        append("["+ markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }

      boolean needSeparator = false;
      if (index > 0) {
        final int currentFrameIndex = descriptor.getUiIndex();
        final Object elementAt = list.getModel().getElementAt(index - 1);
        if (elementAt instanceof StackFrameDescriptorImpl) {
          StackFrameDescriptorImpl previousDescriptor = (StackFrameDescriptorImpl)elementAt;
          final int previousFrameIndex = previousDescriptor.getUiIndex();
          needSeparator = (currentFrameIndex - previousFrameIndex != 1);
        }
      }

      if (selected) {
        setBackground(UIUtil.getListSelectionBackground());
      }
      else {
        Color bg = descriptor.getBackgroundColor();
        if (bg == null) bg = UIUtil.getListBackground();
        if (shouldHighlightAsRecursive) bg = myColorScheme.getColor(DebuggerColors.RECURSIVE_CALL_ATTRIBUTES);
        setBackground(bg);
      }

      if (needSeparator) {
        final MatteBorder border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.GRAY);
        setBorder(border);
      }
      else {
        setBorder(null);
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
        append(" (" + label.substring(openingBrace + 1, closingBrace) + ")", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);

        append(label.substring(closingBrace + 1, label.length()), attributes);
        if (shouldHighlightAsRecursive && descriptor.isRecursiveCall()) {
          append(" [" + descriptor.getOccurrenceIndex() + "]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
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
