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
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseCodeInsightAction extends CodeInsightAction {
  private final boolean myLookForInjectedEditor;

  protected BaseCodeInsightAction() {
    this(true);
  }

  protected BaseCodeInsightAction(boolean lookForInjectedEditor) {
    myLookForInjectedEditor = lookForInjectedEditor;
  }

  @Override
  protected @Nullable Editor getEditor(final @NotNull DataContext dataContext, final @NotNull Project project, boolean forUpdate) {
    Editor editor = getBaseEditor(dataContext, project);
    if (!myLookForInjectedEditor) return editor;
    return getInjectedEditor(project, editor, !forUpdate);
  }

  public static Editor getInjectedEditor(@NotNull Project project, final Editor editor) {
    return getInjectedEditor(project, editor, true);
  }

  public static Editor getInjectedEditor(@NotNull Project project, final Editor editor, boolean commit) {
    Editor injectedEditor = editor;
    if (editor != null) {
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      PsiFile psiFile = documentManager.getCachedPsiFile(editor.getDocument());
      if (psiFile != null) {
        if (commit) documentManager.commitAllDocuments();
        injectedEditor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, psiFile);
      }
    }
    return injectedEditor;
  }

  protected @Nullable Editor getBaseEditor(final @NotNull DataContext dataContext, final @NotNull Project project) {
    return super.getEditor(dataContext, project, true);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    final Lookup activeLookup = LookupManager.getInstance(project).getActiveLookup();
    if (activeLookup != null){
      presentation.setEnabled(isValidForLookup());
    }
    else {
      super.update(event);
    }
  }

  protected boolean isValidForLookup() {
    return false;
  }
}
