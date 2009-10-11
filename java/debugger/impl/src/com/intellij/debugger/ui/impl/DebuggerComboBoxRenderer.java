/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

public class DebuggerComboBoxRenderer extends BasicComboBoxRenderer {

  public Component getListCellRendererComponent(
    JList list,
    Object value,
    int index,
    boolean isSelected,
    boolean cellHasFocus) {

    JLabel component = (JLabel)super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    if (list.getComponentCount() > 0) {
      Icon icon = getIcon(value);
      if (icon != null) {
        component.setIcon(icon);
      }
    }
    else {
      component.setIcon(null);
    }
    return component;
  }

  @Nullable
  private static Icon getIcon(Object item) {
    if (item instanceof ThreadDescriptorImpl) {
      ThreadDescriptorImpl descriptor = (ThreadDescriptorImpl)item;
      return descriptor.getIcon();
    }
    if (item instanceof StackFrameDescriptorImpl) {
      return ((StackFrameDescriptorImpl)item).getIcon();
    }
    return null;
  }
}