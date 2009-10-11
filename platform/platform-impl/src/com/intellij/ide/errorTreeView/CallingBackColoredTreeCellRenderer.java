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
package com.intellij.ide.errorTreeView;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;

import javax.swing.*;

public class CallingBackColoredTreeCellRenderer extends ColoredTreeCellRenderer {
  private CustomizeColoredTreeCellRenderer myCurrentCallback;

  public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (myCurrentCallback != null) {
      myCurrentCallback.customizeCellRenderer(this, tree, value, selected, expanded, leaf, row, hasFocus);
    }
  }

  public void setCurrentCallback(final CustomizeColoredTreeCellRenderer currentCallback) {
    myCurrentCallback = currentCallback;
  }
}
