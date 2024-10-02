// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.actions.CopyAction;
import com.intellij.openapi.editor.actions.DocumentGuardedTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

@ApiStatus.Internal
public final class CutHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public CutHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void executeWriteAction(final @NotNull Editor editor, Caret caret, DataContext dataContext) {
    assert caret == null : "Invocation of 'cut' operation for specific caret is not supported";
    Project project = editor.getProject();
    PsiFile file = project == null ? null : PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) {
      if (myOriginalHandler != null) {
        myOriginalHandler.execute(editor, null, dataContext);
      }
      return;
    }

    final SelectionModel selectionModel = editor.getSelectionModel();
    CopyAction.SelectionToCopy selectionToCopy = CopyAction.prepareSelectionToCut(editor);
    if (selectionToCopy == null) {
      return;
    }
    dataContext = selectionToCopy.extendDataContext(dataContext);

    int start = selectionModel.getSelectionStart();
    int end = selectionModel.getSelectionEnd();
    final List<TextRange> selections = new ArrayList<>();
    if (editor.getCaretModel().supportsMultipleCarets()) {
      editor.getCaretModel().runForEachCaret(
        __ -> selections.add(new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd())));
    }

    EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_COPY).execute(editor, null, dataContext);

    if (editor.getCaretModel().supportsMultipleCarets()) {

      Collections.reverse(selections);
      final Iterator<TextRange> it = selections.iterator();
      editor.getCaretModel().runForEachCaret(__ -> {
        TextRange range = it.next();
        editor.getCaretModel().moveToOffset(range.getStartOffset());
        selectionModel.removeSelection();
        DocumentGuardedTextUtil.deleteString(editor.getDocument(), range.getStartOffset(), range.getEndOffset());
      });
      editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
    }
    else {
      if (start != end) {
        // There is a possible case that 'sticky selection' is active. It's automatically removed on copying then, so, we explicitly
        // remove the text.
        editor.getDocument().deleteString(start, end);
      }
      else {
        EditorModificationUtil.deleteSelectedText(editor);
      }
    }
  }
}
