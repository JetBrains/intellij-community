/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util.side;

import com.intellij.diff.tools.holders.TextEditorHolder;
import com.intellij.diff.util.Side;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ThreesideTextContentPanel extends ThreesideContentPanel {
  @NotNull private final List<? extends TextEditorHolder> myEditors;

  public ThreesideTextContentPanel(@NotNull List<? extends TextEditorHolder> editors,
                                   @NotNull List<JComponent> titleComponents) {
    super(editors, titleComponents);
    myEditors = editors;
  }

  @NotNull
  private static List<JComponent> getComponents(@NotNull List<? extends Editor> editors) {
    List<JComponent> result = new ArrayList<JComponent>();

    for (Editor editor : editors) {
      result.add(editor != null ? editor.getComponent() : null);
    }
    return result;
  }

  public void setScrollbarPainter(@NotNull ButtonlessScrollBarUI.ScrollbarRepaintCallback painter) {
    myEditors.get(1).getEditor().registerScrollBarRepaintCallback(painter);
  }

  @Override
  public void repaintDivider(@NotNull Side side) {
    super.repaintDivider(side);
    if (side == Side.RIGHT) myEditors.get(1).getEditor().getScrollPane().getVerticalScrollBar().repaint();
  }
}
