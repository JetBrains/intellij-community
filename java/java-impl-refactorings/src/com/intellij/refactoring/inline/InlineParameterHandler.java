// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public class InlineParameterHandler extends JavaInlineActionHandler {
  private static final String REFACTORING_ID = "refactoring.inline.parameter";

  @Override
  public boolean canInlineElement(PsiElement element) {
    if (element instanceof PsiParameter) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiParameterList &&
          parent.getParent() instanceof PsiMethod &&
          element.getLanguage() == JavaLanguage.INSTANCE) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void inlineElement(final Project project, final Editor editor, @NotNull PsiElement psiElement) {
    final PsiParameter psiParameter = (PsiParameter) psiElement;
    final PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
    if (!(parameterList.getParent() instanceof PsiMethod method)) {
      return;
    }
    final int index = parameterList.getParameterIndex(psiParameter);

    String errorMessage = getCannotInlineMessage(psiParameter, method);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    final Ref<PsiExpression> refInitializer = new Ref<>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<>();
    final Ref<PsiCallExpression> refMethodCall = new Ref<>();
    final List<PsiReference> occurrences = Collections.synchronizedList(new ArrayList<>());
    final Collection<PsiFile> containingFiles = Collections.synchronizedSet(new HashSet<>());
    containingFiles.add(psiParameter.getContainingFile());
    boolean[] result = new boolean[1];
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      result[0] = ReferencesSearch.search(method).forEach(psiReference -> {
        PsiElement element = psiReference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiCallExpression methodCall) {
          occurrences.add(psiReference);
          containingFiles.add(element.getContainingFile());
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length <= index) return false;
          PsiExpression argument = expressions[index];
          if (!refInitializer.isNull()) {
            return argument != null && PsiEquivalenceUtil.areElementsEquivalent(refInitializer.get(), argument);
          }
          if (InlineToAnonymousConstructorProcessor.isConstant(argument) || getReferencedFinalField(argument) != null) {
            if (refConstantInitializer.isNull()) {
              refConstantInitializer.set(argument);
            }
            else if (!isSameConstant(argument, refConstantInitializer.get())) {
              return false;
            }
          }
          else if (!isRecursiveReferencedParameter(argument, psiParameter)) {
            if (!refConstantInitializer.isNull()) return false;
            refInitializer.set(argument);
            refMethodCall.set(methodCall);
          }
        }
        return true;
      });
    }, JavaRefactoringBundle.message("inline.parameter.method.usages.progress"), true, project)) {
      return;
    }
    final PsiReference reference = TargetElementUtil.findReference(editor);
    final PsiReferenceExpression refExpr = reference instanceof PsiReferenceExpression ? (PsiReferenceExpression)reference : null;
    final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(refExpr, PsiCodeBlock.class);
    if (codeBlock != null) {
      final PsiElement[] defs = DefUseUtil.getDefs(codeBlock, psiParameter, refExpr);
      if (defs.length == 1) {
        final PsiElement def = defs[0];
        if (def instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)def)) {
          final PsiExpression rExpr = ((PsiAssignmentExpression)def.getParent()).getRExpression();
          if (rExpr != null) {
            PsiExpression toInline = InlineLocalHandler.getDefToInline(psiParameter, refExpr, codeBlock, false);
            if (toInline != null) {
              final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, psiParameter, toInline);

              if (InlineLocalHandler.checkRefsInAugmentedAssignmentOrUnaryModified(refs, def) == null) {
                WriteCommandAction.writeCommandAction(project).run(() -> {
                  for (final PsiElement ref : refs) {
                    InlineUtil.inlineVariable(psiParameter, rExpr, (PsiJavaCodeReferenceElement)ref);
                  }
                  def.getParent().delete();
                });
                return;
              }
            }
          }
        }
      }
    }
    if (occurrences.isEmpty()) {
      CommonRefactoringUtil
        .showErrorHint(project, editor, JavaRefactoringBundle.message("inline.parameter.no.usages.warning.message"), JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }
    if (!result[0]) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.parameter.cannot.find.initializer.warning.message"),
                                          JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }
    if (!refInitializer.isNull()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final InlineParameterExpressionProcessor processor =
          new InlineParameterExpressionProcessor(refMethodCall.get(), method, psiParameter, refInitializer.get(),
                                                 method.getProject().getUserData(
                                                   InlineParameterExpressionProcessor.CREATE_LOCAL_FOR_TESTS));
        processor.run();
      }
      else {
        final boolean createLocal = ReferencesSearch.search(psiParameter).findAll().size() > 1;
        InlineParameterDialog dlg = new InlineParameterDialog(refMethodCall.get(), method, psiParameter, refInitializer.get(), createLocal);
        dlg.show();
      }
      return;
    }
    if (refConstantInitializer.isNull()) {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          JavaRefactoringBundle.message("inline.parameter.cannot.find.initializer.warning.message"), JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    final Ref<Boolean> isNotConstantAccessible = new Ref<>();
    final PsiExpression constantExpression = refConstantInitializer.get();
    constantExpression.accept(new JavaRecursiveElementVisitor(){
      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiMember && !PsiUtil.isMemberAccessibleAt((PsiMember)resolved, method)) {
          isNotConstantAccessible.set(Boolean.TRUE);
        }
      }
    });
    if (!isNotConstantAccessible.isNull() && isNotConstantAccessible.get()) {
      CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.parameter.not.accessible.warning.message"), JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    for (PsiReference psiReference : ReferencesSearch.search(psiParameter)) {
      final PsiElement element = psiReference.getElement();
      if (element instanceof PsiExpression && PsiUtil.isAccessedForWriting((PsiExpression)element)) {
        CommonRefactoringUtil.showErrorHint(project, editor, JavaRefactoringBundle.message("inline.parameter.write.usages.warning.message"), JavaRefactoringBundle.message("inline.parameter.refactoring"), null);
        return;
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String occurrencesString = RefactoringBundle.message("occurrences.string", occurrences.size());
      String question = JavaRefactoringBundle.message("inline.parameter.confirmation", psiParameter.getName(),
                                                  constantExpression.getText()) + " " + occurrencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        getRefactoringName(),
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
      if (!dialog.showAndGet()) {
        return;
      }
    }

    final RefactoringEventData data = new RefactoringEventData();
    data.addElement(psiElement.copy());

    CommandProcessor.getInstance().executeCommand(project, () -> {
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringStarted(REFACTORING_ID, data);
      SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, constantExpression);
      project.getMessageBus().syncPublisher(RefactoringEventListener.REFACTORING_EVENT_TOPIC).refactoringDone(REFACTORING_ID, null);
    }, getRefactoringName(), null);
  }

  @Nullable
  private static PsiField getReferencedFinalField(final PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiField field) {
      final PsiModifierList modifierList = field.getModifierList();
      if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
        return field;
      }
    }
    return null;
  }

  private static boolean isRecursiveReferencedParameter(final PsiExpression argument, final PsiParameter param) {
    if (argument instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiParameter) {
        return element.equals(param);
      }
    }
    return false;
  }

  private static boolean isSameConstant(final PsiExpression expr1, final PsiExpression expr2) {
    boolean expr1Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr1);
    boolean expr2Null = InlineToAnonymousConstructorProcessor.ourNullPattern.accepts(expr2);
    if (expr1Null || expr2Null) {
      return expr1Null && expr2Null;
    }
    final PsiField field1 = getReferencedFinalField(expr1);
    final PsiField field2 = getReferencedFinalField(expr2);
    if (field1 != null && field1 == field2) {
      if (field1.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
      PsiExpression q1 = ((PsiReferenceExpression)expr1).getQualifierExpression();
      PsiExpression q2 = ((PsiReferenceExpression)expr2).getQualifierExpression();
      return q1 == null && q2 == null ||
             q1 != null && q2 != null && PsiEquivalenceUtil.areElementsEquivalent(q1, q2);
    }
    Object value1 = JavaPsiFacade.getInstance(expr1.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr1);
    Object value2 = JavaPsiFacade.getInstance(expr2.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr2);
    return value1 != null && value1.equals(value2);
  }

  @Nullable
  private static @NlsContexts.DialogMessage String getCannotInlineMessage(final PsiParameter psiParameter, final PsiMethod method) {
    if (psiParameter.isVarArgs()) {
      return JavaRefactoringBundle.message("inline.parameter.error.varargs");
    }
    if (method.findSuperMethods().length > 0 ||
        OverridingMethodsSearch.search(method).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
      return JavaRefactoringBundle.message("inline.parameter.error.hierarchy");
    }

    if (!method.getManager().isInProject(method)) {
      return JavaRefactoringBundle.message("inline.parameter.error.non.project.method");
    }
    return null;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement element) {
    return JavaRefactoringBundle.message("inline.parameter.action.name");
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return JavaRefactoringBundle.message("inline.parameter.refactoring");
  }
}
