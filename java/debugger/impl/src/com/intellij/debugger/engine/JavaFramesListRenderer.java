/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.Method;
import org.jetbrains.annotations.NotNull;

// Copied from FramesListRenderer
class JavaFramesListRenderer /*extends ColoredListCellRenderer*/ {
  private final EditorColorsScheme myColorScheme;

  public JavaFramesListRenderer() {
    myColorScheme = EditorColorsManager.getInstance().getGlobalScheme();
  }

  public void customizePresentation(StackFrameDescriptorImpl descriptor, @NotNull ColoredTextContainer component, StackFrameDescriptorImpl selectedDescriptor) {
    component.setIcon(descriptor.getIcon());
      //final Object selectedValue = list.getSelectedValue();

      final boolean shouldHighlightAsRecursive = isOccurrenceOfSelectedFrame(selectedDescriptor, descriptor);

      final ValueMarkup markup = descriptor.getValueMarkup();
      if (markup != null) {
        component.append("["+ markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
      }

      boolean needSeparator = false;
      //if (index > 0) {
      //  final int currentFrameIndex = descriptor.getUiIndex();
      //  final Object elementAt = list.getModel().getElementAt(index - 1);
      //  if (elementAt instanceof StackFrameDescriptorImpl) {
      //    StackFrameDescriptorImpl previousDescriptor = (StackFrameDescriptorImpl)elementAt;
      //    final int previousFrameIndex = previousDescriptor.getUiIndex();
      //    needSeparator = (currentFrameIndex - previousFrameIndex != 1);
      //  }
      //}

      //if (selected) {
      //  setBackground(UIUtil.getListSelectionBackground());
      //}
      //else {
      //  Color bg = descriptor.getBackgroundColor();
      //  if (bg == null) bg = UIUtil.getListBackground();
      //  if (shouldHighlightAsRecursive) bg = myColorScheme.getColor(DebuggerColors.RECURSIVE_CALL_ATTRIBUTES);
      //  setBackground(bg);
      //}
      //
      //if (needSeparator) {
      //  final MatteBorder border = BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor.GRAY);
      //  setBorder(border);
      //}
      //else {
      //  setBorder(null);
      //}
      
      final String label = descriptor.getLabel();
      final int openingBrace = label.indexOf("{");
      final int closingBrace = (openingBrace < 0) ? -1 : label.indexOf("}");
      final SimpleTextAttributes attributes = getAttributes(descriptor);
      if (openingBrace < 0 || closingBrace < 0) {
        component.append(label, attributes);
      }
      else {
        component.append(label.substring(0, openingBrace - 1), attributes);
        component.append(" (" + label.substring(openingBrace + 1, closingBrace) + ")", SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES);

        component.append(label.substring(closingBrace + 1, label.length()), attributes);
        if (shouldHighlightAsRecursive && descriptor.isRecursiveCall()) {
          component.append(" [" + descriptor.getOccurrenceIndex() + "]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
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
