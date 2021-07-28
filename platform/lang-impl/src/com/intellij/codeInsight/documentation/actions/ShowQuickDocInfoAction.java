// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.documentation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowQuickDocInfoAction extends CodeInsightAction implements HintManagerImpl.ActionToIgnore, DumbAware, PopupAction {
  @SuppressWarnings("SpellCheckingInspection") public static final String CODEASSISTS_QUICKJAVADOC_FEATURE = "codeassists.quickjavadoc";
  @SuppressWarnings("SpellCheckingInspection") public static final String CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE = "codeassists.quickjavadoc.lookup";
  @SuppressWarnings("SpellCheckingInspection") public static final String CODEASSISTS_QUICKJAVADOC_CTRLN_FEATURE = "codeassists.quickjavadoc.ctrln";

  private final boolean myLookForInjectedEditor;

  public ShowQuickDocInfoAction() {
    this(true);
    setEnabledInModalContext(true);
    setInjectedContext(true);
  }

  protected ShowQuickDocInfoAction(boolean lookForInjectedEditor) {
    myLookForInjectedEditor = lookForInjectedEditor;
  }

  @NotNull
  @Override
  protected CodeInsightActionHandler getHandler() {
    return new CodeInsightActionHandler() {
      @Override
      public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        DocumentationManager documentationManager = DocumentationManager.getInstance(project);
        JBPopup hint = documentationManager.getDocInfoHint();
        documentationManager.showJavaDocInfo(editor, file, hint != null || LookupManager.getActiveLookup(editor) == null);
      }

      @Override
      public boolean startInWriteAction() {
        return false;
      }
    };
  }

  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    presentation.setEnabled(false);

    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return;

    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    if (editor == null && element == null) return;

    if (LookupManager.getInstance(project).getActiveLookup() != null) {
      if (isValidForLookup()) presentation.setEnabled(true);
    }
    else {
      if (editor != null) {
        if (event.getData(EditorGutter.KEY) != null) return;

        PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null && element == null) return;
      }
      presentation.setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);

    if (project != null && editor != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_FEATURE);
      final LookupImpl lookup = (LookupImpl)LookupManager.getInstance(project).getActiveLookup();
      if (lookup != null) {
        //dumpLookupElementWeights(lookup);
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE);
      }
      actionPerformedImpl(project, editor);
    }
    else if (project != null && element != null) {
      FeatureUsageTracker.getInstance().triggerFeatureUsed(CODEASSISTS_QUICKJAVADOC_CTRLN_FEATURE);
      CommandProcessor.getInstance().executeCommand(project,
                                                    () -> {
                                                      DocumentationManager documentationManager = DocumentationManager.getInstance(project);
                                                      JBPopup hint = documentationManager.getDocInfoHint();
                                                      documentationManager.showJavaDocInfo(element, null, hint != null, null);
                                                    },
                                                    getCommandName(),
                                                    null);
    }
  }

  @Override
  @Nullable
  protected Editor getEditor(@NotNull final DataContext dataContext, @NotNull final Project project, boolean forUpdate) {
    Editor editor = getBaseEditor(dataContext, project);
    if (!myLookForInjectedEditor) return editor;
    return getInjectedEditor(project, editor, !forUpdate);
  }

  @Nullable
  protected Editor getBaseEditor(@NotNull final DataContext dataContext, @NotNull final Project project) {
    return super.getEditor(dataContext, project, true);
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
}
