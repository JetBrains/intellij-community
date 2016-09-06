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
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: anna
 */
public class StreamApiMigrationInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#" + StreamApiMigrationInspection.class.getName());

  public boolean REPLACE_TRIVIAL_FOREACH;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(
      "Replace trivial foreach statements",
      this,
      "REPLACE_TRIVIAL_FOREACH"
    );
  }

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
    return "foreach loop can be collapsed with Stream API";
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
            final PsiClass iteratorClass = PsiUtil.resolveClassInClassTypeOnly(iteratedValueType);
            PsiClass collectionClass = null;
            final boolean isArray;
            if(iteratedValueType instanceof PsiArrayType) {
              // Do not handle primitive types now
              if(((PsiArrayType)iteratedValueType).getComponentType() instanceof PsiPrimitiveType) return;
              isArray = true;
            } else {
              collectionClass = JavaPsiFacade.getInstance(body.getProject()).findClass(CommonClassNames.JAVA_UTIL_COLLECTION, statement.getResolveScope());
              if (collectionClass != null && InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true)) {
                isArray = false;
              } else return;
            }
            try {
              final ControlFlow controlFlow = ControlFlowFactory.getInstance(holder.getProject())
                .getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
              int startOffset = controlFlow.getStartOffset(body);
              int endOffset = controlFlow.getEndOffset(body);
              final Collection<PsiStatement> exitPoints = ControlFlowUtil
                .findExitPointsAndStatements(controlFlow, startOffset, endOffset, new IntArrayList(), PsiContinueStatement.class,
                                             PsiBreakStatement.class, PsiReturnStatement.class, PsiThrowStatement.class);
              if (exitPoints.isEmpty()) {

                if (ExceptionUtil.getThrownCheckedExceptions(new PsiElement[]{body}).isEmpty()) {
                  TerminalBlock tb = TerminalBlock.from(statement.getIterationParameter(), body);
                  List<Operation> operations = tb.extractOperations();

                  final List<PsiVariable> nonFinalVariables = ControlFlowUtil.getUsedVariables(controlFlow, startOffset, endOffset)
                    .stream().filter(variable -> !HighlightControlFlowUtil.isEffectivelyFinal(variable, body, null))
                    .collect(Collectors.toList());

                  if(getCounter(statement, tb, operations, nonFinalVariables) != null) {
                    holder.registerProblem(iteratedValue, "Can be replaced with count() call",
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           new ReplaceWithCountFix());
                  }
                  if(!nonFinalVariables.isEmpty()) {
                    return;
                  }
                  if ((isArray || !isRawSubstitution(iteratedValueType, collectionClass)) && isCollectCall(tb, operations)) {
                    boolean addAll = operations.isEmpty() && isAddAllCall(tb);
                    holder.registerProblem(iteratedValue, "Can be replaced with " + (addAll ? "addAll call" : "collect call"),
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           addAll ? new ReplaceWithAddAllFix() : new ReplaceWithCollectFix());
                  }
                  // do not replace for(T e : arr) {} with Arrays.stream(arr).forEach(e -> {}) even if flag is set
                  else if (!operations.isEmpty() ||
                           (!isArray && (REPLACE_TRIVIAL_FOREACH || !isTrivial(body, statement.getIterationParameter())))) {
                    final List<LocalQuickFix> fixes = new ArrayList<>();
                    fixes.add(new ReplaceWithForeachCallFix("forEach"));
                    if (!operations.isEmpty()) {
                      //for .stream()
                      fixes.add(new ReplaceWithForeachCallFix("forEachOrdered"));
                    }
                    holder.registerProblem(iteratedValue, "Can be replaced with foreach call",
                                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                           fixes.toArray(new LocalQuickFix[fixes.size()]));
                  }
                }
              }
            }
            catch (AnalysisCanceledException ignored) {
            }
          }
        }
      }

      private boolean isRawSubstitution(PsiType iteratedValueType, PsiClass collectionClass) {
        return iteratedValueType instanceof PsiClassType && PsiUtil
          .isRawSubstitutor(collectionClass, TypeConversionUtil.getSuperClassSubstitutor(collectionClass, (PsiClassType)iteratedValueType));
      }
    };
  }

  private static PsiExpression extractIncrementedExpression(PsiStatement statement) {
    if(!(statement instanceof PsiExpressionStatement)) return null;
    PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
    PsiExpression operand;
    if(expression instanceof PsiPostfixExpression) {
      if(!JavaTokenType.PLUSPLUS.equals(((PsiPostfixExpression)expression).getOperationTokenType())) return null;
      operand = ((PsiPostfixExpression)expression).getOperand();
    } else if(expression instanceof PsiPrefixExpression) {
      if(!JavaTokenType.PLUSPLUS.equals(((PsiPrefixExpression)expression).getOperationTokenType())) return null;
      operand = ((PsiPrefixExpression)expression).getOperand();
    } else return null; // TODO: support i = i+1;
    return operand;
  }

  @Nullable
  private static PsiLocalVariable getCounter(PsiForeachStatement foreachStatement,
                                             TerminalBlock tb,
                                             List<Operation> operations,
                                             List<PsiVariable> variables) {
    // have only one non-final variable
    if(variables.size() != 1) return null;

    // have single expression which is either ++x or x++
    PsiExpression operand = extractIncrementedExpression(tb.getSingleStatement());
    if(!(operand instanceof PsiReferenceExpression)) return null;
    PsiElement element = ((PsiReferenceExpression)operand).resolve();

    // the referred variable is the same as non-final variable
    if(!(element instanceof PsiLocalVariable) || !variables.contains(element)) return null;

    // the referred variable is not used in intermediate operations
    for(Operation operation : operations) {
      if(ReferencesSearch.search(element, new LocalSearchScope(operation.getExpression())).findFirst() != null) return null;
    }
    return (PsiLocalVariable)element;
  }

  private static boolean isAddAllCall(TerminalBlock tb) {
    final PsiVariable variable = tb.getVariable();
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();
    LOG.assertTrue(methodCallExpression != null);
    return isIdentityMapping(variable, methodCallExpression.getArgumentList().getExpressions()[0]);
  }

  private static boolean isCollectCall(TerminalBlock tb, final List<Operation> operations) {
    final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();
    if (methodCallExpression != null) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      PsiClass qualifierClass = null;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        if (ReferencesSearch.search(tb.getVariable(), new LocalSearchScope(qualifierExpression)).findFirst() != null) {
          return false;
        }
        final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
        if (resolve instanceof PsiVariable) {
          if (ReferencesSearch.search(resolve, new LocalSearchScope(methodCallExpression.getArgumentList())).findFirst() != null) {
            return false;
          }
        }
        qualifierClass = PsiUtil.resolveClassInType(qualifierExpression.getType());
      }
      else if (qualifierExpression == null) {
        final PsiClass enclosingClass = PsiTreeUtil.getParentOfType(methodCallExpression, PsiClass.class);
        if (PsiUtil.getEnclosingStaticElement(methodCallExpression, enclosingClass) == null) {
          qualifierClass = enclosingClass;
        }
      }

      if (qualifierClass != null && 
          InheritanceUtil.isInheritor(qualifierClass, false, CommonClassNames.JAVA_UTIL_COLLECTION)) {

        for(Operation op : operations) {
          final PsiExpression expression = op.getExpression();
          if (expression != null && isExpressionDependsOnUpdatedCollections(expression, qualifierExpression)) return false;
        }

        final PsiElement resolve = methodExpression.resolve();
        if (resolve instanceof PsiMethod &&
            "add".equals(((PsiMethod)resolve).getName()) &&
            ((PsiMethod)resolve).getParameterList().getParametersCount() == 1) {
          final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
          if (args.length == 1) {
            if (args[0] instanceof PsiCallExpression) {
              final PsiMethod method = ((PsiCallExpression)args[0]).resolveMethod();
              return method != null && !method.hasTypeParameters() && !isThrowsCompatible(method);
            }
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isExpressionDependsOnUpdatedCollections(PsiExpression condition,
                                                                 PsiExpression qualifierExpression) {
    final PsiElement collection = qualifierExpression instanceof PsiReferenceExpression
                                  ? ((PsiReferenceExpression)qualifierExpression).resolve()
                                  : null;
    if (collection != null) {
      return ReferencesSearch.search(collection, new LocalSearchScope(condition)).findFirst() != null;
    }

    final boolean[] dependsOnCollection = {false};
    condition.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiExpression callQualifier = expression.getMethodExpression().getQualifierExpression();
        if (callQualifier == null ||
            callQualifier instanceof PsiThisExpression && ((PsiThisExpression)callQualifier).getQualifier() == null ||
            callQualifier instanceof PsiSuperExpression && ((PsiSuperExpression)callQualifier).getQualifier() == null) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitThisExpression(PsiThisExpression expression) {
        super.visitThisExpression(expression);
        if (expression.getQualifier() == null && expression.getParent() instanceof PsiExpressionList) {
          dependsOnCollection[0] = true;
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {}

      @Override
      public void visitLambdaExpression(PsiLambdaExpression expression) {}
    });

    return dependsOnCollection[0];
  }

  private static boolean isTrivial(PsiStatement body, PsiParameter parameter) {
    //method reference
    final PsiCallExpression callExpression = LambdaCanBeMethodReferenceInspection
      .canBeMethodReferenceProblem(body instanceof PsiBlockStatement ? ((PsiBlockStatement)body).getCodeBlock() : body,
                                   new PsiParameter[]{parameter}, 
                                   createDefaultConsumerType(parameter.getProject(), parameter));
    if (callExpression == null) {
      return true;
    }
    final PsiMethod method = callExpression.resolveMethod();
    return method != null && isThrowsCompatible(method);
  }

  private static boolean isThrowsCompatible(PsiMethod method) {
    return ContainerUtil.find(method.getThrowsList().getReferencedTypes(), type -> !ExceptionUtil.isUncheckedException(type)) != null;
  }

  private static boolean isIdentityMapping(PsiVariable variable, PsiExpression mapperCall) {
    return mapperCall instanceof PsiReferenceExpression && ((PsiReferenceExpression)mapperCall).resolve() == variable;
  }

  private static void reformatWhenNeeded(@NotNull Project project, PsiElement result) {
    if (result != null) {
      CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
    }
  }

  private static class ReplaceWithForeachCallFix implements LocalQuickFix {
    private final String myForEachMethodName;

    protected ReplaceWithForeachCallFix(String forEachMethodName) {
      myForEachMethodName = forEachMethodName;
    }

    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with " + myForEachMethodName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiForeachStatement foreachStatement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiForeachStatement.class);
      if (foreachStatement != null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(foreachStatement)) return;
        PsiStatement body = foreachStatement.getBody();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        if (body != null && iteratedValue != null) {
          restoreComments(foreachStatement, body);

          final PsiParameter parameter = foreachStatement.getIterationParameter();
          final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
          TerminalBlock tb = TerminalBlock.from(parameter, body);
          List<String> intermediateOps = tb.extractOperationReplacements(elementFactory);

          StringBuilder buffer = generateStream(iteratedValue, intermediateOps);
          PsiElement block = tb.convertToElement(elementFactory);

          buffer.append(".").append(myForEachMethodName).append("(");

          final String functionalExpressionText = createForEachFunctionalExpressionText(project, block, tb.getVariable());
          PsiExpressionStatement callStatement = (PsiExpressionStatement)elementFactory.createStatementFromText(buffer.toString() + functionalExpressionText + ");", foreachStatement);
          callStatement = (PsiExpressionStatement)foreachStatement.replace(callStatement);

          final PsiExpressionList argumentList = ((PsiCallExpression)callStatement.getExpression()).getArgumentList();
          LOG.assertTrue(argumentList != null, callStatement.getText());
          final PsiExpression[] expressions = argumentList.getExpressions();
          LOG.assertTrue(expressions.length == 1);

          if (expressions[0] instanceof PsiFunctionalExpression && ((PsiFunctionalExpression)expressions[0]).getFunctionalInterfaceType() == null) {
            callStatement =
              (PsiExpressionStatement)callStatement.replace(elementFactory.createStatementFromText(
                buffer.toString() + "(" + tb.getVariable().getText() + ") -> " + wrapInBlock(block) + ");", callStatement));
          }

          simplifyRedundantCast(callStatement);

          CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(callStatement));
        }
      }
    }

    private static String createForEachFunctionalExpressionText(Project project, PsiElement block, PsiVariable variable) {
      final PsiCallExpression callExpression = LambdaCanBeMethodReferenceInspection.extractMethodCallFromBlock(block);
      if (callExpression != null) {
        final PsiClassType functionalType = createDefaultConsumerType(project, variable);
        final PsiVariable[] parameters = {variable};
        String methodReferenceText =
          LambdaCanBeMethodReferenceInspection.convertToMethodReference(block, parameters, functionalType, null);
        if (methodReferenceText != null) {
          return methodReferenceText;
        }
      }
      return variable.getName() + " -> " + wrapInBlock(block);
    }

    private static String wrapInBlock(PsiElement block) {
      if(block instanceof PsiExpressionStatement) {
        return ((PsiExpressionStatement)block).getExpression().getText();
      }
      if(block instanceof PsiCodeBlock) {
        return block.getText();
      }
      return "{" + block.getText() + "}";
    }
  }

  private static PsiClassType createDefaultConsumerType(Project project, PsiVariable variable) {
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass consumerClass = psiFacade.findClass("java.util.function.Consumer", GlobalSearchScope.allScope(project));
    return consumerClass != null ? psiFacade.getElementFactory().createType(consumerClass, variable.getType()) : null;
  }

  private static class ReplaceWithCollectFix extends ReplaceWithCollectAbstractFix {
    @Override
    protected String getMethodName() {
      return "collect";
    }
  }

  private static class ReplaceWithAddAllFix extends ReplaceWithCollectAbstractFix {
    @Override
    protected String getMethodName() {
      return "addAll";
    }
  }

  private abstract static class ReplaceWithCollectAbstractFix implements LocalQuickFix {

    protected abstract String getMethodName();
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with " + getMethodName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiForeachStatement foreachStatement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiForeachStatement.class);
      if (foreachStatement != null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(foreachStatement)) return;
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiStatement body = foreachStatement.getBody();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        if (body != null && iteratedValue != null) {
          final PsiType iteratedValueType = iteratedValue.getType();
          final PsiParameter parameter = foreachStatement.getIterationParameter();
          TerminalBlock tb = TerminalBlock.from(parameter, body);
          List<String> intermediateOps = tb.extractOperationReplacements(elementFactory);
          final PsiMethodCallExpression methodCallExpression = tb.getSingleMethodCall();

          if (methodCallExpression == null) return;

          if (intermediateOps.isEmpty() && isAddAllCall(tb)) {
            restoreComments(foreachStatement, body);
            final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
            final String qualifierText = qualifierExpression != null ? qualifierExpression.getText() : "";
            final String collectionText = iteratedValueType instanceof PsiArrayType ? "java.util.Arrays.asList("+iteratedValue.getText()+")" :
                                          getIteratedValueText(iteratedValue);
            final String callText = StringUtil.getQualifiedName(qualifierText, "addAll(" + collectionText + ");");
            PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(callText, foreachStatement));
            reformatWhenNeeded(project, result);
            return;
          }
          intermediateOps.add(createMapperFunctionalExpressionText(tb.getVariable(), methodCallExpression.getArgumentList().getExpressions()[0]));
          final StringBuilder builder = generateStream(iteratedValue, intermediateOps);

          builder.append(".collect(java.util.stream.Collectors.");
          PsiElement result = null;
          try {
            final PsiExpression qualifierExpression = methodCallExpression.getMethodExpression().getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
              final PsiElement resolve = ((PsiReferenceExpression)qualifierExpression).resolve();
              if (resolve instanceof PsiVariable) {
                if (resolve instanceof PsiLocalVariable && foreachStatement.equals(PsiTreeUtil.skipSiblingsForward(resolve.getParent(), PsiWhiteSpace.class, PsiComment.class))) {
                  final PsiExpression initializer = ((PsiVariable)resolve).getInitializer();
                  if (initializer instanceof PsiNewExpression) {
                    final PsiExpressionList argumentList = ((PsiNewExpression)initializer).getArgumentList();
                    if (argumentList != null && argumentList.getExpressions().length == 0) {
                      restoreComments(foreachStatement, body);
                      final String callText = builder.toString() + createInitializerReplacementText(((PsiVariable)resolve).getType(), initializer) + ")";
                      result = initializer.replace(elementFactory.createExpressionFromText(callText, null));
                      simplifyRedundantCast(result);
                      foreachStatement.delete();
                      return;
                    }
                  }
                }
              }
            }
            restoreComments(foreachStatement, body);
            final String qualifierText = qualifierExpression != null ? qualifierExpression.getText() : "";
            final String callText = StringUtil.getQualifiedName(qualifierText, "addAll(" + builder.toString() + "toList()));");
            result = foreachStatement.replace(elementFactory.createStatementFromText(callText, foreachStatement));
            simplifyRedundantCast(result);
          }
          finally {
            reformatWhenNeeded(project, result);
          }
        }
      }
    }

    private static String createInitializerReplacementText(PsiType varType, PsiExpression initializer) {
      final PsiType initializerType = initializer.getType();
      final PsiClassType rawType = initializerType instanceof PsiClassType ? ((PsiClassType)initializerType).rawType() : null;
      final PsiClassType rawVarType = varType instanceof PsiClassType ? ((PsiClassType)varType).rawType() : null;
      if (rawType != null && rawVarType != null &&
          rawType.equalsToText(CommonClassNames.JAVA_UTIL_ARRAY_LIST) &&
          (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_LIST) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
        return "toList()";
      }
      else if (rawType != null && rawVarType != null &&
               rawType.equalsToText(CommonClassNames.JAVA_UTIL_HASH_SET) &&
               (rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_SET) || rawVarType.equalsToText(CommonClassNames.JAVA_UTIL_COLLECTION))) {
        return "toSet()";
      }
      else if (rawType != null) {
        return "toCollection(" + rawType.getClassName() + "::new)";
      }
      else {
        return "toCollection(() -> " + initializer.getText() +")";
      }
    }

    private static String createMapperFunctionalExpressionText(PsiVariable variable, PsiExpression expression) {
      if (!isIdentityMapping(variable, expression)) {
        return new MapOp(expression, variable).createReplacement(null);
      }
      return "";
    }

  }

  private static class ReplaceWithCountFix implements LocalQuickFix {

    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with count()";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiForeachStatement foreachStatement = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiForeachStatement.class);
      if (foreachStatement != null) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(foreachStatement)) return;
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiStatement body = foreachStatement.getBody();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        if (body != null && iteratedValue != null) {
          final PsiParameter parameter = foreachStatement.getIterationParameter();
          TerminalBlock tb = TerminalBlock.from(parameter, body);
          List<String> intermediateOps = tb.extractOperationReplacements(elementFactory);
          PsiExpression operand = extractIncrementedExpression(tb.getSingleStatement());
          if(!(operand instanceof PsiReferenceExpression)) return;
          PsiElement element = ((PsiReferenceExpression)operand).resolve();
          if(!(element instanceof PsiLocalVariable)) return;
          PsiLocalVariable var = (PsiLocalVariable)element;
          final StringBuilder builder = generateStream(iteratedValue, intermediateOps);
          builder.append(".count()");
          PsiElement declaration = var.getParent();
          if(declaration instanceof PsiDeclarationStatement) {
            PsiElement[] elements = ((PsiDeclarationStatement)declaration).getDeclaredElements();
            if(elements[elements.length-1] == var && foreachStatement.equals(
              PsiTreeUtil.skipSiblingsForward(declaration, PsiWhiteSpace.class, PsiComment.class))) {
              PsiExpression initializer = var.getInitializer();
              if(initializer != null && initializer.getText().equals("0")) {
                String typeStr = var.getType().getCanonicalText();
                String replacement = (typeStr.equals("long") ? "" : "(" + typeStr + ") ") + builder;
                initializer.replace(elementFactory.createExpressionFromText(replacement, foreachStatement));
                simplifyRedundantCast(var);
                foreachStatement.delete();
                reformatWhenNeeded(project, var);
                return;
              }
            }
          }
          PsiElement result = foreachStatement.replace(elementFactory.createStatementFromText(var.getName()+"+="+builder+";", foreachStatement));
          simplifyRedundantCast(result);
          reformatWhenNeeded(project, result);
        }
      }
    }

  }

  /**
   * Intermediate stream operation representation
   */
  static abstract class Operation {
    final PsiExpression myExpression;
    final PsiVariable myVariable;

    protected Operation(PsiExpression expression, PsiVariable variable) {
      myExpression = expression;
      myVariable = variable;
    }

    PsiExpression getExpression() {
      return myExpression;
    }

    abstract String createReplacement(PsiElementFactory factory);
  }

  static class FilterOp extends Operation {

    FilterOp(PsiExpression condition, PsiVariable variable) {
      super(condition, variable);
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      return ".filter(" + compoundLambdaOrMethodReference(myVariable, myExpression,
                                                          "java.util.function.Predicate",
                                                          new PsiType[] {myVariable.getType()}) + ")";
    }
  }

  static class MapOp extends Operation {
    MapOp(PsiExpression expression, PsiVariable variable) {
      super(expression, variable);
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      return ".map(" + compoundLambdaOrMethodReference(myVariable, myExpression,
                                                       "java.util.function.Function",
                                                       new PsiType[] {myVariable.getType(), myExpression.getType()}) + ")";
    }
  }

  static class FlatMapOp extends Operation {
    FlatMapOp(PsiExpression expression, PsiVariable variable) {
      super(expression, variable);
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      PsiExpression replacement = factory.createExpressionFromText(myExpression.getText() + ".stream()", myExpression);
      return ".flatMap(" + compoundLambdaOrMethodReference(myVariable, replacement,
                                                           "java.util.function.Function",
                                                           new PsiType[] {myVariable.getType(), replacement.getType()}) + ")";
    }
  }

  static class ArrayFlatMapOp extends Operation {
    ArrayFlatMapOp(PsiExpression expression, PsiVariable variable) {
      super(expression, variable);
    }

    @Override
    public String createReplacement(PsiElementFactory factory) {
      PsiExpression replacement = factory.createExpressionFromText("java.util.Arrays.stream("+myExpression.getText() + ")", myExpression);
      return ".flatMap(" + compoundLambdaOrMethodReference(myVariable, replacement,
                                                           "java.util.function.Function",
                                                           new PsiType[] {myVariable.getType(), replacement.getType()}) + ")";
    }
  }

  /**
   * This class represents the code which should be performed
   * as a part of forEach operation of resulting stream.
   */
  static class TerminalBlock {
    private PsiVariable myVariable;
    private PsiStatement[] myStatements;

    private TerminalBlock(PsiVariable variable, PsiStatement[] statements) {
      myVariable = variable;
      myStatements = statements;
      flatten();
    }

    private void flatten() {
      while(myStatements.length == 1 && myStatements[0] instanceof PsiBlockStatement) {
        myStatements = ((PsiBlockStatement)myStatements[0]).getCodeBlock().getStatements();
      }
    }

    PsiStatement getSingleStatement() {
      return myStatements.length == 1 ? myStatements[0] : null;
    }

    /**
     * @return PsiMethodCallExpression if this TerminalBlock contains single method call, null otherwise
     */
    @Nullable
    PsiMethodCallExpression getSingleMethodCall() {
      PsiStatement statement = getSingleStatement();
      if(statement instanceof PsiExpressionStatement) {
        PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
        if(expression instanceof PsiMethodCallExpression)
          return (PsiMethodCallExpression)expression;
      }
      return null;
    }

    /**
     * If possible, extract single intermediate stream operation from this
     * {@code TerminalBlock} changing the TerminalBlock itself to exclude this operation
     *
     * @return extracted operation or null if extraction is not possible
     */
    @Nullable
    Operation extractOperation() {
      // extract filter
      if(getSingleStatement() instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)getSingleStatement();
        if(ifStatement.getElseBranch() != null || ifStatement.getCondition() == null)
          return null;
        replaceWith(ifStatement.getThenBranch());
        return new FilterOp(ifStatement.getCondition(), myVariable);
      }
      // extract flatMap
      if(getSingleStatement() instanceof PsiForeachStatement) {
        PsiForeachStatement foreachStatement = (PsiForeachStatement)getSingleStatement();
        final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
        final PsiStatement body = foreachStatement.getBody();
        if (iteratedValue != null && body != null) {
          final PsiType iteratedValueType = iteratedValue.getType();
          Operation op = null;
          if(iteratedValueType instanceof PsiArrayType) {
            // do not handle flatMapToPrimitive
            if (((PsiArrayType)iteratedValueType).getComponentType() instanceof PsiPrimitiveType)
              return null;
            op = new ArrayFlatMapOp(iteratedValue, myVariable);
          } else {
            final PsiClass iteratorClass = PsiUtil.resolveClassInClassTypeOnly(iteratedValueType);
            final PsiClass collectionClass =
              JavaPsiFacade.getInstance(body.getProject())
                .findClass(CommonClassNames.JAVA_UTIL_COLLECTION, foreachStatement.getResolveScope());
            if (collectionClass != null && InheritanceUtil.isInheritorOrSelf(iteratorClass, collectionClass, true)) {
              op = new FlatMapOp(iteratedValue, myVariable);
            }
          }
          if(op != null && ReferencesSearch.search(myVariable, new LocalSearchScope(body)).findFirst() == null) {
            myVariable = foreachStatement.getIterationParameter();
            replaceWith(body);
            return op;
          }
        }
      }
      // extract map
      if(myStatements.length > 1) {
        PsiStatement first = myStatements[0];
        if(first instanceof PsiDeclarationStatement) {
          PsiDeclarationStatement decl = (PsiDeclarationStatement)first;
          PsiElement[] elements = decl.getDeclaredElements();
          if(elements.length == 1) {
            PsiElement element = elements[0];
            if(element instanceof PsiLocalVariable) {
              PsiLocalVariable declaredVar = (PsiLocalVariable)element;
              // do not handle mapToPrimitive
              if(!(declaredVar.getType() instanceof PsiPrimitiveType)) {
                PsiExpression initializer = declaredVar.getInitializer();
                PsiStatement[] leftOver = Arrays.copyOfRange(myStatements, 1, myStatements.length);
                if (initializer != null &&
                    ReferencesSearch.search(myVariable, new LocalSearchScope(leftOver))
                      .findFirst() == null) {
                  MapOp op = new MapOp(initializer, myVariable);
                  myVariable = declaredVar;
                  myStatements = leftOver;
                  flatten();
                  return op;
                }
              }
            }
          }
        }
      }
      return null;
    }

    @NotNull
    List<Operation> extractOperations() {
      List<Operation> result = new ArrayList<>();
      while(true) {
        Operation op = extractOperation();
        if(op == null) return result;
        result.add(op);
      }
    }

    private void replaceWith(PsiStatement statement) {
      myStatements = new PsiStatement[] {statement};
      flatten();
    }

    public PsiVariable getVariable() {
      return myVariable;
    }

    @Contract("_, _ -> !null")
    static TerminalBlock from(PsiVariable variable, PsiStatement statement) {
      return new TerminalBlock(variable, new PsiStatement[] {statement});
    }

    @NotNull
    private List<String> extractOperationReplacements(PsiElementFactory factory) {
      List<String> intermediateOps = new ArrayList<>();
      while(true) {
        Operation operation = extractOperation();
        if(operation == null)
          break;
        intermediateOps.add(operation.createReplacement(factory));
      }
      return intermediateOps;
    }

    /**
     * Converts this TerminalBlock to PsiElement (either PsiStatement or PsiCodeBlock)
     *
     * @param factory factory to use to create new element if necessary
     * @return the PsiElement
     */
    public PsiElement convertToElement(PsiElementFactory factory) {
      if (myStatements.length == 1) {
        return myStatements[0];
      }
      PsiCodeBlock block = factory.createCodeBlock();
      for (PsiStatement statement : myStatements) {
        block.add(statement);
      }
      return block;
    }
  }

  private static String compoundLambdaOrMethodReference(PsiVariable variable,
                                                        PsiExpression expression,
                                                        String samQualifiedName,
                                                        PsiType[] samParamTypes) {
    String result = "";
    final Project project = variable.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiClass functionClass = psiFacade.findClass(samQualifiedName, GlobalSearchScope.allScope(project));
    for (int i = 0; i < samParamTypes.length; i++) {
      if (samParamTypes[i] instanceof PsiPrimitiveType) {
        samParamTypes[i] = ((PsiPrimitiveType)samParamTypes[i]).getBoxedType(expression);
      }
    }
    final PsiClassType functionalInterfaceType = functionClass != null ? psiFacade.getElementFactory().createType(functionClass, samParamTypes) : null;
    final PsiVariable[] parameters = {variable};
    String methodReferenceText = LambdaCanBeMethodReferenceInspection.convertToMethodReference(expression, parameters, functionalInterfaceType, null);
    if (methodReferenceText != null) {
      LOG.assertTrue(functionalInterfaceType != null);
      result += "(" + functionalInterfaceType.getCanonicalText() + ")" + methodReferenceText;
    } else {
      result += variable.getName() + " -> " + expression.getText();
    }
    return result;
  }

  private static void simplifyRedundantCast(PsiElement result) {
    for (PsiMethodReferenceExpression methodReferenceExpression : PsiTreeUtil
      .findChildrenOfType(result, PsiMethodReferenceExpression.class)) {
      final PsiElement parent = methodReferenceExpression.getParent();
      if (parent instanceof PsiTypeCastExpression) {
        if (RedundantCastUtil.isCastRedundant((PsiTypeCastExpression)parent)) {
          final PsiExpression operand = ((PsiTypeCastExpression)parent).getOperand();
          LOG.assertTrue(operand != null);
          parent.replace(operand);
        }
      }
    }
  }

  private static void restoreComments(PsiForeachStatement foreachStatement, PsiStatement body) {
    final PsiElement parent = foreachStatement.getParent();
    for (PsiElement comment : PsiTreeUtil.findChildrenOfType(body, PsiComment.class)) {
      parent.addBefore(comment, foreachStatement);
    }
  }

  @NotNull
  private static StringBuilder generateStream(PsiExpression iteratedValue, List<String> intermediateOps) {
    StringBuilder buffer = new StringBuilder();
    final PsiType iteratedValueType = iteratedValue.getType();
    if (iteratedValueType instanceof PsiArrayType) {
      buffer.append("java.util.Arrays.stream(").append(iteratedValue.getText()).append(")");
    }
    else {
      buffer.append(getIteratedValueText(iteratedValue));
      if (!intermediateOps.isEmpty()) {
        buffer.append(".stream()");
      }
    }
    intermediateOps.forEach(buffer::append);
    return buffer;
  }

  private static String getIteratedValueText(PsiExpression iteratedValue) {
    return iteratedValue instanceof PsiCallExpression ||
           iteratedValue instanceof PsiReferenceExpression ||
           iteratedValue instanceof PsiQualifiedExpression ||
           iteratedValue instanceof PsiParenthesizedExpression ? iteratedValue.getText() : "(" + iteratedValue.getText() + ")";
  }
}
