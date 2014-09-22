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
package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class BaseFoldingHandler extends EditorActionHandler {
  @Override
  protected boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getProject() != null;
  }

  protected List<FoldRegion> getFoldRegionsForSelection(@NotNull Editor editor, @Nullable Caret caret) {
    FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    if (caret == null) {
      caret = editor.getCaretModel().getPrimaryCaret();
    }
    if (caret.hasSelection()) {
      List<FoldRegion> result = new ArrayList<FoldRegion>(allRegions.length);
      for (FoldRegion region : allRegions) {
        if (region.getStartOffset() >= caret.getSelectionStart() && region.getEndOffset() <= caret.getSelectionEnd()) {
          result.add(region);
        }
      }
      if (!result.isEmpty()) {
        return result;
      }
    }
    return Arrays.asList(allRegions);
  }
}
