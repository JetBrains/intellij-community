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
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DebuggerComboBoxRenderer extends ListCellRendererWrapper {
  public DebuggerComboBoxRenderer(final ListCellRenderer listCellRenderer) {
    super();
  }

  @Override
  public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    if (list.getComponentCount() > 0) {
      Icon icon = getIcon(value);
      if (icon != null) {
        setIcon(icon);
      }
    }
    else {
      setIcon(null);
    }
  }

  @Nullable
  private static Icon getIcon(Object item) {
    if (item instanceof ThreadDescriptorImpl) {
      return ((ThreadDescriptorImpl)item).getIcon();
    }
    if (item instanceof StackFrameDescriptorImpl) {
      return ((StackFrameDescriptorImpl)item).getIcon();
    }
    return null;
  }
}