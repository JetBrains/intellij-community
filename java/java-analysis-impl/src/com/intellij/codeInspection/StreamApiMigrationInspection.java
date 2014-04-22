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
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
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
  
                  if (effectivelyFinal[0]) {
                    if (isCollectCall(body)) {
                      holder.registerProblem(iteratedValue, "Can be replaced with collect call",
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithCollectCallFix());
                    } else if (!isTrivial(body, statement.getIterationParameter(), iteratedValueType)) {
                      holder.registerProblem(iteratedValue, "Can be replaced with foreach call",
                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new ReplaceWithForeachCallFix());
                    }
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

  private static boolean isCollectCall(PsiStatement body) {
    final PsiIfStatement ifStatement = extractIfStatement(body);
    final PsiMethodCallExpression methodCallExpression = extractAddCall(body);
    if (methodCallExpression != null) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiClass qualifierClass = null;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        qualifierClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
      } else if (qualifierExpression == null) {
        final PsiClass enclosingClass = PsiTreeUtil.getParentOfType(body, PsiClass.class);
        if (PsiUtil.getEnclosingStaticElement(body, enclosingClass) == null) {
          qualifierClass = enclosingClass;
        }
      }

      if (qualifierClass != null && 
          InheritanceUtil.isInheritor(qualifierClass, false, CommonClassNames.JAVA_UTIL_COLLECTION)) {

        if (ifStatement != null) {
          final PsiExpression condition = ifStatement.getCondition();
          if (condition != null && isConditionDependsOnUpdatedCollections(condition, qualifierExpression)) return false;
        }

        final PsiElement resolve = methodExpression.resolve();
        if (resolve instanceof PsiMethod &&
            "add".equals(((PsiMethod)resolve).getName()) &&
            ((PsiMethod)resolve).getParameterList().getParametersCount() == 1) {
          final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
          if (args.length == 1) {
            if (args[0] instanceof PsiCallExpression) {
              final PsiMethod method = ((PsiCallExpression)args[0]).resolveMethod();
              return method != null && !method.hasTypeParameters();
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isConditionDependsOnUpdatedCollections(PsiExpression condition,
                                                                PsiExpression qualifierExpression) {
    final PsiElement collection = qualifierExpression != null
                                  ? ((PsiReferenceExpression)qualifierExpression).resolve()
                                  : null;
    final boolean[] dependsOnCollection = {false};
    condition.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (collection != null && collection == expression.resolve()) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiExpression callQualifier = expression.getMethodExpression().getQualifierExpression();
        if (collection == callQualifier) {
          dependsOnCollection[0] = true;
        }

        if (collection == null && (callQualifier instanceof PsiThisExpression && ((PsiThisExpression)callQualifier).getQualifier() == null || 
                                   callQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)callQualifier).getQualifier() == null)) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (collection == null && expression.getQualifier() == null && expression.getParent() instanceof PsiExpressionList) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {}
    });

    return dependsOnCollection[0];
  }

  private static boolean isTrivial(PsiStatement body, PsiParameter parameter, PsiType iteratedValueType) {
    final PsiIfStatement ifStatement = extractIfStatement(body);
    //stream
    if (ifStatement != null &&
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
          final Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(body, PsiComment.class);

          final PsiElement parent = foreachStatement.getParent();
          for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
            parent.addBefore(comment, foreachStatement);
          }

          final PsiParameter parameter = foreachStatement.getIterationParameter();
          final PsiIfStatement ifStmt = extractIfStatement(body);

          String foreEachText = wrapInBlock(body);
          String iterated = iteratedValue instanceof PsiCallExpression || 
                            iteratedValue instanceof PsiReferenceExpression ||
                            iteratedValue instanceof PsiQualifiedExpression ||
                            iteratedValue instanceof PsiParenthesizedExpression ? iteratedValue.getText() : "(" + iteratedValue.getText() + ")";
          if (ifStmt != null) {
            final PsiExpression condition = ifStmt.getCondition();
            if (condition != null) {
              final PsiStatement thenBranch = ifStmt.getThenBranch();
              LOG.assertTrue(thenBranch != null);
              if (InheritanceUtil.isInheritor(iteratedValue.getType(), CommonClassNames.JAVA_UTIL_COLLECTION)) {
                body = thenBranch;
                foreEachText = wrapInBlock(thenBranch);
                iterated += ".stream().filter(" + parameter.getName() + " -> " + condition.getText() +")";
              }
            }
          }

          final PsiParameter[] parameters = {parameter};
          String methodReferenceText = null;
          final PsiCallExpression callExpression = LambdaCanBeMethodReferenceInspection.extractMethodCallFromBlock(body);
          if (callExpression != null) {
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiClass consumerClass = psiFacade.findClass("java.util.function.Consumer", GlobalSearchScope.allScope(project));
            final PsiClassType functionalType = consumerClass != null ? psiFacade.getElementFactory().createType(consumerClass, callExpression.getType()) : null;

            final PsiCallExpression toConvertCall = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : body, parameters, functionalType);
            methodReferenceText = LambdaCanBeMethodReferenceInspection.createMethodReferenceText(toConvertCall, functionalType, parameters);
          }
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

    private static String wrapInBlock(PsiStatement body) {

      if (body instanceof PsiExpressionStatement) {
        return ((PsiExpressionStatement)body).getExpression().getText();
      }

      final String bodyText = body.getText();
      if (!(body instanceof PsiBlockStatement)) {
        return "{" + bodyText + "}";
      }
      return bodyText;
    }
  }

  private static class ReplaceWithCollectCallFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with collect";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiForeachStatement foreachStatement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiForeachStatement.class);
      if (foreachStatement != null) {
        PsiStatement body = foreachStatement.getBody();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        if (body != null && iteratedValue != null) {
          final PsiParameter parameter = foreachStatement.getIterationParameter();

          final PsiIfStatement ifStatement = extractIfStatement(body);
          final PsiMethodCallExpression methodCallExpression = extractAddCall(body);
          String iteration = iteratedValue.getText() + ".stream()";
          if (ifStatement != null) {
            final PsiExpression condition = ifStatement.getCondition();
            if (condition != null) {
              iteration += ".filter(" + parameter.getName() + " -> " + condition.getText() +")";
            }
          }
          final PsiExpression mapperCall = methodCallExpression.getArgumentList().getExpressions()[0];
          if (!isIdentityMapping(parameter, mapperCall)) {
            iteration +=".map(";
            final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
            final PsiClass functionClass = psiFacade.findClass("java.util.function.Function", GlobalSearchScope.allScope(project));
            final PsiClassType functionalInterfaceType = functionClass != null ? psiFacade.getElementFactory().createType(functionClass, parameter.getType(), mapperCall.getType()) : null;
            final PsiCallExpression toConvertCall = LambdaCanBeMethodReferenceInspection.canBeMethodReferenceProblem(mapperCall, new PsiParameter[]{parameter}, functionalInterfaceType);
            final String methodReferenceText = LambdaCanBeMethodReferenceInspection.createMethodReferenceText(toConvertCall, functionalInterfaceType, new PsiParameter[]{parameter});
            if (methodReferenceText != null) {
              iteration += methodReferenceText;
            } else {
              iteration += parameter.getName() + " -> " + mapperCall.getText();
            }
            iteration += ")";
          }

          iteration += ".collect(java.util.stream.Collectors.";

          String variableName = null;
          PsiExpression primitiveInitializer = null;
          final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
          if (qualifierExpression instanceof PsiReferenceExpression) {
            final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
            if (resolve instanceof PsiVariable) {
              if (resolve instanceof PsiLocalVariable && foreachStatement.equals(PsiTreeUtil.skipSiblingsForward(resolve.getParent(), PsiWhiteSpace.class))) {
                final PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
                if (initializer instanceof PsiNewExpression) {
                  final PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
                  if (argumentList != null && argumentList.getExpressions().length == 0) {
                    primitiveInitializer = initializer;
                  }
                }
              }
              variableName = ((PsiVariable)resolve).getName() + ".";
            }
          } else if (qualifierExpression == null) {
            variableName = "";
          }

          if (variableName != null) {
            final PsiElement parent = foreachStatement.getParent();
            for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
              parent.addBefore(comment, foreachStatement);
            }
          }

          PsiElement result = null;
          if (primitiveInitializer != null) {
            final PsiType initializerType = primitiveInitializer.getType();
            final PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
            if (rawType != null && rawType.equalsToText(CommonClassNames.JAVA_UTIL_ARRAY_LIST)) {
              iteration += "toList()";
            } else if (rawType != null && rawType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_SET)) {
              iteration += "toSet()";
            } else {
              iteration += "toCollection(() -> " + primitiveInitializer.getText() +")";
            }
            iteration += ")";
            result = primitiveInitializer.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(iteration, foreachStatement));
            foreachStatement.delete();
          } else if (variableName != null){
            iteration += "toList())";
            result = foreachStatement.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(variableName + "addAll(" + iteration +");", foreachStatement));
          }

          if (result != null) {
            result = JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
          }
        }
      }
    }

    private static boolean isIdentityMapping(PsiParameter parameter, PsiExpression mapperCall) {
      return mapperCall instanceof PsiReferenceExpression && ((PsiReferenceExpression)mapperCall).resolve() == parameter;
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
    if (ifStmt != null && ifStmt.getElseBranch() == null && ifStmt.getThenBranch() != null) {
      return ifStmt;
    }
    return null;
  }
  
  private static PsiMethodCallExpression extractAddCall(PsiStatement body) {
    final PsiIfStatement ifStatement = extractIfStatement(body);
    if (ifStatement != null) {
      return extractAddCall(ifStatement.getThenBranch());
    }
    PsiExpressionStatement stmt = null;
    if (body instanceof PsiBlockStatement) {
      final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiExpressionStatement) {
        stmt = (PsiExpressionStatement)statements[0];
      }
    }
    else if (body instanceof PsiExpressionStatement) {
      stmt = (PsiExpressionStatement)body;
    }

    if (stmt != null) {
      final PsiExpression expression = stmt.getExpression();
      if (expression instanceof PsiMethodCallExpression) {
        return (PsiMethodCallExpression)expression;
      }
    }
    return null;
  }
}
