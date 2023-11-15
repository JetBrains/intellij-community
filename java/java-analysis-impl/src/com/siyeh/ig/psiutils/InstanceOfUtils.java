// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.dataFlow.ContractValue;
import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public final class InstanceOfUtils {

  private InstanceOfUtils() {}

  public static PsiInstanceOfExpression getConflictingInstanceof(@Nullable PsiType castType,
                                                                 @NotNull PsiReferenceExpression operand,
                                                                 @NotNull PsiElement context) {
    if (!(castType instanceof PsiClassType classType)) {
      return null;
    }
    if (((PsiClassType)castType).resolve() instanceof PsiTypeParameter) {
      return null;
    }
    final PsiClassType rawType = classType.rawType();
    final InstanceofChecker checker = new InstanceofChecker(operand, rawType, false);
    PsiStatement sibling = PsiTreeUtil.getParentOfType(context, PsiStatement.class);
    sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    while (sibling != null) {
      if (sibling instanceof PsiIfStatement ifStatement) {
        final PsiExpression condition = ifStatement.getCondition();
        if (condition != null) {
          if (!ControlFlowUtils.statementMayCompleteNormally(ifStatement.getThenBranch())) {
            checker.negate = true;
            checker.checkExpression(condition);
            if (checker.hasAgreeingInstanceof()) {
              return null;
            }
          }
          else if (!ControlFlowUtils.statementMayCompleteNormally(ifStatement.getElseBranch())) {
            checker.negate = false;
            checker.checkExpression(condition);
            if (checker.hasAgreeingInstanceof()) {
              return null;
            }
          }
        }
      }
      else if (sibling instanceof PsiAssertStatement assertStatement) {
        final PsiExpression condition = assertStatement.getAssertCondition();
        checker.negate = false;
        checker.checkExpression(condition);
        if (checker.hasAgreeingInstanceof()) {
          return null;
        }
      }
      else if (sibling instanceof PsiExpressionStatement) {
        PsiMethodCallExpression call =
          ObjectUtils.tryCast(((PsiExpressionStatement)sibling).getExpression(), PsiMethodCallExpression.class);
        if (isInstanceOfAssertionCall(checker, call)) return null;
      }
      sibling = PsiTreeUtil.getPrevSiblingOfType(sibling, PsiStatement.class);
    }
    checker.negate = false;
    PsiElement parent = findInterestingParent(context);
    while (parent != null) {
      IElementType tokenType = parent instanceof PsiPolyadicExpression ? ((PsiPolyadicExpression)parent).getOperationTokenType() : null;
      if (JavaTokenType.ANDAND.equals(tokenType) || JavaTokenType.OROR.equals(tokenType)) {
        checker.negate = tokenType.equals(JavaTokenType.OROR);
        for (PsiExpression expression : ((PsiPolyadicExpression)parent).getOperands()) {
          if (PsiTreeUtil.isAncestor(expression, context, false)) break;
          expression.accept(checker);
          if (checker.hasAgreeingInstanceof()) {
            return null;
          }
        }
        checker.negate = false;
      }
      else {
        parent.accept(checker);
        if (checker.hasAgreeingInstanceof()) {
          return null;
        }
      }
      parent = findInterestingParent(parent);
    }
    if (checker.hasAgreeingInstanceof()) {
      return null;
    }
    return checker.getConflictingInstanceof();
  }

  @Nullable
  private static PsiElement findInterestingParent(@NotNull PsiElement context) {
    while (true) {
      PsiElement parent = context.getParent();
      if (parent == null) return null;
      if (parent instanceof PsiPolyadicExpression) {
        IElementType tokenType = ((PsiPolyadicExpression)parent).getOperationTokenType();
        if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) return parent;
      }
      if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() != context) {
        return parent;
      }
      if (parent instanceof PsiConditionalLoopStatement && ((PsiConditionalLoopStatement)parent).getCondition() != context) {
        return parent;
      }
      if (parent instanceof PsiConditionalExpression && ((PsiConditionalExpression)parent).getCondition() != context) {
        return parent;
      }
      context = parent;
    }
  }

  private static boolean isInstanceOfAssertionCall(@NotNull InstanceofChecker checker, @Nullable PsiMethodCallExpression call) {
    if (call == null) return false;
    List<? extends MethodContract> contracts = JavaMethodContractUtil.getMethodCallContracts(call);
    if (contracts.isEmpty()) return false;
    MethodContract contract = contracts.get(0);
    if (!contract.getReturnValue().isFail()) return false;
    ContractValue condition = ContainerUtil.getOnlyItem(contract.getConditions());
    if (condition == null) return false;
    checker.negate = true;
    OptionalInt argNum = condition.getArgumentComparedTo(ContractValue.booleanValue(true), true);
    if (argNum.isEmpty()) {
      checker.negate = false;
      argNum = condition.getArgumentComparedTo(ContractValue.booleanValue(false), true);
    }
    if (argNum.isEmpty()) return false;
    int index = argNum.getAsInt();
    PsiExpression[] args = call.getArgumentList().getExpressions();
    if (index >= args.length) return false;
    checker.checkExpression(args[index]);
    return checker.hasAgreeingInstanceof();
  }

  public static boolean hasAgreeingInstanceof(@NotNull PsiTypeCastExpression expression) {
    final PsiType castType = expression.getType();
    final PsiExpression operand = expression.getOperand();
    if (!(operand instanceof PsiReferenceExpression referenceExpression)) return false;
    final InstanceofChecker checker = new InstanceofChecker(referenceExpression, castType, false);
    PsiElement parent = findInterestingParent(expression);
    while (parent != null) {
      parent.accept(checker);
      if (checker.hasAgreeingInstanceof()) return true;
      parent = findInterestingParent(parent);
    }
    return false;
  }

  /**
   * @param cast a cast expression to find parent instanceof for
   * @return a traditional instanceof expression that is a candidate to introduce a pattern that covers given cast.
   */
  @Nullable
  public static PsiInstanceOfExpression findPatternCandidate(@NotNull PsiTypeCastExpression cast) {
    if (isUncheckedCast(cast)) return null;
    return findCorrespondingInstanceOf(cast);
  }

  public static boolean isUncheckedCast(@NotNull PsiTypeCastExpression cast) {
    PsiTypeElement castType = cast.getCastType();
    if (castType == null) return true;
    PsiExpression castOperand = cast.getOperand();
    if (castOperand == null) return true;
    PsiType type = castOperand.getType();
    if (type == null) return true;
    return JavaGenericsUtil.isUncheckedCast(castType.getType(), type);
  }

  /**
   * @param cast a cast expression to find parent instanceof for
   * @return an instanceof expression that checks for the same raw type as the cast.
   * Unlike {@link #findPatternCandidate(PsiTypeCastExpression)}, this may find a corresponding instanceof,
   * even if the cast is unchecked.
   */
  @Nullable
  public static PsiInstanceOfExpression findCorrespondingInstanceOf(@NotNull PsiTypeCastExpression cast) {
    PsiElement context = PsiUtil.skipParenthesizedExprUp(cast.getContext());
    if (context instanceof PsiLocalVariable) {
      context = context.getContext();
    } else {
      while (true) {
        if (context instanceof PsiPolyadicExpression polyadic) {
          IElementType tokenType = polyadic.getOperationTokenType();
          if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
            PsiInstanceOfExpression instanceOf = findInstanceOf(polyadic, cast, tokenType.equals(JavaTokenType.ANDAND));
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (context instanceof PsiConditionalExpression conditional) {
          PsiExpression condition = conditional.getCondition();
          if (!PsiTreeUtil.isAncestor(condition, cast, true)) {
            boolean whenTrue = PsiTreeUtil.isAncestor(conditional.getThenExpression(), cast, false);
            PsiInstanceOfExpression instanceOf = findInstanceOf(condition, cast, whenTrue);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if ((context instanceof PsiExpression && !(context instanceof PsiLambdaExpression)) ||
            context instanceof PsiExpressionList || context instanceof PsiLocalVariable ||
            context instanceof DummyHolder) {
          context = context.getContext();
          continue;
        }
        break;
      }
      if (!(context instanceof PsiStatement)) return null;
    }
    if (context == null) return null;
    PsiElement parent = context.getContext();
    if (parent instanceof PsiCodeBlock) {
      for (PsiElement stmt = context.getPrevSibling(); stmt != null; stmt = stmt.getPrevSibling()) {
        if (stmt instanceof PsiIfStatement ifStatement) {
          PsiStatement thenBranch = ifStatement.getThenBranch();
          PsiStatement elseBranch = ifStatement.getElseBranch();
          boolean thenCompletes = canCompleteNormally(parent, thenBranch);
          boolean elseCompletes = canCompleteNormally(parent, elseBranch);
          if (thenCompletes != elseCompletes) {
            PsiInstanceOfExpression instanceOf = findInstanceOf(ifStatement.getCondition(), cast, thenCompletes);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (stmt instanceof PsiWhileStatement || stmt instanceof PsiDoWhileStatement || stmt instanceof PsiForStatement) {
          PsiConditionalLoopStatement loop = (PsiConditionalLoopStatement)stmt;
          if (PsiTreeUtil.processElements(
            loop, e -> !(e instanceof PsiBreakStatement breakStatement) || breakStatement.findExitedStatement() != loop)) {
            PsiInstanceOfExpression instanceOf = findInstanceOf(loop.getCondition(), cast, false);
            if (instanceOf != null) {
              return instanceOf;
            }
          }
        }
        if (stmt instanceof PsiSwitchLabelStatementBase) break;
      }
      if (parent.getContext() instanceof PsiBlockStatement) {
        context = parent.getContext();
        parent = context.getContext();
      }
    }
    return processParent(cast, context, parent);
  }

  public static @Nullable PsiTypeElement findCheckTypeElement(@NotNull PsiInstanceOfExpression expression) {
    PsiTypeElement typeElement = expression.getCheckType();
    if (typeElement == null) {
      typeElement = JavaPsiPatternUtil.getPatternTypeElement(expression.getPattern());
    }
    return typeElement;
  }

  private static PsiInstanceOfExpression processParent(@NotNull PsiTypeCastExpression cast,
                                                       @NotNull PsiElement context,
                                                       @Nullable PsiElement parent) {
    if (parent instanceof PsiIfStatement ifStatement) {
      if (ifStatement.getThenBranch() == context) {
        return findInstanceOf(ifStatement.getCondition(), cast, true);
      }
      else if (ifStatement.getElseBranch() == context) {
        return findInstanceOf(ifStatement.getCondition(), cast, false);
      }
    }
    if (parent instanceof PsiForStatement || parent instanceof PsiWhileStatement) {
      return findInstanceOf(((PsiConditionalLoopStatement)parent).getCondition(), cast, true);
    }
    return null;
  }

  private static boolean canCompleteNormally(@NotNull PsiElement parent, @Nullable PsiStatement statement) {
    if (statement == null) return true;
    ControlFlow flow;
    try {
      flow = ControlFlowFactory.getControlFlow(parent, new LocalsControlFlowPolicy(parent), 
                                               ControlFlowOptions.NO_CONST_EVALUATE);
    }
    catch (AnalysisCanceledException e) {
      return true;
    }
    int startOffset = flow.getStartOffset(statement);
    int endOffset = flow.getEndOffset(statement);
    return startOffset != -1 && endOffset != -1 && ControlFlowUtil.canCompleteNormally(flow, startOffset, endOffset);
  }

  @Contract("null, _, _ -> null")
  private static PsiInstanceOfExpression findInstanceOf(@Nullable PsiExpression condition,
                                                        @NotNull PsiTypeCastExpression cast,
                                                        boolean whenTrue) {
    if (condition == null) return null;
    if (condition instanceof PsiParenthesizedExpression) {
      return findInstanceOf(((PsiParenthesizedExpression)condition).getExpression(), cast, whenTrue);
    }
    if (BoolUtils.isNegation(condition)) {
      return findInstanceOf(BoolUtils.getNegated(condition), cast, !whenTrue);
    }
    if (condition instanceof PsiPolyadicExpression polyadic) {
      IElementType tokenType = polyadic.getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND && whenTrue ||
          tokenType == JavaTokenType.OROR && !whenTrue) {
        for (PsiExpression operand : polyadic.getOperands()) {
          if (PsiTreeUtil.isContextAncestor(operand, cast, false)) return null;
          PsiInstanceOfExpression result = findInstanceOf(operand, cast, whenTrue);
          if (result != null) {
            return result;
          }
        }
      }
    }
    if (condition instanceof PsiInstanceOfExpression instanceOf && whenTrue) {
      PsiTypeElement typeElement = findCheckTypeElement(instanceOf);
      if (typeElement != null) {
        PsiType type = typeElement.getType();
        PsiType castType = Objects.requireNonNull(cast.getCastType()).getType();
        PsiExpression castOperand = Objects.requireNonNull(cast.getOperand());
        if (typeCompatible(type, castType, castOperand) &&
            PsiEquivalenceUtil.areElementsEquivalent(instanceOf.getOperand(), castOperand)) {
          return instanceOf;
        }
      }
    }
    return null;
  }

  public static boolean typeCompatible(@NotNull PsiType instanceOfType, @NotNull PsiType castType, @NotNull PsiExpression castOperand) {
    if (instanceOfType.equals(castType)) return true;
    if (castType instanceof PsiClassType) {
      PsiClassType rawType = ((PsiClassType)castType).rawType();
      if (instanceOfType.equals(rawType)) {
        return castOperand.getType() != null;
      }
    }
    return false;
  }

  /**
   * @param variable a variable, which is used to check if the scope contains variables with the same name
   * @param instanceOf an instanceof expression that is used to calculate declaration scope
   * @return true if other declared variables with the same name are found in the scope of instanceof with variable patterns.
   * {@link HighlightUtil#checkVariableAlreadyDefined(PsiVariable)} is used on copy files
   */
  public static boolean hasConflictingDeclaredNames(@NotNull PsiLocalVariable variable, @NotNull PsiInstanceOfExpression instanceOf) {
    PsiIdentifier identifier = variable.getNameIdentifier();
    if (identifier == null) {
      return false;
    }

    PsiElement scope = JavaSharedImplUtil.getPatternVariableDeclarationScope(instanceOf);
    if (scope == null) {
      return false;
    }
    boolean hasConflict = isConflictingNameDeclaredInside(variable, scope, false);
    if (hasConflict) {
      if (instanceOf.getPattern() != null) return true;
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(variable.getProject());
      PsiElement newExpression = factory.createExpressionFromText(instanceOf.getText() + " " + identifier.getText(), instanceOf);

      PsiFile file = variable.getContainingFile();
      if (file != instanceOf.getContainingFile()) {
        return true;
      }
      PsiFile copyFile = (PsiFile)file.copy();
      PsiInstanceOfExpression copyInstanceOf = PsiTreeUtil.findSameElementInCopy(instanceOf, copyFile);
      PsiLocalVariable copyVariable = PsiTreeUtil.findSameElementInCopy(variable, copyFile);
      copyVariable.delete();
      newExpression = copyInstanceOf.replace(newExpression);
      if (!(newExpression instanceof PsiInstanceOfExpression newInstanceOfExpression)) {
        return true;
      }
      if (!(newInstanceOfExpression.getPattern() instanceof PsiTypeTestPattern typeTestPattern)) {
        return true;
      }
      PsiPatternVariable patternVariable = typeTestPattern.getPatternVariable();
      if (patternVariable == null) {
        return true;
      }
      scope = JavaSharedImplUtil.getPatternVariableDeclarationScope(newInstanceOfExpression);
      if (scope == null) {
        return false;
      }
      return isConflictingNameDeclaredInside(patternVariable, scope, true);
    }
    return false;
  }

  /**
   * Checks if there are other variables declared with the same name inside a given element.
   *
   * @param myVariable        the variable to check for conflicts
   * @param element         the statement where to search for conflicts
   * @param checkRedeclared   true if re-declaration of the variable should be counted as conflict
   * @return true if other variables with the same name are found inside the statement, false otherwise
   */
  public static boolean isConflictingNameDeclaredInside(@Nullable PsiVariable myVariable,
                                                         @Nullable PsiElement element,
                                                         boolean checkRedeclared) {
    if (myVariable == null || element == null) return false;
    PsiIdentifier identifier = myVariable.getNameIdentifier();
    if (identifier == null) {
      return false;
    }
    HasDeclaredVariableWithTheSameNameVisitor visitor = new HasDeclaredVariableWithTheSameNameVisitor(myVariable, checkRedeclared);
    element.accept(visitor);
    return visitor.hasConflict;
  }

  private static class HasDeclaredVariableWithTheSameNameVisitor extends JavaRecursiveElementWalkingVisitor {
    boolean hasConflict = false;

    private final PsiVariable myVariable;
    private final PsiIdentifier myIdentifier;
    private final boolean myCheckRedeclared;

    private HasDeclaredVariableWithTheSameNameVisitor(@NotNull PsiVariable variable, boolean checkRedeclared) {
      myVariable = variable;
      myIdentifier = variable.getNameIdentifier();
      myCheckRedeclared = checkRedeclared;
      if (myIdentifier == null) {
        stopWalking();
      }
    }

    @Override
    public void visitClass(final @NotNull PsiClass aClass) {}

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      String name = variable.getName();
      if (name != null &&
          myVariable != variable &&
          myIdentifier.textMatches(name) &&
          (!myCheckRedeclared || HighlightUtil.checkVariableAlreadyDefined(variable) != null)) {
        hasConflict = true;
        stopWalking();
      }
      super.visitVariable(variable);
    }
  }

  private static class InstanceofChecker extends JavaElementVisitor {

    private final PsiReferenceExpression referenceExpression;
    private final PsiType castType;
    private final boolean strict;
    private boolean negate = false;
    private PsiInstanceOfExpression conflictingInstanceof = null;
    private boolean agreeingInstanceof = false;


    InstanceofChecker(PsiReferenceExpression referenceExpression,
                      PsiType castType, boolean strict) {
      this.referenceExpression = referenceExpression;
      this.castType = castType;
      this.strict = strict;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
        for (PsiExpression operand : expression.getOperands()) {
          checkExpression(operand);
          if (agreeingInstanceof) {
            return;
          }
        }
        if (!negate && conflictingInstanceof != null) {
          agreeingInstanceof = false;
        }
      }
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      processConditionalLoop(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      processConditionalLoop(statement);
    }

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      processConditionalLoop(statement);
    }

    private void processConditionalLoop(PsiConditionalLoopStatement loop) {
      PsiStatement body = loop.getBody();
      if (!PsiTreeUtil.isAncestor(body, referenceExpression, true)) return;
      if (isReassignedInside(body)) return;
      checkExpression(loop.getCondition());
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement ifStatement) {
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      negate = PsiTreeUtil.isAncestor(elseBranch, referenceExpression, true);
      if (isReassignedInside(negate ? elseBranch : ifStatement.getThenBranch())) return;
      checkExpression(ifStatement.getCondition());
    }

    private boolean isReassignedInside(PsiStatement branch) {
      return branch instanceof PsiBlockStatement && VariableAccessUtils.variableIsAssignedBeforeReference(referenceExpression, branch);
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      final PsiExpression elseExpression = expression.getElseExpression();
      negate = PsiTreeUtil.isAncestor(elseExpression, referenceExpression, true);
      checkExpression(expression.getCondition());
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      if (negate) return;
      if (isAgreeing(expression)) {
        agreeingInstanceof = true;
        conflictingInstanceof = null;
      }
      else if (isConflicting(expression) && conflictingInstanceof == null) {
        conflictingInstanceof = expression;
      }
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      PsiExpression operand = expression.getExpression();
      if (operand != null) {
        operand.accept(this);
      }
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      PsiExpression operand = expression.getOperand();
      if (operand != null && expression.getOperationTokenType().equals(JavaTokenType.EXCL)) {
        negate = !negate;
        operand.accept(this);
        negate = !negate;
      }
    }

    private void checkExpression(PsiExpression expression) {
      if (expression != null) {
        expression.accept(this);
      }
    }

    private boolean isConflicting(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) return false;
      final PsiType type = typeElement.getType();
      if (strict) {
        return !castType.equals(type);
      }
      return !castType.isAssignableFrom(type);
    }

    private boolean isAgreeing(PsiInstanceOfExpression expression) {
      final PsiExpression conditionOperand = expression.getOperand();
      if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(referenceExpression, conditionOperand)) {
        return false;
      }
      final PsiTypeElement typeElement = expression.getCheckType();
      if (typeElement == null) return false;
      final PsiType type = typeElement.getType();
      if (strict) {
        return castType.equals(type);
      }
      return castType.isAssignableFrom(type);
    }

    public boolean hasAgreeingInstanceof() {
      return agreeingInstanceof;
    }

    public PsiInstanceOfExpression getConflictingInstanceof() {
      return conflictingInstanceof;
    }
  }
}
