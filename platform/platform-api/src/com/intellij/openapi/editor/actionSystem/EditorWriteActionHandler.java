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
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.MockDocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

public abstract class EditorWriteActionHandler extends EditorActionHandler {
  private boolean inExecution;

  protected EditorWriteActionHandler() {
  }

  protected EditorWriteActionHandler(boolean runForEachCaret) {
    super(runForEachCaret);
  }

  @Override
  public final void doExecute(final Editor editor, @Nullable final Caret caret, final DataContext dataContext) {
    if (editor.isViewer()) return;

    if (dataContext != null) {
      Project project = CommonDataKeys.PROJECT.getData(dataContext);
      if (project != null && !FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) return;
    }

    ApplicationManager.getApplication().runWriteAction(new DocumentRunnable(editor.getDocument(),editor.getProject()) {
      @Override
      public void run() {
        final Document doc = editor.getDocument();
        final SelectionModel selectionModel = editor.getSelectionModel();
        if (selectionModel.hasBlockSelection()) {
          RangeMarker guard = selectionModel.getBlockSelectionGuard();
          if (guard != null) {
            DocumentEvent evt = new MockDocumentEvent(editor.getDocument(), editor.getCaretModel().getOffset());
            ReadOnlyFragmentModificationException e = new ReadOnlyFragmentModificationException(evt, guard);
            EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
            return;
          }
        }

        doc.startGuardedBlockChecking();
        try {
          executeWriteAction(editor, caret, dataContext);
        }
        catch (ReadOnlyFragmentModificationException e) {
          EditorActionManager.getInstance().getReadonlyFragmentModificationHandler(doc).handle(e);
        }
        finally {
          doc.stopGuardedBlockChecking();
        }
      }
    });
  }

  /**
   * @deprecated Use/override
   * {@link #executeWriteAction(com.intellij.openapi.editor.Editor, com.intellij.openapi.editor.Caret, com.intellij.openapi.actionSystem.DataContext)}
   * instead.
   */
  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      executeWriteAction(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  public void executeWriteAction(Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      //noinspection deprecation
      executeWriteAction(editor, dataContext);
    }
    finally {
      inExecution = false;
    }
  }
}
