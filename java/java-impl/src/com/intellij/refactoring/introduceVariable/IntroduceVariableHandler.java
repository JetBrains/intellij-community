package com.intellij.refactoring.introduceVariable;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.ui.TypeSelectorManagerImpl;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.ArrayList;

public class IntroduceVariableHandler extends IntroduceVariableBase {

  protected IntroduceVariableSettings getSettings(final Project project, Editor editor, PsiExpression expr,
                                                  PsiElement[] occurrences, boolean anyAssignmentLHS,
                                                  boolean declareFinalIfAll, PsiType type,
                                                  TypeSelectorManagerImpl typeSelectorManager,
                                                  InputValidator validator) {
    ArrayList<RangeHighlighter> highlighters = new ArrayList<RangeHighlighter>();
    HighlightManager highlightManager = null;
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      EditorColorsManager colorsManager = EditorColorsManager.getInstance();
      TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
      if (occurrences.length > 1 ) {
        highlightManager.addOccurrenceHighlights(editor, occurrences, attributes, true, highlighters);
      }
    }

    IntroduceVariableDialog dialog = new IntroduceVariableDialog(
            project, expr, occurrences.length, anyAssignmentLHS, declareFinalIfAll,
            typeSelectorManager,
            validator);
    dialog.show();
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
      }
    } else {
      if (editor != null) {
        for (RangeHighlighter highlighter : highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog;
  }

  protected void showErrorMessage(final Project project, Editor editor, String message) {
    CommonRefactoringUtil.showErrorHint(project, editor, message, REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE);
  }

  protected void highlightReplacedOccurences(final Project project, Editor editor, final PsiElement[] replacedOccurences) {
    if (editor == null) return;
    HighlightManager highlightManager = HighlightManager.getInstance(project);
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    TextAttributes attributes = colorsManager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
    highlightManager.addOccurrenceHighlights(editor, replacedOccurences, attributes, true, null);
    WindowManager.getInstance().getStatusBar(project).setInfo(RefactoringBundle.message("press.escape.to.remove.the.highlighting"));
  }

  protected boolean reportConflicts(final ArrayList<String> conflicts, final Project project) {
    ConflictsDialog conflictsDialog = new ConflictsDialog(project, conflicts);
    conflictsDialog.show();
    return conflictsDialog.isOK();
  }
}
