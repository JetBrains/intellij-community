/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class CollapseSelectionAsCustomRegionAction extends EditorAction implements DumbAware {
  protected CollapseSelectionAsCustomRegionAction() {
    super(new EditorActionHandler() {
      @Override
      protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
        if (caret == null) caret = editor.getCaretModel().getPrimaryCaret();
        if (!caret.hasSelection()) return;
        FoldingModel foldingModel = editor.getFoldingModel();
        Caret finalCaret = caret;
        Inlay.Renderer renderer = new Inlay.Renderer() {
          @Override
          public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
            g.setColor(JBColor.CYAN);
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            g.drawLine(r.x, r.y, r.x + r.width - 1, r.y + r.height - 1);
            g.drawLine(r.x + r.width - 1, r.y, r.x, r.y + r.height - 1);
          }

          @Override
          public int calcWidthInPixels(@NotNull Editor editor) {
            return 60;
          }
        };
        foldingModel.runBatchFoldingOperation(() -> {
          FoldRegion region = ((FoldingModelEx)foldingModel).createFoldRegion(finalCaret.getSelectionStart(), finalCaret.getSelectionEnd(),
                                                                              renderer, null, false);
          if (region != null) {
            foldingModel.addFoldRegion(region);
            region.setExpanded(false);
          }
        });
      }
    });
  }
}
