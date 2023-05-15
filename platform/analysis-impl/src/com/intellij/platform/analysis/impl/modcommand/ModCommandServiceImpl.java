// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.analysis.impl.modcommand;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.modcommand.*;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModCommandServiceImpl implements ModCommandService {
  @Override
  @NotNull
  public IntentionAction wrap(@NotNull ModCommandAction action) {
    return new ModCommandActionWrapper(action);
  }

  @Override
  @Nullable
  public ModCommandAction unwrap(@NotNull IntentionAction action) {
    while (action instanceof IntentionActionDelegate delegate) {
      action = delegate.getDelegate();
    }
    return action instanceof ModCommandActionWrapper wrapper ? wrapper.action() : null;
  }

  @Override
  public @NotNull ModStatus execute(@NotNull Project project, @NotNull ModCommand command) {
    if (command instanceof ModUpdatePsiFile upd) {
      return executeUpdate(project, upd);
    }
    if (command instanceof ModCompositeCommand cmp) {
      return executeComposite(project, cmp);
    }
    if (command instanceof ModNavigate nav) {
      return executeNavigate(project, nav);
    }
    if (command instanceof ModNothing) {
      return ModStatus.SUCCESS;
    }
    throw new IllegalArgumentException("Unknown command: " + command);
  }

  @NotNull
  private static ModStatus executeNavigate(@NotNull Project project, ModNavigate nav) {
    VirtualFile file = nav.file();
    int selectionStart = nav.selectionStart();
    int selectionEnd = nav.selectionEnd();
    int caret = nav.caret();
    FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
    if (fileEditor instanceof TextEditor textEditor) {
      Editor editor = textEditor.getEditor();
      if (selectionStart != -1 && selectionEnd != -1) {
        editor.getSelectionModel().setSelection(selectionStart, selectionEnd);
      }
      if (caret != -1) {
        editor.getCaretModel().moveToOffset(caret);
      }
      return ModStatus.SUCCESS;
    }
    return ModStatus.ABORT;
  }

  @NotNull
  private static ModStatus executeComposite(@NotNull Project project, ModCompositeCommand cmp) {
    for (ModCommand command : cmp.commands()) {
      ModStatus status = command.execute(project);
      if (status != ModStatus.SUCCESS) {
        return status;
      }
    }
    return ModStatus.SUCCESS;
  }

  private static @NotNull ModStatus executeUpdate(@NotNull Project project, @NotNull ModUpdatePsiFile upd) {
    PsiFile file = upd.file();
    String oldText = upd.oldText();
    String newText = upd.newText();
    if (!file.textMatches(oldText)) return ModStatus.ABORT;
    Document document = file.getViewProvider().getDocument();
    return WriteAction.compute(() -> {
      document.replaceString(0, document.getTextLength(), newText);
      PsiDocumentManager.getInstance(project).commitDocument(document);
      return ModStatus.SUCCESS;
    });
  }
}
