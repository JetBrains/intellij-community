// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseCodeInsightAction extends CodeInsightAction {
  private final boolean lookForInjectedEditor;

  protected BaseCodeInsightAction() {
    this(true);
  }

  protected BaseCodeInsightAction(boolean lookForInjectedEditor) {
    this.lookForInjectedEditor = lookForInjectedEditor;
  }

  @Override
  protected @Nullable Editor getEditor(final @NotNull DataContext dataContext, final @NotNull Project project, boolean forUpdate) {
    Editor editor = getBaseEditor(dataContext, project);
    return lookForInjectedEditor ? getInjectedEditor(project, editor, !forUpdate) : editor;
  }

  public static Editor getInjectedEditor(@NotNull Project project, final Editor editor) {
    return getInjectedEditor(project, editor, true);
  }

  public static Editor getInjectedEditor(@NotNull Project project, final Editor editor, boolean commit) {
    if (editor == null) {
      return editor;
    }

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getCachedPsiFile(editor.getDocument());
    if (psiFile == null) {
      return editor;
    }

    if (commit) {
      documentManager.commitAllDocuments();
    }
    return InjectedLanguageEditorUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile);
  }

  protected @Nullable Editor getBaseEditor(@NotNull DataContext dataContext, @NotNull Project project) {
    return super.getEditor(dataContext, project, true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup == null) {
      super.update(event);
    }
    else {
      presentation.setEnabled(isValidForLookup());
    }
  }

  protected boolean isValidForLookup() {
    return false;
  }
}
