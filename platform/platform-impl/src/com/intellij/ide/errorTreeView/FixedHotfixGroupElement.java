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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CustomizeColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;

import javax.swing.*;

public class FixedHotfixGroupElement extends GroupingElement {
  private final CustomizeColoredTreeCellRenderer myCustomizeColoredTreeCellRenderer;

  public FixedHotfixGroupElement(String name, Object data, VirtualFile file) {
    super(name, data, file);
    myCustomizeColoredTreeCellRenderer = new CustomizeColoredTreeCellRenderer() {
      public void customizeCellRenderer(SimpleColoredComponent renderer,
                                        JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        renderer.setIcon(AllIcons.General.Information);
        renderer.append("Fixed: ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        final String[] text = getText();
        final String checkedText = ((text != null) && (text.length > 0)) ? text[0] : "";
        renderer.append(checkedText, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    };
  }

  @Override
  public CustomizeColoredTreeCellRenderer getLeftSelfRenderer() {
    return myCustomizeColoredTreeCellRenderer;
  }
}
