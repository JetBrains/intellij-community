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

import com.intellij.codeInsight.FileModificationService;
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
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Tagir Valeev
 */
public class Java8ReplaceMapGetInspection extends BaseJavaBatchLocalInspectionTool {

  public boolean mySuggestMapGetOrDefault = true;
  public boolean mySuggestMapComputeIfAbsent = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox("Suggest conversion to Map.computeIfAbsent", "mySuggestMapComputeIfAbsent");
    panel.addCheckbox("Suggest conversion to Map.getOrDefault", "mySuggestMapGetOrDefault");
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
              equivalence.expressionsAreEquivalent(assignment.getLExpression(), value)) {
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
          if (!mySuggestMapComputeIfAbsent) return;
          PsiExpression key = getArguments[0];
          PsiExpression mapExpression = getCall.getMethodExpression().getQualifierExpression();
          PsiExpression lambdaCandidate = extractLambdaCandidate(thenBranch, mapExpression, key, value);
          if (lambdaCandidate == null) return;
          holder.registerProblem(condition, QuickFixBundle.message("java.8.replace.map.get.inspection.description"),
                                 new ReplaceGetNullCheck("computeIfAbsent"));
        }
      }
    };
  }

  @Nullable
  static PsiExpression extractLambdaCandidate(PsiStatement statement, PsiExpression mapExpression,
                                              PsiExpression keyExpression, PsiReferenceExpression valueExpression) {
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    PsiAssignmentExpression assignment;
    PsiMethodCallExpression putCall = extractPutCall(statement);
    if(putCall != null) {
      // like map.put(key, val = new ArrayList<>());
      PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
      if (putArguments.length != 2 ||
          !equivalence.expressionsAreEquivalent(putCall.getMethodExpression().getQualifierExpression(), mapExpression) ||
          !equivalence.expressionsAreEquivalent(keyExpression, putArguments[0])) {
        return null;
      }
      assignment = ExpressionUtils.getAssignment(putArguments[1]);
    }
    else {
      if (!(statement instanceof PsiBlockStatement)) return null;
      // like val = new ArrayList<>(); map.put(key, val);
      PsiStatement[] statements = ((PsiBlockStatement)statement).getCodeBlock().getStatements();
      putCall = extractPutCall(statements[1]);
      if (putCall == null) return null;
      PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
      if (putArguments.length != 2 ||
          !equivalence.expressionsAreEquivalent(putCall.getMethodExpression().getQualifierExpression(), mapExpression) ||
          !equivalence.expressionsAreEquivalent(keyExpression, putArguments[0]) ||
          !equivalence.expressionsAreEquivalent(valueExpression, putArguments[1])) {
        return null;
      }
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
      PsiElement nameElement = getCall.getMethodExpression().getReferenceNameElement();
      if(nameElement == null) return;
      PsiExpression[] args = getCall.getArgumentList().getExpressions();
      if(args.length != 1) return;
      PsiStatement thenBranch = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
      Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(ifStatement, PsiComment.class),
                                                          comment -> (PsiComment)comment.copy());
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(thenBranch);
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      if(assignment != null) {
        PsiExpression defaultValue = assignment.getRExpression();
        if (!ExpressionUtils.isSimpleExpression(defaultValue)) return;
        nameElement.replace(factory.createIdentifier("getOrDefault"));
        getCall.getArgumentList().add(defaultValue);
      } else {
        PsiExpression lambdaCandidate =
          extractLambdaCandidate(thenBranch, getCall.getMethodExpression().getQualifierExpression(), args[0], value);
        if (lambdaCandidate == null) return;
        nameElement.replace(factory.createIdentifier("computeIfAbsent"));
        String varName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("k", lambdaCandidate, true);
        PsiExpression lambda = factory.createExpressionFromText(varName + " -> " + lambdaCandidate.getText(), lambdaCandidate);
        getCall.getArgumentList().add(lambda);
      }
      ifStatement.delete();
      CodeStyleManager.getInstance(project).reformat(statement);
      comments.forEach(comment -> statement.getParent().addBefore(comment, statement));
    }
  }
}