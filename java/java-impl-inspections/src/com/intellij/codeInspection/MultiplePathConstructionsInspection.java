// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.duplicateExpressions.ExpressionHashingStrategy;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MultiplePathConstructionsInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher PATH_CONSTRUCTION_CALL = CallMatcher.anyOf(
    CallMatcher.staticCall("java.nio.file.Path", "of"),
    CallMatcher.staticCall("java.nio.file.Paths", "get")
  );

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel7OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        super.visitMethod(method);
        PsiCodeBlock methodBody = method.getBody();
        if (methodBody == null) return;
        PathCallsCollector pathCallsCollector = new PathCallsCollector();
        methodBody.accept(pathCallsCollector);
        pathCallsCollector.getCalls().forEach((args, calls) -> {
          if (calls.size() < 2 || !isEffectivelyFinal(methodBody, args)) return;
          calls.forEach(call -> holder.registerProblem(call,
                                                       JavaBundle.message("inspection.multiple.path.constructions.description"),
                                                       new ReplaceWithPathVariableFix(isOnTheFly)));
        });
      }
    };
  }

  private static boolean isEffectivelyFinal(@NotNull PsiElement context, @NotNull PsiExpressionList expressionList) {
    return ContainerUtil.and(expressionList.getExpressions(), e -> isEffectivelyFinal(context, e));
  }

  private static boolean isEffectivelyFinal(@NotNull PsiElement context, PsiExpression expression) {
    return ExpressionUtils.nonStructuralChildren(expression).allMatch(c -> isEffectivelyFinal(context, expression, c));
  }

  private static boolean isEffectivelyFinal(PsiElement context, PsiExpression parent, PsiExpression child) {
    if (child != parent) return isEffectivelyFinal(context, child);
    if (child instanceof PsiLiteralExpression) return true;
    if (child instanceof PsiPolyadicExpression) {
      return ContainerUtil.and(((PsiPolyadicExpression)child).getOperands(), e -> isEffectivelyFinal(context, e));
    }
    if (!(child instanceof PsiReferenceExpression)) return false;
    PsiVariable target = ObjectUtils.tryCast(((PsiReferenceExpression)child).resolve(), PsiVariable.class);
    if (!PsiUtil.isJvmLocalVariable(target)) return false;
    return HighlightControlFlowUtil.isEffectivelyFinal(target, context, null);
  }

  private static class PathCallsCollector extends JavaRecursiveElementWalkingVisitor {

    private final Map<PsiExpressionList, List<PsiMethodCallExpression>> myCalls =
      CollectionFactory.createCustomHashingStrategyMap(new ExpressionListHashingStrategy());


    private static class ExpressionListHashingStrategy implements HashingStrategy<PsiExpressionList> {

      private static final ExpressionHashingStrategy EXPRESSION_STRATEGY = new ExpressionHashingStrategy();

      @Override
      public int hashCode(PsiExpressionList list) {
        if (list == null) return 0;
        int hash = 0;
        for (PsiExpression expression : list.getExpressions()) {
          hash = hash * 31 + EXPRESSION_STRATEGY.hashCode(expression);
        }
        return hash;
      }

      @Override
      public boolean equals(PsiExpressionList l1, PsiExpressionList l2) {
        if (l1 == null || l2 == null) return l1 == l2;
        PsiExpression[] e1 = l1.getExpressions();
        PsiExpression[] e2 = l2.getExpressions();
        if (e1.length != e2.length) return false;
        for (int i = 0; i < e1.length; i++) {
          if (!EXPRESSION_STRATEGY.equals(e1[i], e2[i])) return false;
        }
        return true;
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!PATH_CONSTRUCTION_CALL.test(expression)) return;
      PsiExpressionList args = expression.getArgumentList();
      List<PsiMethodCallExpression> calls = myCalls.computeIfAbsent(args, k -> new SmartList<>());
      calls.add(expression);
    }

    private Map<PsiExpressionList, List<PsiMethodCallExpression>> getCalls() {
      return myCalls;
    }
  }

  private static class ReplaceWithPathVariableFix implements LocalQuickFix {

    private final boolean myIsOnTheFly;

    private ReplaceWithPathVariableFix(boolean isOnTheFly) {
      myIsOnTheFly = isOnTheFly;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.extract.to.path.variable.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiMethod containingMethod = PsiTreeUtil.getParentOfType(call, PsiMethod.class);
      if (containingMethod == null) return;
      PsiCodeBlock methodBody = containingMethod.getBody();
      if (methodBody == null) return;
      if (!PATH_CONSTRUCTION_CALL.test(call)) return;
      PsiExpressionList args = call.getArgumentList();
      PathCallsCollector callsCollector = new PathCallsCollector();
      methodBody.accept(callsCollector);
      List<PsiMethodCallExpression> argCalls = callsCollector.getCalls().get(args);
      if (argCalls == null) return;
      PsiExpression[] occurrences = argCalls.toArray(PsiExpression.EMPTY_ARRAY);
      if (occurrences.length < 2) return;
      PsiElement anchor = CommonJavaRefactoringUtil.getAnchorElementForMultipleExpressions(occurrences, containingMethod);
      if (anchor == null) return;
      PsiElement parent = anchor.getParent();
      if (parent == null) return;
      PsiDeclarationStatement declaration = createDeclaration(methodBody, call, anchor);
      if (declaration == null) return;
      declaration = (PsiDeclarationStatement)parent.addBefore(declaration, anchor);
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(parent.getProject());
      declaration = (PsiDeclarationStatement)codeStyleManager.shortenClassReferences(declaration);
      PsiVariable pathVar = ObjectUtils.tryCast(declaration.getDeclaredElements()[0], PsiVariable.class);
      if (pathVar == null) return;
      String pathVarName = pathVar.getName();
      if (pathVarName == null) return;
      PsiReference[] refs = new PsiReference[occurrences.length];
      for (int i = 0; i < occurrences.length; i++) {
        PsiExpression occurrence = occurrences[i];
        refs[i] = (PsiReference)new CommentTracker().replaceAndRestoreComments(occurrence, pathVarName);
      }
      if (!myIsOnTheFly) return;
      HighlightUtils.showRenameTemplate(containingMethod, pathVar, refs);
    }

    private static @Nullable PsiDeclarationStatement createDeclaration(@NotNull PsiCodeBlock block,
                                                                       @NotNull PsiMethodCallExpression toPathCall,
                                                                       @NotNull PsiElement anchor) {
      PsiClassType type = TypeUtils.getType("java.nio.file.Path", block);
      String varName = getSuggestedName(type, toPathCall, anchor);
      if (varName == null) return null;
      PsiElementFactory elementFactory = PsiElementFactory.getInstance(block.getProject());
      return elementFactory.createVariableDeclarationStatement(varName, type, toPathCall);
    }

    private static @Nullable String getSuggestedName(@NotNull PsiType type,
                                                     @NotNull PsiExpression initializer,
                                                     @NotNull PsiElement anchor) {
      SuggestedNameInfo nameInfo = CommonJavaRefactoringUtil.getSuggestedName(type, initializer, anchor);
      String[] names = nameInfo.names;
      return names.length == 0 ? null : names[0];
    }
  }
}
