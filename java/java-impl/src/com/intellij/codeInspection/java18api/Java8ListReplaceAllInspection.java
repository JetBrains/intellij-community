// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.bulkOperation.UseBulkOperationInspection;
import com.intellij.codeInspection.util.IteratorDeclaration;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

public class Java8ListReplaceAllInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_SET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "set").parameterTypes("int", "E");
  private static final CallMatcher LIST_GET = CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_LIST, "get").parameterTypes("int");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiForStatement forStatement = PsiTreeUtil.getParentOfType(call, PsiForStatement.class);
        if (forStatement == null) return;
        PsiJavaToken endToken = forStatement.getRParenth();
        if (endToken == null) return;
        PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(call.getMethodExpression()));
        if (qualifier == null) return;
        PsiExpression iterable = findIterable(call);
        if (iterable == null) return;
        if (!PsiEquivalenceUtil.areElementsEquivalent(qualifier, iterable) &&
            !(qualifier instanceof PsiQualifiedExpression && iterable instanceof PsiQualifiedExpression)) {
          return;
        }
        holder.registerProblem(forStatement, new TextRange(0, endToken.getTextOffset() - forStatement.getTextOffset() + 1),
                               QuickFixBundle.message("java.8.list.replaceall.inspection.description"),
                               new ReplaceWithReplaceAllQuickFix(call));
      }
    };
  }

  @Nullable
  private static PsiExpression findIterable(PsiMethodCallExpression call) {
    if (!LIST_SET.test(call)) return null;
    PsiForStatement forStatement = PsiTreeUtil.getParentOfType(call, PsiForStatement.class);
    if (forStatement == null) return null;
    PsiStatement body = forStatement.getBody();
    if (body == null) return null;
    PsiStatement lastStatement = ArrayUtil.getLastElement(ControlFlowUtils.unwrapBlock(body));
    PsiElement parent = RefactoringUtil.getParentStatement(call, false);
    if (parent == null) return null;
    if (!PsiEquivalenceUtil.areElementsEquivalent(lastStatement, parent)) return null;
    CountingLoop loop = CountingLoop.from(forStatement);
    if (loop == null || !ExpressionUtils.isReferenceTo(call.getArgumentList().getExpressions()[0], loop.getCounter())) return null;
    Predicate<PsiVariable> variableAllowedPredicate = variable -> PsiEquivalenceUtil.areElementsEquivalent(variable, loop.getCounter());
    if (!LambdaGenerationUtil.canBeUncheckedLambda(forStatement.getBody(), variableAllowedPredicate)) return null;
    PsiMethodCallExpression listGetCall = getListGetCall(body);
    if (listGetCall == null) return null;
    Ref<Integer> counter = new Ref<>(0);
    PsiTreeUtil.processElements(body, e -> {
      if (ExpressionUtils.isReferenceTo(ObjectUtils.tryCast(e, PsiExpression.class), loop.getCounter())) {
        counter.set(counter.get() + 1);
      }
      return counter.get() <= 2;
    });
    if (counter.get() != 2) return null;
    return UseBulkOperationInspection.findIterableForIndexedLoop(loop, listGetCall);
  }

  @Nullable
  private static PsiMethodCallExpression getListGetCall(@NotNull PsiStatement body) {
    Ref<PsiMethodCallExpression> getElementExpression = new Ref<>();
    boolean isSoleGelElementExpression = PsiTreeUtil.processElements(body, e -> {
      PsiMethodCallExpression maybeListGet = ObjectUtils.tryCast(e, PsiMethodCallExpression.class);
      return !LIST_GET.test(maybeListGet) || getElementExpression.setIfNull(maybeListGet);
    }) && !getElementExpression.isNull();
    if (!isSoleGelElementExpression) return null;
    return getElementExpression.get();
  }

  private static class ReplaceWithReplaceAllQuickFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiMethodCallExpression> myCallPointer;

    private ReplaceWithReplaceAllQuickFix(@NotNull PsiMethodCallExpression call) {
      SmartPointerManager manager = SmartPointerManager.getInstance(call.getProject());
      myCallPointer = manager.createSmartPsiElementPointer(call);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.list.replaceall.inspection.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = myCallPointer.getElement();
      if (call == null) return;
      PsiExpression qualifier = ExpressionUtils.getEffectiveQualifier(call.getMethodExpression());
      if (qualifier == null) return;
      PsiExpression iterable = findIterable(call);
      if (iterable == null) return;
      PsiElement parent = RefactoringUtil.getParentStatement(iterable, false);
      if (parent == null) return;
      CommentTracker ct = new CommentTracker();
      String bulkMethodParameterText = calculateReplaceAllLambdaExpressionText(call, ct);
      String text = ct.text(qualifier) + ".replaceAll(" + bulkMethodParameterText + ");";
      PsiElement result = ct.replaceAndRestoreComments(parent, text);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      simplifyToExpressionLambda(result);
      result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
    }

    @Nullable
    private static String calculateReplaceAllLambdaExpressionText(PsiMethodCallExpression listSetCall, CommentTracker ct) {
      PsiForStatement forStatement = PsiTreeUtil.getParentOfType(listSetCall, PsiForStatement.class);
      if (forStatement == null) return null;
      PsiStatement body = forStatement.getBody();
      if (body == null) return null;
      PsiMethodCallExpression listGetCall = getListGetCall(body);
      if (listGetCall == null) return null;
      PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(listGetCall, PsiDeclarationStatement.class);
      PsiLocalVariable var = IteratorDeclaration.getDeclaredVariable(declarationStatement);
      String paramName;
      if (var != null && var.getInitializer() == listGetCall) {
        paramName = var.getName();
        new CommentTracker().deleteAndRestoreComments(declarationStatement);
      }
      else {
        paramName = new VariableNameGenerator(body, VariableKind.PARAMETER).byExpression(listGetCall).generate(true);
        PsiElement element = new CommentTracker().replaceAndRestoreComments(listGetCall, paramName);
        PsiLocalVariable variable =
          IteratorDeclaration.getDeclaredVariable(PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class));
        inlineVariable(variable);
      }
      String text = "return " + ct.textWithComments(listSetCall.getArgumentList().getExpressions()[1]) + ";";
      PsiElement result = new CommentTracker().replaceAndRestoreComments(listSetCall.getParent(), text);
      String codeBlockText = body instanceof PsiBlockStatement ? ct.text(body) : "{ " + ct.text(result) + " }";
      return paramName + " -> " + codeBlockText;
    }

    private static void inlineVariable(@Nullable PsiLocalVariable variable) {
      if (variable == null) return;
      final Collection<PsiReference> references = ReferencesSearch.search(variable).findAll();
      PsiExpression initializer = variable.getInitializer();
      if (initializer == null || references.size() != 1) return;
      InlineUtil.inlineVariable(variable, initializer, (PsiJavaCodeReferenceElement)references.iterator().next());
      variable.delete();
    }

    private static void simplifyToExpressionLambda(@NotNull PsiElement element) {
      PsiExpressionStatement expressionStatement = ObjectUtils.tryCast(element, PsiExpressionStatement.class);
      if (expressionStatement == null) return;
      PsiMethodCallExpression call = ObjectUtils.tryCast(expressionStatement.getExpression(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression arg = ArrayUtil.getFirstElement(call.getArgumentList().getExpressions());
      PsiLambdaExpression lambdaExpression = ObjectUtils.tryCast(arg, PsiLambdaExpression.class);
      if (lambdaExpression == null) return;
      LambdaRefactoringUtil.simplifyToExpressionLambda(lambdaExpression);
    }
  }
}
