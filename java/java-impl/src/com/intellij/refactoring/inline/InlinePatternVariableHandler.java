// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InlinePatternVariableHandler extends JavaInlineActionHandler {
  @Override
  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiPatternVariable;
  }

  @Override
  public void inlineElement(Project project, Editor editor, PsiElement element) {
    final PsiReference psiReference = TargetElementUtil.findReference(editor);
    final PsiReferenceExpression refExpr = psiReference instanceof PsiReferenceExpression ? (PsiReferenceExpression)psiReference : null;
    invoke(project, editor, (PsiPatternVariable)element, refExpr);
  }

  public static void invoke(@NotNull final Project project,
                            final Editor editor,
                            @NotNull PsiPatternVariable pattern,
                            PsiReferenceExpression refExpr) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, pattern)) return;

    String initializerText = JavaPsiPatternUtil.getEffectiveInitializerText(pattern);
    if (initializerText == null) {
      ApplicationManager.getApplication().invokeLater(() -> {
        String message = RefactoringBundle.message("cannot.perform.refactoring");
        CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), HelpID.INLINE_VARIABLE);
      }, ModalityState.NON_MODAL);
      return;
    }
    List<PsiElement> refsToInlineList = new ArrayList<>(ReferencesSearch.search(pattern).mapping(PsiReference::getElement).findAll());
    String name = pattern.getName();
    if (refsToInlineList.isEmpty()) {
      InlineLocalHandler.showNoUsagesMessage(project, editor, name);
      return;
    }
    boolean inlineAll = editor == null || InlineLocalHandler.askInlineAll(project, pattern, refExpr, refsToInlineList);
    if (refsToInlineList.isEmpty()) return;
    final PsiElement[] refsToInline = PsiUtilCore.toPsiElementArray(refsToInlineList);
    PsiExpression defToInline = JavaPsiFacade.getElementFactory(project).createExpressionFromText(initializerText, pattern);

    final EditorColorsManager manager = EditorColorsManager.getInstance();
    final TextAttributes attributes = manager.getGlobalScheme().getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);

    if (editor != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      HighlightManager.getInstance(project).addOccurrenceHighlights(editor, refsToInline, attributes, true, null);
    }
    
    final Runnable runnable = () -> {
      final String refactoringId = "refactoring.inline.pattern.variable";
      PsiElement scope = pattern.getDeclarationScope();
      try {
        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(refsToInline);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(refactoringId, beforeData);

        List<SmartPsiElementPointer<PsiExpression>> exprs = WriteAction.compute(
          () -> InlineLocalHandler.inlineOccurrences(project, pattern, defToInline, refsToInline));

        if (inlineAll && ReferencesSearch.search(pattern).findFirst() == null && editor != null) {
          QuickFixFactory.getInstance().createRemoveUnusedVariableFix(pattern).invoke(project, editor, pattern.getContainingFile());
        }

        InlineLocalHandler.highlightOccurrences(project, editor, exprs);
      }
      finally {
        final RefactoringEventData afterData = new RefactoringEventData();
        afterData.addElement(scope);
        project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(refactoringId, afterData);
      }
    };

    CommandProcessor.getInstance()
      .executeCommand(project, () -> PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(runnable),
                      RefactoringBundle.message("inline.command", name), null);
  }


  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return getRefactoringName();
  }

  private static String getRefactoringName() {
    return RefactoringBundle.message("inline.pattern.variable.title");
  }

}