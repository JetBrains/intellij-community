/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * User: anna
 */
public class StreamApiMigrationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + StreamApiMigrationInspection.class.getName());

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.LANGUAGE_LEVEL_SPECIFIC_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "foreach loop can be collapsed with stream api";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "Convert2streamapi";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitForeachStatement(PsiForeachStatement statement) {
        super.visitForeachStatement(statement);
        if (PsiUtil.getLanguageLevel(statement).isAtLeast(LanguageLevel.JDK_1_8)) {
          final PsiExpression iteratedValue = statement.getIteratedValue();
          final PsiStatement body = statement.getBody();
          if (iteratedValue != null && body != null) {
            final PsiType iteratedValueType = iteratedValue.getType();
            if (InheritanceUtil.isInheritor(iteratedValueType, CommonClassNames.JAVA_LANG_ITERABLE)) {
              final PsiClass iteratorClass = PsiUtil.resolveClassInType(iteratedValueType);
              LOG.assertTrue(iteratorClass != null);
              try {
                final ControlFlow controlFlow = ControlFlowFactory.getInstance(holder.getProject())
                  .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
                int startOffset = controlFlow.getStartOffset(body);
                int endOffset = controlFlow.getEndOffset(body);
                final Collection<PsiStatement> exitPoints = ControlFlowUtil
                  .findExitPointsAndStatements(controlFlow, startOffset, endOffset, new IntArrayList(), PsiContinueStatement.class,
                                               PsiBreakStatement.class, PsiReturnStatement.class, PsiThrowStatement.class);
                if (exitPoints.isEmpty()) {
  
                  final boolean[] effectivelyFinal = new boolean[] {true};
                  body.accept(new JavaRecursiveElementWalkingVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {
                      if (!effectivelyFinal[0]) return;
                      super.visitElement(element);
                    }
  
                    @Override
                    public void visitReferenceExpression(PsiReferenceExpression expression) {
                      if (!effectivelyFinal[0]) return;
                      super.visitReferenceExpression(expression);
                      final PsiElement resolve = expression.resolve();
                      if (resolve instanceof PsiVariable && !(resolve instanceof PsiField)) {
                        effectivelyFinal[0] = HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)resolve, body, expression);
                      }
                    }
                  });
  
                  if (effectivelyFinal[0] && !isTrivial(body, statement.getIterationParameter(), iteratedValueType)) {
                    holder.registerProblem(iteratedValue, "Can be replaced with foreach call",
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithForeachCallFix());
                  }
                }
              }
              catch (AnalysisCanceledException ignored) {
              }
            }
          }
        }
      }
    };
  }

  private static boolean isTrivial(PsiStatement body, PsiParameter parameter, PsiType iteratedValueType) {
    final PsiIfStatement ifStatement = extractIfStatement(body);
    //stream
    if (ifStatement != null && ifStatement.getElseBranch() == null && ifStatement.getThenBranch() != null && 
        InheritanceUtil.isInheritor(iteratedValueType, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      return false;
    }
    //method reference 
    return LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : body, new PsiParameter[] {parameter}, null) == null;
  }

  private static class ReplaceWithForeachCallFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with forEach";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiForeachStatement foreachStatement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiForeachStatement.class);
      if (foreachStatement != null) {
        PsiStatement body = foreachStatement.getBody();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        if (body != null && iteratedValue != null) {
          final PsiParameter parameter = foreachStatement.getIterationParameter();
          final PsiIfStatement ifStmt = extractIfStatement(body);

          String foreEachText = body.getText();
          String iterated = iteratedValue.getText();
          if (ifStmt != null && ifStmt.getElseBranch() == null) {
            final PsiExpression condition = ifStmt.getCondition();
            if (condition != null) {
              final PsiStatement thenBranch = ifStmt.getThenBranch();
              if (thenBranch != null && InheritanceUtil.isInheritor(iteratedValue.getType(), CommonClassNames.JAVA_UTIL_COLLECTION)) {
                body = thenBranch;
                foreEachText = thenBranch.getText();
                iterated += ".stream().filter(" + parameter.getName() + " -> " + condition.getText() +")";
              }
            }
          }

          final PsiParameter[] parameters = {parameter};
          final PsiCallExpression expression = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : body, parameters, null);
          final String methodReferenceText = LambdaCanBeMethodReferenceInspection.createMethodReferenceText(expression, null, parameters);
          final String lambdaText = parameter.getName() + " -> " + foreEachText;
          final String codeBlock8 = methodReferenceText != null ? methodReferenceText : lambdaText;
          PsiExpressionStatement callStatement = (PsiExpressionStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText(iterated + ".forEach(" + codeBlock8 + ");", foreachStatement);

          callStatement = (PsiExpressionStatement)foreachStatement.replace(callStatement);
          final PsiExpressionList argumentList = ((PsiCallExpression)callStatement.getExpression()).getArgumentList();
          LOG.assertTrue(argumentList != null, callStatement.getText());
          final PsiExpression[] expressions = argumentList.getExpressions();
          LOG.assertTrue(expressions.length == 1);

          if (expressions[0] instanceof PsiLambdaExpression && ((PsiLambdaExpression)expressions[0]).getFunctionalInterfaceType() == null ||
              expressions[0] instanceof PsiMethodReferenceExpression && ((PsiMethodReferenceExpression)expressions[0]).getFunctionalInterfaceType() == null) {
            callStatement = (PsiExpressionStatement)callStatement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(iterated + ".forEach((" + parameter.getText() + ") -> " + foreEachText + ");", callStatement));
          }
        }
      }
    }
  }

  public static PsiIfStatement extractIfStatement(PsiStatement body) {
    PsiIfStatement ifStmt = null;
    if (body instanceof PsiIfStatement) {
      ifStmt = (PsiIfStatement)body;
    } else if (body instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiIfStatement) {
        ifStmt = (PsiIfStatement)statements[0];
      }
    }
    return ifStmt;
  }
}
