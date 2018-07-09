// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

public class OverwrittenKeyInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher SET_ADD =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SET, "add").parameterCount(1);
  private static final CallMatcher MAP_PUT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "put").parameterCount(2);
  private static final CallMatcher SET_OF =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_SET, "of");
  private static final CallMatcher MAP_OF =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "of");
  private static final CallMatcher MAP_OF_ENTRIES =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "ofEntries");
  private static final CallMatcher MAP_ENTRY =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP, "entry");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new OverwrittenKeyVisitor(holder);
  }

  private static class OverwrittenKeyVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    public OverwrittenKeyVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitCodeBlock(PsiCodeBlock block) {
      PsiExpressionStatement statement = PsiTreeUtil.getChildOfType(block, PsiExpressionStatement.class);
      while (statement != null) {
        PsiMethodCallExpression call = tryCast(statement.getExpression(), PsiMethodCallExpression.class);
        if (SET_ADD.test(call)) {
          statement = processCallSequence(call, statement, SET_ADD, InspectionsBundle.message("inspection.overwritten.key.set.message"));
        }
        else if (MAP_PUT.test(call)) {
          statement = processCallSequence(call, statement, MAP_PUT, InspectionsBundle.message("inspection.overwritten.key.map.message"));
        }
        statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiExpressionStatement.class);
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression call) {
      if(SET_OF.test(call)) {
        findDuplicates(call.getArgumentList().getExpressions(), InspectionsBundle.message("inspection.overwritten.key.set.message"));
      }
      else if (MAP_OF.test(call)) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        findDuplicates(IntStreamEx.range(0, args.length, 2).elements(args).toArray(PsiExpression[]::new),
                       InspectionsBundle.message("inspection.overwritten.key.map.message"));
      }
      else if (MAP_OF_ENTRIES.test(call)) {
        PsiExpression[] keys = StreamEx.of(call.getArgumentList().getExpressions()).map(PsiUtil::skipParenthesizedExprDown)
          .select(PsiMethodCallExpression.class).filter(MAP_ENTRY).map(entryCall -> entryCall.getArgumentList().getExpressions()[0])
          .toArray(PsiExpression[]::new);
        findDuplicates(keys, InspectionsBundle.message("inspection.overwritten.key.map.message"));
      }
    }

    private void findDuplicates(PsiExpression[] expressions, String message) {
      Map<Object, List<PsiExpression>> groups = StreamEx.of(expressions).mapToEntry(OverwrittenKeyVisitor::getKey, Function.identity())
        .nonNullKeys().grouping();
      registerDuplicates(message, groups);
    }

    /**
     * @param call      a starting method call
     * @param statement a statement containing the call
     * @param myMatcher a matcher to use to match subsequent calls
     * @param message   a message to use in warning
     * @return last processed statement
     */
    private PsiExpressionStatement processCallSequence(PsiMethodCallExpression call,
                                                       PsiExpressionStatement statement,
                                                       CallMatcher myMatcher,
                                                       String message) {
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      Object key = getKey(arg);
      if (key == null) return statement;
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getQualifierOrThis(call.getMethodExpression()));
      if (qualifier == null) return statement;
      PsiVariable qualifierVar =
        qualifier instanceof PsiReferenceExpression ? tryCast(((PsiReferenceExpression)qualifier).resolve(), PsiVariable.class) : null;
      Map<Object, List<PsiExpression>> map = new HashMap<>();
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(arg);
      while (true) {
        PsiExpressionStatement nextStatement =
          tryCast(PsiTreeUtil.getNextSiblingOfType(statement, PsiStatement.class), PsiExpressionStatement.class);
        if (nextStatement == null) break;
        PsiMethodCallExpression nextCall = tryCast(nextStatement.getExpression(), PsiMethodCallExpression.class);
        if (!myMatcher.test(nextCall)) break;
        PsiExpression nextQualifier =
          PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getQualifierOrThis(nextCall.getMethodExpression()));
        if (nextQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, nextQualifier)) break;
        if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, nextCall.getArgumentList())) break;
        PsiExpression nextArg = nextCall.getArgumentList().getExpressions()[0];
        Object nextKey = getKey(nextArg);
        if (nextKey != null) {
          map.computeIfAbsent(nextKey, k -> new ArrayList<>()).add(nextArg);
        }
        statement = nextStatement;
      }
      registerDuplicates(message, map);
      return statement;
    }

    private void registerDuplicates(String message, Map<Object, List<PsiExpression>> map) {
      for (List<PsiExpression> args : map.values()) {
        if (args.size() < 2) continue;
        for (int i = 0; i < args.size(); i++) {
          PsiExpression arg = args.get(i);
          LocalQuickFix fix = null;
          if (myHolder.isOnTheFly()) {
            PsiExpression nextArg = args.get((i + 1) % args.size());
            fix = new NavigateToDuplicateFix(nextArg);
          }
          myHolder.registerProblem(arg, message, fix);
        }
      }
    }

    private static Object getKey(PsiExpression key) {
      Object constant = ExpressionUtils.computeConstantExpression(key);
      if (constant != null) {
        return constant;
      }
      if (key instanceof PsiReferenceExpression) {
        PsiField field = tryCast(((PsiReferenceExpression)key).resolve(), PsiField.class);
        if (field instanceof PsiEnumConstant ||
            field != null && field.hasModifierProperty(PsiModifier.FINAL) && field.hasModifierProperty(PsiModifier.STATIC)) {
          return field;
        }
      }
      return null;
    }
  }

  private static class NavigateToDuplicateFix implements LocalQuickFix {
    private final SmartPsiElementPointer<PsiExpression> myPointer;

    public NavigateToDuplicateFix(PsiExpression arg) {
      myPointer = SmartPointerManager.getInstance(arg.getProject()).createSmartPsiElementPointer(arg);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("navigate.to.duplicate.fix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression element = myPointer.getElement();
      if (element == null) return;
      PsiFile file = element.getContainingFile();
      if (file == null) return;
      int offset = element.getTextRange().getStartOffset();
      PsiNavigationSupport.getInstance().createNavigatable(project, file.getVirtualFile(), offset).navigate(true);
    }
  }
}
