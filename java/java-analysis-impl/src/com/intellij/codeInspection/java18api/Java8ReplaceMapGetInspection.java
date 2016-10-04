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

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
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
        } else if(thenBranch instanceof PsiBlockStatement) {
          /*
            value = map.get(key);
            if(value == null) {
              value = ...
              map.put(key, value);
            }
           */
          if (!mySuggestMapComputeIfAbsent) return;
          PsiExpression key = getArguments[0];
          PsiStatement[] statements = ((PsiBlockStatement)thenBranch).getCodeBlock().getStatements();
          if(statements.length != 2) return;
          assignment = ExpressionUtils.getAssignment(statements[0]);
          if(assignment == null) return;
          PsiExpression lambdaCandidate = assignment.getRExpression();
          if (lambdaCandidate == null ||
              !equivalence.expressionsAreEquivalent(assignment.getLExpression(), value) ||
              !(statements[1] instanceof PsiExpressionStatement)) {
            return;
          }
          PsiExpression expression = ((PsiExpressionStatement)statements[1]).getExpression();
          if(!(expression instanceof PsiMethodCallExpression)) return;
          PsiMethodCallExpression putCall = (PsiMethodCallExpression)expression;
          if(!Java8CollectionsApiInspection.isJavaUtilMapMethodWithName(putCall, "put")) return;
          PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
          if (putArguments.length != 2 ||
              !equivalence.expressionsAreEquivalent(putCall.getMethodExpression().getQualifierExpression(),
                                                    getCall.getMethodExpression().getQualifierExpression()) ||
              !equivalence.expressionsAreEquivalent(key, putArguments[0]) ||
              !equivalence.expressionsAreEquivalent(value, putArguments[1])) {
            return;
          }
          if(!ExceptionUtil.getThrownCheckedExceptions(lambdaCandidate).isEmpty()) return;
          if(!PsiTreeUtil.processElements(lambdaCandidate, e -> {
            if(!(e instanceof PsiReferenceExpression)) return true;
            PsiElement element = ((PsiReferenceExpression)e).resolve();
            if(!(element instanceof PsiVariable)) return true;
            return HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
          })) {
            return;
          }
          holder.registerProblem(condition, QuickFixBundle.message("java.8.replace.map.get.inspection.description"),
                                 new ReplaceGetNullCheck("computeIfAbsent"));
        }
      }
    };
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
        if(lastDeclaration instanceof PsiLocalVariable && lastDeclaration == target.resolve()) {
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
      if(thenBranch instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)thenBranch).getExpression();
        if (!(expression instanceof PsiAssignmentExpression)) return;
        PsiExpression defaultValue = ((PsiAssignmentExpression)expression).getRExpression();
        if (!ExpressionUtils.isSimpleExpression(defaultValue)) return;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
        nameElement.replace(factory.createIdentifier("getOrDefault"));
        getCall.getArgumentList().add(defaultValue);
      } else if(thenBranch instanceof PsiBlockStatement) {
        PsiStatement[] statements = ((PsiBlockStatement)thenBranch).getCodeBlock().getStatements();
        if(statements.length != 2) return;
        PsiAssignmentExpression assignment = ExpressionUtils.getAssignment(statements[0]);
        if(assignment == null) return;
        PsiExpression lambdaCandidate = assignment.getRExpression();
        if(lambdaCandidate == null) return;
        if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
        nameElement.replace(factory.createIdentifier("computeIfAbsent"));
        String varName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName("k", lambdaCandidate, true);
        PsiExpression lambda = factory.createExpressionFromText(varName + " -> " + lambdaCandidate.getText(), lambdaCandidate);
        getCall.getArgumentList().add(lambda);
      } else return;
      ifStatement.delete();
      CodeStyleManager.getInstance(project).reformat(statement);
      comments.forEach(comment -> statement.getParent().addBefore(comment, statement));
    }
  }
}