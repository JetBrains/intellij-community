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
package com.intellij.psi.codeStyle.arrangement.engine;

import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.fileEditor.impl.text.CodeFoldingState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 11/21/12 12:57 PM
 */
public class RestoreFoldArrangementCallback implements ArrangementCallback {

  @NotNull private final  Editor           myEditor;
  @Nullable private final CodeFoldingState myCodeFoldingState;

  public RestoreFoldArrangementCallback(@NotNull Editor editor) {
    myEditor = editor;

    Project project = editor.getProject();
    if (project == null) {
      myCodeFoldingState = null;
    }
    else {
      final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(editor.getProject());
      myCodeFoldingState = foldingManager.saveFoldingState(editor);
    }
  }

  @Override
  public void afterArrangement(@NotNull final List<ArrangementMoveInfo> moveInfos) {
    // Restore state for the PSI elements not affected by arrangement.
    Project project = myEditor.getProject();
    if (myCodeFoldingState != null && project != null) {
      final CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
      foldingManager.updateFoldRegions(myEditor);
      myEditor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
        @Override
        public void run() {
          foldingManager.restoreFoldingState(myEditor, myCodeFoldingState);
        }
      });
    }
  }
}
