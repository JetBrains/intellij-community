// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.generation.AutoIndentLinesHandler;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AutoIndentLinesAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @Nullable Editor getEditor(@NotNull DataContext dataContext, @NotNull Project project, boolean forUpdate) {
    Editor editor = getBaseEditor(dataContext, project);
    if (editor == null) return null;
    Document document = editor.getDocument();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getCachedPsiFile(document);
    if (psiFile == null) return editor;
    if (!forUpdate) documentManager.commitDocument(document);
    int startLineOffset = DocumentUtil.getLineStartOffset(editor.getSelectionModel().getSelectionStart(), document);
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile, startLineOffset);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new AutoIndentLinesHandler();
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    final FileType fileType = file.getFileType();
    return fileType instanceof LanguageFileType &&
           LanguageFormatting.INSTANCE.forContext(((LanguageFileType)fileType).getLanguage(), file) != null;
  }
}