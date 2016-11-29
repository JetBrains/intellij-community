/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.java18api;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Tagir Valeev
 */
public class Java8ReplaceMapGetInspection extends BaseJavaBatchLocalInspectionTool {

  public boolean mySuggestMapGetOrDefault = true;
  public boolean mySuggestMapComputeIfAbsent = true;
  public boolean mySuggestMapPutIfAbsent = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Suggest conversion to Map.computeIfAbsent", "mySuggestMapComputeIfAbsent");
    panel.addCheckbox("Suggest conversion to Map.getOrDefault", "mySuggestMapGetOrDefault");
    panel.addCheckbox("Suggest conversion to Map.putIfAbsent", "mySuggestMapPutIfAbsent");
    return panel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        if(statement.getElseBranch() != null) return;
        PsiExpression condition = statement.getCondition();
        PsiReferenceExpression value = getReferenceComparedWithNull(condition);
        if (value == null) return;
        PsiElement previous = PsiTreeUtil.skipSiblingsBackward(statement, PsiWhiteSpace.class, PsiComment.class);
        PsiMethodCallExpression getCall = tryExtractMapGetCall(value, previous);
        if(getCall == null) return;
        PsiExpression[] getArguments = getCall.getArgumentList().getExpressions();
        if(getArguments.length != 1) return;
        PsiStatement thenBranch = ControlFlowUtils.stripBraces(statement.getThenBranch());
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(thenBranch);
        EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
        if(assignment != null) {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
            }
           */
          if (!mySuggestMapGetOrDefault) return;
          if (ExpressionUtils.isSimpleExpression(assignment.getRExpression()) &&
              equivalence.expressionsAreEquivalent(assignment.getLExpression(), value) &&
              !equivalence.expressionsAreEquivalent(assignment.getRExpression(), value)) {
            holder.registerProblem(condition, QuickFixBundle.message("java.8.replace.map.get.inspection.description"),
                                   new ReplaceGetNullCheck("getOrDefault"));
          }
        } else {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
              map.put(key, value);
            }
           */
          PsiExpression key = getArguments[0];
          PsiExpression mapExpression = getCall.getMethodExpression().getQualifierExpression();
          PsiExpression lambdaCandidate = extractLambdaCandidate(thenBranch, mapExpression, key, value);
          if (lambdaCandidate != null && mySuggestMapComputeIfAbsent) {
            holder.registerProblem(condition, QuickFixBundle.message("java.8.replace.map.get.inspection.description"),
                                   new ReplaceGetNullCheck("computeIfAbsent"));
          }
          if (lambdaCandidate == null && mySuggestMapPutIfAbsent) {
            PsiExpression expression = extractPutValue(thenBranch, mapExpression, key);
            if(ExpressionUtils.isSimpleExpression(expression) && !equivalence.expressionsAreEquivalent(expression, value)) {
              holder.registerProblem(condition, QuickFixBundle.message("java.8.replace.map.get.inspection.description"),
                                     new ReplaceGetNullCheck("putIfAbsent"));
            }
          }
        }
      }
    };
  }

  @Nullable
  static PsiExpression extractLambdaCandidate(PsiStatement statement, PsiExpression mapExpression,
                                              PsiExpression keyExpression, PsiReferenceExpression valueExpression) {
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    PsiAssignmentExpression assignment;
    PsiExpression putValue = extractPutValue(statement, mapExpression, keyExpression);
    if(putValue != null) {
      // like map.put(key, val = new ArrayList<>());
      assignment = ExpressionUtils.getAssignment(putValue);
    }
    else {
      if (!(statement instanceof PsiBlockStatement)) return null;
      // like val = new ArrayList<>(); map.put(key, val);
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      if (statements.length != 2) return null;
      putValue = extractPutValue(statements[1], mapExpression, keyExpression);
      if (!equivalence.expressionsAreEquivalent(valueExpression, putValue)) return null;
      assignment = ExpressionUtils.getAssignment(statements[0]);
    }
    if (assignment == null) return null;
    PsiExpression lambdaCandidate = assignment.getRExpression();
    if (lambdaCandidate == null || !equivalence.expressionsAreEquivalent(assignment.getLExpression(), valueExpression)) return null;
    if (!LambdaGenerationUtil.canBeUncheckedLambda(lambdaCandidate)) return null;
    return lambdaCandidate;
  }

  @Contract("null -> null")
  @Nullable
  private static PsiMethodCallExpression extractPutCall(PsiStatement statement) {
    if(!(statement instanceof PsiExpressionStatement)) return null;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    if (!(expression instanceof PsiMethodCallExpression)) return null;
    PsiMethodCallExpression putCall = (PsiMethodCallExpression)expression;
    if (!Java8CollectionsApiInspection.isJavaUtilMapMethodWithName(putCall, "put")) return null;
    return putCall;
  }

  @Nullable
  private static PsiExpression extractPutValue(PsiStatement statement, PsiExpression mapExpression, PsiExpression keyExpression) {
    PsiMethodCallExpression putCall = extractPutCall(statement);
    if (putCall == null) return null;
    PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    return putArguments.length == 2 &&
           equivalence.expressionsAreEquivalent(putCall.getMethodExpression().getQualifierExpression(), mapExpression) &&
           equivalence.expressionsAreEquivalent(keyExpression, putArguments[0]) ? putArguments[1] : null;
  }

  @Contract("null -> null")
  @Nullable
  private static PsiReferenceExpression getReferenceComparedWithNull(PsiExpression condition) {
    if(!(condition instanceof PsiBinaryExpression)) return null;
    PsiBinaryExpression binOp = (PsiBinaryExpression)condition;
    if(!binOp.getOperationTokenType().equals(JavaTokenType.EQEQ)) return null;
    PsiExpression value = Java8CollectionsApiInspection.getValueComparedWithNull(binOp);
    if(!(value instanceof PsiReferenceExpression)) return null;
    return (PsiReferenceExpression)value;
  }

  @Nullable
  @Contract("_, null -> null")
  static PsiMethodCallExpression tryExtractMapGetCall(PsiReferenceExpression target, PsiElement element) {
    if(element instanceof PsiDeclarationStatement) {
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)element;
      PsiElement[] elements = declaration.getDeclaredElements();
      if(elements.length > 0) {
        PsiElement lastDeclaration = elements[elements.length - 1];
        if(lastDeclaration instanceof PsiLocalVariable && target.isReferenceTo(lastDeclaration)) {
          PsiLocalVariable var = (PsiLocalVariable)lastDeclaration;
          PsiExpression initializer = PsiUtil.skipParenthesizedExprDown(var.getInitializer());
          if (initializer instanceof PsiMethodCallExpression &&
              Java8CollectionsApiInspection.isJavaUtilMapMethodWithName((PsiMethodCallExpression)initializer, "get")) {
            return (PsiMethodCallExpression)initializer;
          }
        }
      }
    }
    PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(element);
    if(assignment != null) {
      PsiExpression lValue = assignment.getLExpression();
      if (lValue instanceof PsiReferenceExpression &&
          EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(target, lValue)) {
        PsiExpression rValue = PsiUtil.skipParenthesizedExprDown(assignment.getRExpression());
        if (rValue instanceof PsiMethodCallExpression &&
            Java8CollectionsApiInspection.isJavaUtilMapMethodWithName((PsiMethodCallExpression)rValue, "get")) {
          return (PsiMethodCallExpression)rValue;
        }
      }
    }
    return null;
  }

  private static class ReplaceGetNullCheck implements LocalQuickFix {
    private final String myMethodName;

    ReplaceGetNullCheck(String methodName) {
      myMethodName = methodName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return QuickFixBundle.message("java.8.collections.api.inspection.fix.text", myMethodName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.replace.map.get.inspection.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
      if(ifStatement == null) return;
      PsiReferenceExpression value = getReferenceComparedWithNull(ifStatement.getCondition());
      if(value == null) return;
      PsiElement statement = PsiTreeUtil.skipSiblingsBackward(ifStatement, PsiWhiteSpace.class, PsiComment.class);
      PsiMethodCallExpression getCall = tryExtractMapGetCall(value, statement);
      if(getCall == null || !Java8CollectionsApiInspection.isJavaUtilMapMethodWithName(getCall, "get")) return;
      PsiExpression[] args = getCall.getArgumentList().getExpressions();
      if(args.length != 1) return;
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(thenBranch);
      CommentTracker ct = new CommentTracker();
      PsiReferenceExpression methodExpression = getCall.getMethodExpression();
      if(assignment != null) {
        PsiExpression defaultValue = assignment.getRExpression();
        if (!ExpressionUtils.isSimpleExpression(defaultValue)) return;
        methodExpression.handleElementRename(myMethodName);
        getCall.getArgumentList().add(ct.markUnchanged(defaultValue));
      } else {
        PsiExpression lambdaCandidate = extractLambdaCandidate(thenBranch, methodExpression.getQualifierExpression(), args[0], value);
        if (lambdaCandidate == null) {
          PsiExpression valueExpression = extractPutValue(thenBranch, methodExpression.getQualifierExpression(), args[0]);
          if(ExpressionUtils.isSimpleExpression(valueExpression)) {
            methodExpression.handleElementRename(myMethodName);
            getCall.getArgumentList().add(ct.markUnchanged(valueExpression));
          }
        } else {
          methodExpression.handleElementRename(myMethodName);
          String varName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("k", lambdaCandidate, true);
          PsiExpression lambda = factory.createExpressionFromText(varName + " -> " + ct.text(lambdaCandidate), lambdaCandidate);
          getCall.getArgumentList().add(lambda);
        }
      }
      ct.deleteAndRestoreComments(ifStatement);
      CodeStyleManager.getInstance(project).reformat(statement);
    }
  }
}