/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.inline;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.DefUseUtil;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author yole
 */
public class InlineParameterHandler extends JavaInlineActionHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineParameterHandler");
  public static final String REFACTORING_NAME = RefactoringBundle.message("inline.parameter.refactoring");

  public boolean canInlineElement(PsiElement element) {
    return element instanceof PsiParameter && element.getParent() instanceof PsiParameterList;
  }

  public void inlineElement(final Project project, final Editor editor, final PsiElement psiElement) {
    final PsiParameter psiParameter = (PsiParameter) psiElement;
    final PsiParameterList parameterList = (PsiParameterList) psiParameter.getParent();
    if (!(parameterList.getParent() instanceof PsiMethod)) {
      return;
    }
    final int index = parameterList.getParameterIndex(psiParameter);
    final PsiMethod method = (PsiMethod) parameterList.getParent();

    String errorMessage = getCannotInlineMessage(psiParameter, method);
    if (errorMessage != null) {
      CommonRefactoringUtil.showErrorHint(project, editor, errorMessage, RefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    final Ref<PsiExpression> refInitializer = new Ref<PsiExpression>();
    final Ref<PsiExpression> refConstantInitializer = new Ref<PsiExpression>();
    final Ref<PsiCallExpression> refMethodCall = new Ref<PsiCallExpression>();
    final List<PsiReference> occurrences = new ArrayList<PsiReference>();
    final Collection<PsiFile> containingFiles = new HashSet<PsiFile>();
    containingFiles.add(psiParameter.getContainingFile());
    boolean result = ReferencesSearch.search(method).forEach(new Processor<PsiReference>() {
      public boolean process(final PsiReference psiReference) {
        PsiElement element = psiReference.getElement();
        final PsiElement parent = element.getParent();
        if (parent instanceof PsiCallExpression) {
          final PsiCallExpression methodCall = (PsiCallExpression)parent;
          occurrences.add(psiReference);
          containingFiles.add(element.getContainingFile());
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          if (expressions.length <= index) return false;
          PsiExpression argument = expressions[index];
          if (!refInitializer.isNull()) {
            return argument != null
                   && PsiEquivalenceUtil.areElementsEquivalent(refInitializer.get(), argument)
                   && PsiEquivalenceUtil.areElementsEquivalent(refMethodCall.get(), methodCall);
          }
          if (InlineToAnonymousConstructorProcessor.isConstant(argument) || getReferencedFinalField(argument) != null) {
            if (refConstantInitializer.isNull()) {
              refConstantInitializer.set(argument);
            }
            else if (!isSameConstant(argument, refConstantInitializer.get())) {
              return false;
            }
          } else if (!isRecursiveReferencedParameter(argument, psiParameter)) {
            if (!refConstantInitializer.isNull()) return false;
            refInitializer.set(argument);
            refMethodCall.set(methodCall);
          }
        }
        return true;
      }
    });
    if (occurrences.isEmpty()) {
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement refExpr = psiElement.getContainingFile().findElementAt(offset);
      final PsiCodeBlock codeBlock = PsiTreeUtil.getParentOfType(refExpr, PsiCodeBlock.class);
      if (codeBlock != null) {
        final PsiElement[] defs = DefUseUtil.getDefs(codeBlock, psiParameter, refExpr);
        if (defs.length == 1) {
          final PsiElement def = defs[0];
          if (def instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand((PsiExpression)def)) {
            final PsiExpression rExpr = ((PsiAssignmentExpression)def.getParent()).getRExpression();
            if (rExpr != null) {
              final PsiElement[] refs = DefUseUtil.getRefs(codeBlock, psiParameter, refExpr);

              if (InlineLocalHandler.checkRefsInAugmentedAssignmentOrUnaryModified(refs) == null) {
                new WriteCommandAction(project) {
                  @Override
                  protected void run(Result result) throws Throwable {
                    for (final PsiElement ref : refs) {
                      InlineUtil.inlineVariable(psiParameter, rExpr, (PsiJavaCodeReferenceElement)ref);
                    }
                    def.getParent().delete();
                  }
                }.execute();
                return;
              }
            }
          }
        }
      }
      CommonRefactoringUtil
        .showErrorHint(project, editor, "Method has no usages", RefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }
    if (!result) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot find constant initializer for parameter", RefactoringBundle.message("inline.parameter.refactoring"), null);
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
      CommonRefactoringUtil.showErrorHint(project, editor, "Cannot find constant initializer for parameter", RefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    final Ref<Boolean> isNotConstantAccessible = new Ref<Boolean>();
    final PsiExpression constantExpression = refConstantInitializer.get();
    constantExpression.accept(new JavaRecursiveElementVisitor(){
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiMember && !PsiUtil.isAccessible((PsiMember)resolved, method, null)) {
          isNotConstantAccessible.set(Boolean.TRUE);
        }
      }
    });
    if (!isNotConstantAccessible.isNull() && isNotConstantAccessible.get()) {
      CommonRefactoringUtil.showErrorHint(project, editor, "Constant initializer is not accessible in method body", RefactoringBundle.message("inline.parameter.refactoring"), null);
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      String occurencesString = RefactoringBundle.message("occurences.string", occurrences.size());
      String question = RefactoringBundle.message("inline.parameter.confirmation", psiParameter.getName(),
                                                  constantExpression.getText()) + " " + occurencesString;
      RefactoringMessageDialog dialog = new RefactoringMessageDialog(
        REFACTORING_NAME,
        question,
        HelpID.INLINE_VARIABLE,
        "OptionPane.questionIcon",
        true,
        project);
      dialog.show();
      if (!dialog.isOK()){
        return;
      }
    }

    new WriteCommandAction(project,
                           RefactoringBundle.message("inline.parameter.command.name", psiParameter.getName()),
                           containingFiles.toArray(new PsiFile[containingFiles.size()]) ) {
      protected void run(final Result result) throws Throwable {
        SameParameterValueInspection.InlineParameterValueFix.inlineSameParameterValue(method, psiParameter, constantExpression);
      }

      protected UndoConfirmationPolicy getUndoConfirmationPolicy() {
        return UndoConfirmationPolicy.DEFAULT;
      }
    }.execute();
  }

  @Nullable
  private static PsiField getReferencedFinalField(final PsiExpression argument) {
    if (argument instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiField) {
        final PsiField field = (PsiField)element;
        final PsiModifierList modifierList = field.getModifierList();
        if (modifierList != null && modifierList.hasModifierProperty(PsiModifier.FINAL)) {
          return field;
        }
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
      return true;
    }
    Object value1 = JavaPsiFacade.getInstance(expr1.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr1);
    Object value2 = JavaPsiFacade.getInstance(expr2.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr2);
    return value1 != null && value2 != null && value1.equals(value2);
  }

  @Nullable
  private static String getCannotInlineMessage(final PsiParameter psiParameter, final PsiMethod method) {
    if (psiParameter.isVarArgs()) {
      return RefactoringBundle.message("inline.parameter.error.varargs");
    }
    if (method.findSuperMethods().length > 0 ||
                 OverridingMethodsSearch.search(method, method.getUseScope(), true).toArray(PsiMethod.EMPTY_ARRAY).length > 0) {
      return RefactoringBundle.message("inline.parameter.error.hierarchy");
    }
    return null;
  }
}
