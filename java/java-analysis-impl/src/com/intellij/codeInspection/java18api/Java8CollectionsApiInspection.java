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
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

/**
 * @author Dmitry Batkovich
 */
public class Java8CollectionsApiInspection extends BaseJavaBatchLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(Java8CollectionsApiInspection.class);

  public boolean myReportContainsCondition;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report when \'containsKey\' is used in condition (may change semantics)", this,
                                          "myReportContainsCondition");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiElement nameElement = expression.getMethodExpression().getReferenceNameElement();
        if(nameElement != null && expression.getArgumentList().getExpressions().length == 2 &&
          "sort".equals(nameElement.getText())) {
          PsiMethod method = expression.resolveMethod();
          if(method != null) {
            PsiClass containingClass = method.getContainingClass();
            if(containingClass != null && CommonClassNames.JAVA_UTIL_COLLECTIONS.equals(containingClass.getQualifiedName())) {
              //noinspection DialogTitleCapitalization
              holder.registerProblem(nameElement, QuickFixBundle.message("java.8.collections.api.inspection.sort.description"),
                                     new ReplaceWithListSortFix());
            }
          }
        }
      }

      void handleIteratorLoop(PsiLoopStatement statement, PsiJavaToken endToken, IteratorDeclaration declaration) {
        if(endToken == null) return;
        PsiStatement body = statement.getBody();
        if(!(body instanceof PsiBlockStatement)) return;
        PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
        if(statements.length != 2 || !(statements[1] instanceof PsiIfStatement)) return;
        PsiVariable element = declaration.getNextElementVariable(statements[0]);
        if(element == null) return;
        PsiIfStatement ifStatement = (PsiIfStatement)statements[1];
        PsiExpression condition = ifStatement.getCondition();
        if(condition == null || ifStatement.getElseBranch() != null) return;
        PsiStatement thenStatement = ControlFlowUtils.stripBraces(ifStatement.getThenBranch());
        if(!(thenStatement instanceof PsiExpressionStatement)) return;
        if(!declaration.isIteratorMethodCall(((PsiExpressionStatement)thenStatement).getExpression(), "remove")) return;
        if(!OptionalIsPresentInspection.isVoidLambdaCandidate(condition)) return;
        //noinspection DialogTitleCapitalization
        holder.registerProblem(statement, new TextRange(0, endToken.getTextOffset() - statement.getTextOffset() + 1),
                               QuickFixBundle.message("java.8.collections.api.inspection.remove.description"),
                               new ReplaceWithRemoveIfQuickFix());
      }

      @Override
      public void visitForStatement(PsiForStatement statement) {
        super.visitForStatement(statement);
        PsiStatement initialization = statement.getInitialization();
        IteratorDeclaration declaration = IteratorDeclaration.extract(initialization);
        if(declaration == null) return;
        if(statement.getUpdate() != null) return;
        if(!declaration.isHasNextCall(statement.getCondition())) return;
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitWhileStatement(PsiWhileStatement statement) {
        super.visitWhileStatement(statement);
        PsiElement previous = PsiTreeUtil.skipSiblingsBackward(statement, PsiComment.class, PsiWhiteSpace.class);
        if(!(previous instanceof PsiDeclarationStatement)) return;
        IteratorDeclaration declaration = IteratorDeclaration.extract((PsiStatement)previous);
        if(declaration == null || !declaration.isHasNextCall(statement.getCondition())) return;
        if(!ReferencesSearch.search(declaration.myIterator, declaration.myIterator.getUseScope()).forEach(ref -> {
          return PsiTreeUtil.isAncestor(statement, ref.getElement(), true);
        })) return;
        handleIteratorLoop(statement, statement.getRParenth(), declaration);
      }

      @Override
      public void visitConditionalExpression(PsiConditionalExpression expression) {
        final ConditionInfo conditionInfo = extractConditionInfo(expression.getCondition());
        if (conditionInfo == null) return;
        final PsiExpression thenExpression = expression.getThenExpression();
        final PsiExpression elseExpression = expression.getElseExpression();
        if (thenExpression == null || elseExpression == null) return;
        analyzeCorrespondenceOfPutAndGet(conditionInfo.isInverted() ? thenExpression : elseExpression,
                                         conditionInfo.isInverted() ? elseExpression : thenExpression,
                                         conditionInfo.getQualifier(), conditionInfo.getContainsKey(),
                                         holder, expression);
      }

      @Override
      public void visitIfStatement(PsiIfStatement statement) {
        handleGetWithVariable(holder, statement);
        final PsiExpression condition = statement.getCondition();
        final ConditionInfo conditionInfo = extractConditionInfo(condition);
        if (conditionInfo == null) return;
        PsiStatement maybeGetBranch = conditionInfo.isInverted() ? statement.getElseBranch() : statement.getThenBranch();
        if (maybeGetBranch instanceof PsiBlockStatement) {
          final PsiStatement[] getBranchStatements = ((PsiBlockStatement)maybeGetBranch).getCodeBlock().getStatements();
          if (getBranchStatements.length > 1) return;
          maybeGetBranch = getBranchStatements.length == 0 ? null : getBranchStatements[0];
        }
        final PsiStatement branch = conditionInfo.isInverted() ? statement.getThenBranch() : statement.getElseBranch();
        final PsiStatement maybePutStatement;
        if (branch instanceof PsiBlockStatement) {
          final PsiStatement[] statements = ((PsiBlockStatement)branch).getCodeBlock().getStatements();
          if (statements.length != 1) return;
          maybePutStatement = statements[statements.length - 1];
        }
        else {
          maybePutStatement = branch;
        }
        if (maybePutStatement != null) {
          analyzeCorrespondenceOfPutAndGet(maybePutStatement, maybeGetBranch, conditionInfo.getQualifier(), conditionInfo.getContainsKey(),
                                           holder, statement);
        }
      }

      private void handleGetWithVariable(ProblemsHolder holder, PsiIfStatement statement) {
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
          if (ExpressionUtils.isSimpleExpression(assignment.getRExpression()) &&
              equivalence.expressionsAreEquivalent(assignment.getLExpression(), value)) {
            holder.registerProblem(condition, QuickFixBundle.message("java.8.collections.api.inspection.description"),
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
          if(!isJavaUtilMapMethodWithName(putCall, "put")) return;
          PsiExpression[] putArguments = putCall.getArgumentList().getExpressions();
          if (putArguments.length != 2 ||
              !equivalence.expressionsAreEquivalent(putCall.getMethodExpression().getQualifierExpression(),
                                                    getCall.getMethodExpression().getQualifierExpression()) ||
              !equivalence.expressionsAreEquivalent(key, putArguments[0]) ||
              !equivalence.expressionsAreEquivalent(value, putArguments[1])) {
            return;
          }
          if(!ExceptionUtil.getThrownCheckedExceptions(new PsiElement[] {lambdaCandidate}).isEmpty()) return;
          if(!PsiTreeUtil.processElements(lambdaCandidate, e -> {
            if(!(e instanceof PsiReferenceExpression)) return true;
            PsiElement element = ((PsiReferenceExpression)e).resolve();
            if(!(element instanceof PsiVariable)) return true;
            return HighlightControlFlowUtil.isEffectivelyFinal((PsiVariable)element, lambdaCandidate, null);
          })) {
            return;
          }
          holder.registerProblem(condition, QuickFixBundle.message("java.8.collections.api.inspection.description"),
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
    PsiExpression value = getValueComparedWithNull(binOp);
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
              isJavaUtilMapMethodWithName((PsiMethodCallExpression)initializer, "get")) {
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
        if (rValue instanceof PsiMethodCallExpression && isJavaUtilMapMethodWithName((PsiMethodCallExpression)rValue, "get")) {
          return (PsiMethodCallExpression)rValue;
        }
      }
    }
    return null;
  }

  @Nullable
  private ConditionInfo extractConditionInfo(PsiExpression condition) {
    final ConditionInfo info = extractConditionInfoIfGet(condition);
    if (info != null) {
      return info;
    }
    return !myReportContainsCondition ? null : extractConditionInfoIfContains(condition);
  }

  @Nullable
  private static PsiExpression getValueComparedWithNull(PsiBinaryExpression binOp) {
    if(!binOp.getOperationTokenType().equals(JavaTokenType.EQEQ) &&
      !binOp.getOperationTokenType().equals(JavaTokenType.NE)) return null;
    PsiExpression left = binOp.getLOperand();
    PsiExpression right = binOp.getROperand();
    if(ExpressionUtils.isNullLiteral(right)) return left;
    if(ExpressionUtils.isNullLiteral(left)) return right;
    return null;
  }

  @Nullable
  private static ConditionInfo extractConditionInfoIfGet(PsiExpression condition) {
    if(!(condition instanceof PsiBinaryExpression)) return null;
    PsiBinaryExpression binOp = (PsiBinaryExpression)condition;
    PsiExpression operand = getValueComparedWithNull(binOp);
    if (!(operand instanceof PsiMethodCallExpression)) return null;
    final PsiMethodCallExpression maybeGetCall = (PsiMethodCallExpression)operand;
    if (!isJavaUtilMapMethodWithName(maybeGetCall, "get")) return null;
    final PsiExpression[] arguments = maybeGetCall.getArgumentList().getExpressions();
    if (arguments.length != 1) return null;
    PsiExpression getQualifier = maybeGetCall.getMethodExpression().getQualifierExpression();
    PsiExpression keyExpression = arguments[0];
    return new ConditionInfo(getQualifier, keyExpression, binOp.getOperationTokenType().equals(JavaTokenType.EQEQ));
  }

  @Nullable
  private static ConditionInfo extractConditionInfoIfContains(PsiExpression condition) {
    boolean inverted = false;
    final PsiMethodCallExpression conditionMethodCall;
    if (condition instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
      if (JavaTokenType.EXCL.equals(prefixExpression.getOperationSign().getTokenType()) &&
          prefixExpression.getOperand() instanceof PsiMethodCallExpression) {
        conditionMethodCall = (PsiMethodCallExpression)prefixExpression.getOperand();
        inverted = true;
      }
      else {
        return null;
      }
    }
    else if (condition instanceof PsiMethodCallExpression) {
      conditionMethodCall = (PsiMethodCallExpression)condition;
    }
    else {
      return null;
    }
    if (!isJavaUtilMapMethodWithName(conditionMethodCall, "containsKey")) {
      return null;
    }
    final PsiExpression containsQualifier = conditionMethodCall.getMethodExpression().getQualifierExpression();
    if (containsQualifier == null) {
      return null;
    }
    final PsiExpression[] expressions = conditionMethodCall.getArgumentList().getExpressions();
    if (expressions.length != 1) {
      return null;
    }
    PsiExpression containsKey = expressions[0];
    return new ConditionInfo(containsQualifier, containsKey, inverted);
  }

  private static void analyzeCorrespondenceOfPutAndGet(@NotNull PsiElement adjustedElseBranch,
                                                       @Nullable PsiElement adjustedThenBranch,
                                                       @Nullable PsiExpression containsQualifier,
                                                       @Nullable PsiExpression containsKey,
                                                       @NotNull ProblemsHolder holder,
                                                       @NotNull PsiElement context) {
    final PsiElement maybePutMethodCall;
    final PsiElement maybeGetMethodCall;
    if (adjustedThenBranch == null) {
      maybeGetMethodCall = null;
      if (adjustedElseBranch instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)adjustedElseBranch).getExpression();
        if (expression instanceof PsiMethodCallExpression && isJavaUtilMapMethodWithName((PsiMethodCallExpression)expression, "put")) {
          maybePutMethodCall = expression;
        }
        else {
          maybePutMethodCall = null;
        }
      }
      else {
        maybePutMethodCall = null;
      }
    }
    else {
      if (adjustedElseBranch instanceof PsiStatement && adjustedThenBranch instanceof PsiStatement) {
        final EquivalenceChecker.Decision decision = EquivalenceChecker.getCanonicalPsiEquivalence().statementsAreEquivalentDecision((PsiStatement)adjustedElseBranch,
                                                                                                                                     (PsiStatement)adjustedThenBranch);
        maybePutMethodCall = decision.getLeftDiff();
        maybeGetMethodCall = decision.getRightDiff();
      }
      else {
        maybePutMethodCall = adjustedElseBranch;
        maybeGetMethodCall = adjustedThenBranch;
      }
    }
    if (maybePutMethodCall instanceof PsiMethodCallExpression &&
        (maybeGetMethodCall == null || maybeGetMethodCall instanceof PsiMethodCallExpression)) {
      final PsiMethodCallExpression putMethodCall = (PsiMethodCallExpression)maybePutMethodCall;
      final PsiMethodCallExpression getMethodCall = (PsiMethodCallExpression)maybeGetMethodCall;
      final PsiExpression putQualifier = putMethodCall.getMethodExpression().getQualifierExpression();
      final PsiExpression getQualifier = getMethodCall == null ? null : getMethodCall.getMethodExpression().getQualifierExpression();
      if ((getMethodCall == null || EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(putQualifier, getQualifier)) &&
          EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(putQualifier, containsQualifier) &&
          isJavaUtilMapMethodWithName(putMethodCall, "put") &&
          (getMethodCall == null || isJavaUtilMapMethodWithName(getMethodCall, "get"))) {

        PsiExpression getArgument;
        if (getMethodCall != null) {
          final PsiExpression[] arguments = getMethodCall.getArgumentList().getExpressions();
          if (arguments.length != 1) {
            return;
          }
          getArgument = arguments[0];
        }
        else {
          getArgument = null;
        }

        final PsiExpression[] putArguments = putMethodCall.getArgumentList().getExpressions();
        if (putArguments.length != 2) {
          return;
        }
        PsiExpression putKeyArgument = putArguments[0];

        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(containsKey, putKeyArgument) &&
            (getArgument == null || EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(getArgument, putKeyArgument))) {
          holder.registerProblem(context, QuickFixBundle.message("java.8.collections.api.inspection.description"),
                                 new ReplaceWithMapPutIfAbsentFix(putMethodCall));
        }
      }
    }
  }

  private static boolean isJavaUtilMapMethodWithName(@NotNull PsiMethodCallExpression methodCallExpression, @NotNull String expectedName) {
    if (!expectedName.equals(methodCallExpression.getMethodExpression().getReferenceName())) {
      return false;
    }
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) return false;
    PsiMethod[] superMethods = method.findDeepestSuperMethods();
    if (superMethods.length == 0) {
      superMethods = new PsiMethod[]{method};
    }
    return StreamEx.of(superMethods).map(PsiMember::getContainingClass).nonNull().map(PsiClass::getQualifiedName)
      .has(CommonClassNames.JAVA_UTIL_MAP);
  }

  private static class ConditionInfo {
    private final PsiExpression myQualifier;
    private final PsiExpression myContainsKey;
    private final boolean myInverted;

    private ConditionInfo(PsiExpression qualifier, PsiExpression containsKey, boolean inverted) {
      myQualifier = qualifier;
      myContainsKey = containsKey;
      myInverted = inverted;
    }

    public PsiExpression getQualifier() {
      return myQualifier;
    }

    public PsiExpression getContainsKey() {
      return myContainsKey;
    }

    public boolean isInverted() {
      return myInverted;
    }
  }

  private static class ReplaceWithListSortFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return QuickFixBundle.message("java.8.collections.api.inspection.sort.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if(methodCallExpression != null) {
        PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
        if(args.length == 2) {
          PsiExpression list = args[0];
          PsiExpression comparator = args[1];
          String replacement =
            ParenthesesUtils.getText(list, ParenthesesUtils.METHOD_CALL_PRECEDENCE) + ".sort(" + comparator.getText() + ")";
          if (!FileModificationService.getInstance().preparePsiElementForWrite(element.getContainingFile())) return;
          methodCallExpression
            .replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, methodCallExpression));
        }
      }
    }
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
      return QuickFixBundle.message("java.8.collections.api.inspection.get.fix.family.name");
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
      if(getCall == null || !isJavaUtilMapMethodWithName(getCall, "get")) return;
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

  private static class ReplaceWithRemoveIfQuickFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return QuickFixBundle.message("java.8.collections.api.inspection.remove.fix.name");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiLoopStatement)) return;
      PsiLoopStatement loop = (PsiLoopStatement)element;
      IteratorDeclaration declaration;
      PsiElement previous = null;
      if(loop instanceof PsiForStatement) {
        declaration = IteratorDeclaration.extract(((PsiForStatement)loop).getInitialization());
      } else if(loop instanceof PsiWhileStatement) {
        previous = PsiTreeUtil.skipSiblingsBackward(loop, PsiComment.class, PsiWhiteSpace.class);
        if(!(previous instanceof PsiDeclarationStatement)) return;
        declaration = IteratorDeclaration.extract((PsiStatement)previous);
      } else return;
      if(declaration == null) return;
      PsiStatement body = loop.getBody();
      if(!(body instanceof PsiBlockStatement)) return;
      PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      if(statements.length != 2 || !(statements[1] instanceof PsiIfStatement)) return;
      PsiVariable variable = declaration.getNextElementVariable(statements[0]);
      if(variable == null) return;
      PsiExpression condition = ((PsiIfStatement)statements[1]).getCondition();
      if(condition == null) return;
      if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
      String replacement = (declaration.myCollection == null ? "" : declaration.myCollection.getText() + ".") +
                           "removeIf(" +
                           LambdaUtil.createLambda(variable, condition) +
                           ");";
      Collection<PsiComment> comments = ContainerUtil.map(PsiTreeUtil.findChildrenOfType(loop, PsiComment.class),
                                                          comment -> (PsiComment)comment.copy());
      PsiElement result = loop.replace(JavaPsiFacade.getElementFactory(project).createStatementFromText(replacement, loop));
      if(previous != null) previous.delete();
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      CodeStyleManager.getInstance(project).reformat(result);
      comments.forEach(comment -> result.getParent().addBefore(comment, result));
    }
  }

  static class IteratorDeclaration {
    private final @NotNull PsiVariable myIterator;
    private final @Nullable PsiExpression myCollection;

    private IteratorDeclaration(@NotNull PsiVariable iterator, @Nullable PsiExpression collection) {
      myIterator = iterator;
      myCollection = collection;
    }

    public boolean isHasNextCall(PsiExpression condition) {
      return isIteratorMethodCall(condition, "hasNext");
    }

    boolean isIteratorMethodCall(PsiExpression candidate, String method) {
      if(!(candidate instanceof PsiMethodCallExpression)) return false;
      PsiMethodCallExpression call = (PsiMethodCallExpression)candidate;
      if(call.getArgumentList().getExpressions().length != 0) return false;
      PsiReferenceExpression expression = call.getMethodExpression();
      if(!method.equals(expression.getReferenceName())) return false;
      PsiExpression qualifier = expression.getQualifierExpression();
      if(!(qualifier instanceof PsiReferenceExpression)) return false;
      return ((PsiReferenceExpression)qualifier).resolve() == myIterator;
    }

    public PsiVariable getNextElementVariable(PsiStatement statement) {
      if(!(statement instanceof PsiDeclarationStatement)) return null;
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
      if(declaration.getDeclaredElements().length != 1) return null;
      PsiElement element = declaration.getDeclaredElements()[0];
      if(!(element instanceof PsiVariable)) return null;
      PsiVariable var = (PsiVariable)element;
      if(!isIteratorMethodCall(var.getInitializer(), "next")) return null;
      return var;
    }

    @Contract("null -> null")
    static IteratorDeclaration extract(PsiStatement statement) {
      if(!(statement instanceof PsiDeclarationStatement)) return null;
      PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
      if(declaration.getDeclaredElements().length != 1) return null;
      PsiElement element = declaration.getDeclaredElements()[0];
      if(!(element instanceof PsiVariable)) return null;
      PsiVariable variable = (PsiVariable)element;
      PsiExpression initializer = variable.getInitializer();
      if(!(initializer instanceof PsiMethodCallExpression)) return null;
      PsiMethodCallExpression call = (PsiMethodCallExpression)initializer;
      if(call.getArgumentList().getExpressions().length != 0) return null;
      PsiReferenceExpression methodExpression = call.getMethodExpression();
      if(!"iterator".equals(methodExpression.getReferenceName())) return null;
      PsiMethod method = call.resolveMethod();
      if(method == null || !InheritanceUtil.isInheritor(method.getContainingClass(), CommonClassNames.JAVA_UTIL_COLLECTION)) return null;
      PsiType type = variable.getType();
      if(!(type instanceof PsiClassType) || !((PsiClassType)type).rawType().equalsToText(CommonClassNames.JAVA_UTIL_ITERATOR)) return null;
      return new IteratorDeclaration(variable, methodExpression.getQualifierExpression());
    }
  }
}