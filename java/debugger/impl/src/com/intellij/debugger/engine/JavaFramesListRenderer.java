// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.sun.jdi.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class JavaFramesListRenderer {
  public static void customizePresentation(StackFrameDescriptorImpl descriptor,
                                    @NotNull ColoredTextContainer component,
                                    @Nullable StackFrameDescriptorImpl selectedDescriptor) {
    component.setIcon(descriptor.getIcon());

    final ValueMarkup markup = descriptor.getValueMarkup();
    if (markup != null) {
      component.append("[" + markup.getText() + "] ", new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor()));
    }

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
      component.append(label.substring(closingBrace + 1), attributes);
    }

    if (isOccurrenceOfSelectedFrame(selectedDescriptor, descriptor) && descriptor.isRecursiveCall()) {
      component.append(" [" + descriptor.getOccurrenceIndex() + "]", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }
  }

  private static boolean isOccurrenceOfSelectedFrame(@Nullable StackFrameDescriptorImpl selectedDescriptor,
                                                     @NotNull StackFrameDescriptorImpl descriptor) {
    Method method = descriptor.getMethod();
    return selectedDescriptor != null && method != null && method.equals(selectedDescriptor.getMethod());
  }

  private static SimpleTextAttributes getAttributes(final StackFrameDescriptorImpl descriptor) {
    if (descriptor.isSynthetic() || descriptor.isInLibraryContent()) {
      return SimpleTextAttributes.GRAYED_ATTRIBUTES;
    }
    return SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES;
  }
}
