// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class IntroduceVariableHandler extends IntroduceVariableBase implements JavaIntroduceVariableHandlerBase {

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiExpression expression) {
    invokeImpl(project, expression, editor);
  }

  @Override
  public IntroduceVariableSettings getSettings(Project project, Editor editor,
                                               PsiExpression expr, PsiExpression[] occurrences,
                                               TypeSelectorManagerImpl typeSelectorManager,
                                               boolean declareFinalIfAll,
                                               boolean anyAssignmentLHS,
                                               final InputValidator validator,
                                               PsiElement anchor, JavaReplaceChoice replaceChoice) {
    if (replaceChoice != null) {
      return super.getSettings(project, editor, expr, occurrences, typeSelectorManager, declareFinalIfAll, anyAssignmentLHS, validator,
                               anchor, replaceChoice);
    }
    ArrayList<RangeHighlighter> highlighters = new ArrayList<>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      if (occurrences.length > 1) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, EditorColors.SEARCH_RESULT_ATTRIBUTES, true, highlighters);
      }
    }

    IntroduceVariableDialog dialog = new IntroduceVariableDialog(
      project, expr, occurrences.length, anyAssignmentLHS, declareFinalIfAll,
      typeSelectorManager,
      validator);
    if (!dialog.showAndGet()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    }
    else {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  @Override
  protected void showErrorMessage(final Project project, Editor editor, @NlsContexts.DialogMessage String message) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INTRODUCE_VARIABLE);
  }

  @Override
  protected boolean reportConflicts(final MultiMap<PsiElement,String> conflicts, final Project project, IntroduceVariableSettings dialog) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    final boolean ok = conflictsDialog.isOK();
    if (!ok && conflictsDialog.isShowConflicts()) {
      if (dialog instanceof DialogWrapper) ((DialogWrapper)dialog).close(DialogWrapper.CANCEL_EXIT_CODE);
    }
    return ok;
  }

  @Override
  protected boolean acceptLocalVariable() {
    return false;
  }
}
