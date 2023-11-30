// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actionSystem;

import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
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

  /** Consider subclassing {@link ForEachCaret} instead. */
  protected EditorWriteActionHandler(boolean runForEachCaret) {
    super(runForEachCaret);
  }

  @Override
  public void doExecute(final @NotNull Editor editor, final @Nullable Caret caret, final DataContext dataContext) {
    boolean preview = IntentionPreviewUtils.getPreviewEditor() == editor;
    if (!preview) {
      if (!EditorModificationUtil.checkModificationAllowed(editor)) return;
      if (!ApplicationManager.getApplication().isWriteAccessAllowed() && !EditorModificationUtil.requestWriting(editor)) return;
    }

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
    if (preview || editor instanceof TextComponentEditor) {
      runnable.run();
    }
    else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  /**
   * @deprecated Use/override {@link #executeWriteAction(Editor, Caret, DataContext)} instead.
   */
  @Deprecated
  public void executeWriteAction(Editor editor, DataContext dataContext) {
    executeWriteAction(editor, editor.getCaretModel().getCurrentCaret(), dataContext);
  }

  public void executeWriteAction(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
    if (inExecution) {
      return;
    }
    try {
      inExecution = true;
      executeWriteAction(editor, dataContext);
    }
    finally {
      inExecution = false;
    }
  }

  public abstract static class ForEachCaret extends EditorWriteActionHandler {
    protected ForEachCaret() {
      super(true);
    }

    @Override
    public abstract void executeWriteAction(@NotNull Editor editor,
                                            @SuppressWarnings("NullableProblems") @NotNull Caret caret,
                                            DataContext dataContext);
  }
}
