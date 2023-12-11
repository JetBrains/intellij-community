// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.util.ObjectUtils.tryCast;

public final class OverwrittenKeyInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher SET_ADD =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_SET, "add").parameterCount(1);
  private static final CallMatcher MAP_PUT =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_MAP, "put").parameterCount(2);
  private static final CallMatcher SET_OF =
    CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_SET, "of"),
      CallMatcher.staticCall("java.util.EnumSet", "of"),
      CallMatcher.staticCall("com.google.common.collect.ImmutableSet", "of"));
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

    OverwrittenKeyVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitCodeBlock(@NotNull PsiCodeBlock block) {
      PsiExpressionStatement statement = PsiTreeUtil.getChildOfType(block, PsiExpressionStatement.class);
      while (statement != null) {
        PsiExpression expression = statement.getExpression();
        PsiMethodCallExpression call = tryCast(expression, PsiMethodCallExpression.class);
        if (SET_ADD.test(call)) {
          statement = processCallSequence(call, statement, SET_ADD, JavaBundle.message("inspection.overwritten.key.set.message"));
        }
        else if (MAP_PUT.test(call)) {
          statement = processCallSequence(call, statement, MAP_PUT, JavaBundle.message("inspection.overwritten.key.map.message"));
        }
        if (expression instanceof PsiAssignmentExpression) {
          statement = processArraySequence((PsiAssignmentExpression)expression, statement,
                                           JavaBundle.message("inspection.overwritten.key.array.message"));
        }
        statement = PsiTreeUtil.getNextSiblingOfType(statement, PsiExpressionStatement.class);
      }
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
      if(SET_OF.test(call)) {
        findDuplicates(call.getArgumentList().getExpressions(), JavaBundle.message("inspection.overwritten.key.set.message"));
      }
      else if (MAP_OF.test(call)) {
        PsiExpression[] args = call.getArgumentList().getExpressions();
        findDuplicates(IntStreamEx.range(0, args.length, 2).elements(args).toArray(PsiExpression[]::new),
                       JavaBundle.message("inspection.overwritten.key.map.message"));
      }
      else if (MAP_OF_ENTRIES.test(call)) {
        PsiExpression[] keys = StreamEx.of(call.getArgumentList().getExpressions()).map(PsiUtil::skipParenthesizedExprDown)
          .select(PsiMethodCallExpression.class).filter(MAP_ENTRY).map(entryCall -> entryCall.getArgumentList().getExpressions()[0])
          .toArray(PsiExpression[]::new);
        findDuplicates(keys, JavaBundle.message("inspection.overwritten.key.map.message"));
      }
    }

    private void findDuplicates(PsiExpression[] expressions, @InspectionMessage String message) {
      Map<Object, List<PsiExpression>> groups = StreamEx.of(expressions).mapToEntry(OverwrittenKeyVisitor::getKey, Function.identity())
        .nonNullKeys().grouping();
      registerDuplicates(message, groups);
    }

    private @NotNull PsiExpressionStatement processArraySequence(PsiAssignmentExpression assignment,
                                                        PsiExpressionStatement statement,
                                                        @InspectionMessage String message) {
      if (!assignment.getOperationTokenType().equals(JavaTokenType.EQ)) return statement;
      var arrayAccessExpression = tryCast(assignment.getLExpression(), PsiArrayAccessExpression.class);
      if (arrayAccessExpression == null) return statement;
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(arrayAccessExpression.getArrayExpression());
      PsiExpression arg = arrayAccessExpression.getIndexExpression();
      Object key = getKey(arg);
      if (key == null) return statement;
      if (qualifier == null) return statement;
      PsiVariable qualifierVar =
        qualifier instanceof PsiReferenceExpression ? tryCast(((PsiReferenceExpression)qualifier).resolve(), PsiVariable.class) : null;
      if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, assignment.getRExpression())) return statement;
      Map<Object, List<PsiExpression>> map = new HashMap<>();
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(arg);
      while (true) {
        PsiExpressionStatement nextStatement = findNextStatement(statement);
        if (nextStatement == null) break;
        PsiAssignmentExpression nextExpression = tryCast(nextStatement.getExpression(), PsiAssignmentExpression.class);
        if (nextExpression == null || !nextExpression.getOperationTokenType().equals(JavaTokenType.EQ)) break;
        var nextArrayAccess = tryCast(nextExpression.getLExpression(), PsiArrayAccessExpression.class);
        if (nextArrayAccess == null) break;
        PsiExpression nextQualifier = PsiUtil.skipParenthesizedExprDown(nextArrayAccess.getArrayExpression());
        if (nextQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, nextQualifier)) break;
        if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, nextExpression.getRExpression())) break;
        PsiExpression nextArg = nextArrayAccess.getIndexExpression();
        Object nextKey = getKey(nextArg);
        if (nextKey != null) {
          map.computeIfAbsent(nextKey, k -> new ArrayList<>()).add(nextArg);
        }
        statement = nextStatement;
      }
      registerDuplicates(message, map);
      return statement;
    }

    /**
     * @param call      a starting method call
     * @param statement a statement containing the call
     * @param myMatcher a matcher to use to match subsequent calls
     * @param message   a message to use in warning
     * @return last processed statement
     */
    private @NotNull PsiExpressionStatement processCallSequence(PsiMethodCallExpression call,
                                                       PsiExpressionStatement statement,
                                                       CallMatcher myMatcher,
                                                       @InspectionMessage String message) {
      PsiExpression arg = call.getArgumentList().getExpressions()[0];
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(call.getMethodExpression()));
      Object key = getKey(arg);
      if (key == null) return statement;
      if (qualifier == null) return statement;
      PsiVariable qualifierVar =
        qualifier instanceof PsiReferenceExpression ? tryCast(((PsiReferenceExpression)qualifier).resolve(), PsiVariable.class) : null;
      if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, call.getArgumentList())) return statement;
      Map<Object, List<PsiExpression>> map = new HashMap<>();
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(arg);
      while (true) {
        PsiExpressionStatement nextStatement = findNextStatement(statement);
        if (nextStatement == null) break;
        PsiMethodCallExpression nextCall = tryCast(nextStatement.getExpression(), PsiMethodCallExpression.class);
        if (!myMatcher.test(nextCall)) break;
        PsiExpression nextQualifier =
          PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(nextCall.getMethodExpression()));
        if (nextQualifier == null || !PsiEquivalenceUtil.areElementsEquivalent(qualifier, nextQualifier)) break;
        if (qualifierVar != null && VariableAccessUtils.variableIsUsed(qualifierVar, nextCall.getArgumentList())) break;
        PsiExpression nextArg = ArrayUtil.getFirstElement(nextCall.getArgumentList().getExpressions());
        Object nextKey = getKey(nextArg);
        if (nextKey != null) {
          map.computeIfAbsent(nextKey, k -> new ArrayList<>()).add(nextArg);
        }
        statement = nextStatement;
      }
      registerDuplicates(message, map);
      return statement;
    }

    private static @Nullable PsiExpressionStatement findNextStatement(@NotNull PsiExpressionStatement statement) {
      for (PsiElement child = statement.getNextSibling(); child != null; child = child.getNextSibling()) {
        if (child instanceof PsiStatement) {
          if (child instanceof PsiSwitchLabelStatement) continue;
          return tryCast(child, PsiExpressionStatement.class);
        }
      }
      return null;
    }

    private void registerDuplicates(@InspectionMessage String message, Map<Object, List<PsiExpression>> map) {
      for (List<PsiExpression> args : map.values()) {
        if (args.size() < 2) continue;
        for (int i = 0; i < args.size(); i++) {
          PsiExpression arg = args.get(i);
          PsiExpression nextArg = args.get((i + 1) % args.size());
          LocalQuickFix fix = new NavigateToDuplicateFix(nextArg);
          myHolder.registerProblem(arg, message, fix);
        }
      }
    }

    @Contract("null -> null")
    private static Object getKey(PsiExpression key) {
      if (key == null) return null;
      key = PsiUtil.skipParenthesizedExprDown(key);
      Object constant = ExpressionUtils.computeConstantExpression(key);
      if (constant != null) {
        return constant;
      }
      if (key instanceof PsiReferenceExpression) {
        PsiVariable var = tryCast(((PsiReferenceExpression)key).resolve(), PsiVariable.class);
        if (var instanceof PsiEnumConstant) return var;
        if (var != null) {
          if (var.hasModifierProperty(PsiModifier.FINAL) &&
              (var.hasModifierProperty(PsiModifier.STATIC) || ExpressionUtil.isEffectivelyUnqualified((PsiReferenceExpression)key))) {
            return var;
          }
          if (PsiUtil.isJvmLocalVariable(var)) {
            PsiElement scope = PsiUtil.getVariableCodeBlock(var, null);
            if (scope != null && HighlightControlFlowUtil.isEffectivelyFinal(var, scope, null)) {
              return var;
            }
          }
        }
      }
      return null;
    }
  }

  private static class NavigateToDuplicateFix extends ModCommandQuickFix {
    private final SmartPsiElementPointer<PsiExpression> myPointer;

    NavigateToDuplicateFix(PsiExpression arg) {
      myPointer = SmartPointerManager.getInstance(arg.getProject()).createSmartPsiElementPointer(arg);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("navigate.to.duplicate.fix");
    }

    @Override
    public @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiExpression element = myPointer.getElement();
      if (element == null) return ModCommand.nop();
      return ModCommand.select(element);
    }
  }
}
