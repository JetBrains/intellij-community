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

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.folding.impl.FoldingUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public class ExpandRegionAction extends EditorAction {
  public ExpandRegionAction() {
    super(new EditorActionHandler() {
      @Override
      public void execute(Editor editor, DataContext dataContext) {
        expandRegionAtCaret(editor.getProject(), editor);
      }
      
      @Override
      public boolean isEnabled(Editor editor, DataContext dataContext) {
        return super.isEnabled(editor, dataContext) && CommonDataKeys.PROJECT.getData(dataContext) != null;
      }
      
    });
  }

  public static void expandRegionAtCaret(final Project project, @Nullable final Editor editor) {
    if (editor == null) return;

    CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);

    final int line = editor.getCaretModel().getLogicalPosition().line;
    Runnable processor = new Runnable() {
      @Override
      public void run() {
        FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
        if (region != null && !region.isExpanded()){
          region.setExpanded(true);
        }
        else{
          int offset = editor.getCaretModel().getOffset();
          FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
          for(int i = regions.length - 1; i >= 0; i--){
            region = regions[i];
            if (!region.isExpanded()){
              region.setExpanded(true);
              break;
            }
          }
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }
}
