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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.textarea.TextComponentEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for {@link EditorActionHandler} instances, which need to modify the document.
 * Implementations should override {@link #executeWriteAction(Editor, Caret, DataContext)}.
 */
public abstract class EditorWriteActionHandler extends EditorActionHandler {
  private boolean inExecution;

  protected EditorWriteActionHandler() {
  }

  protected EditorWriteActionHandler(boolean runForEachCaret) {
    super(runForEachCaret);
  }

  @Override
  public void doExecute(@NotNull final Editor editor, @Nullable final Caret caret, final DataContext dataContext) {
    if (editor.isViewer()) return;
    if (!ApplicationManager.getApplication().isWriteAccessAllowed() && !EditorModificationUtil.requestWriting(editor)) return;

    DocumentRunnable runnable = new DocumentRunnable(editor.getDocument(), editor.getProject()) {
      @Override
      public void run() {
        final Document doc = editor.getDocument();

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
    };
    if (editor instanceof TextComponentEditor) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  /**
   * @deprecated Use/override
   * {@link #executeWriteAction(Editor, Caret, DataContext)}
   * instead.
   */
  @Deprecated
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
