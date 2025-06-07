// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.SplitConditionUtil;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.fix.DeleteSwitchLabelFix;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.java.codeserver.core.JavaPsiSwitchUtil;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.psi.JavaTokenType.WHEN_KEYWORD;

public class SimplifyBooleanExpressionFix extends PsiUpdateModCommandAction<PsiExpression> {
  private static final Logger LOG = Logger.getInstance(SimplifyBooleanExpressionFix.class);

  enum SideEffectStatus {
    NO_SIDE_EFFECTS,
    CAN_BE_EXTRACTED,
    MAY_CHANGE_SEMANTICS,
    BREAKS_COMPILATION
  }

  private final boolean mySubExpressionValue;
  private final @NotNull SideEffectStatus mySideEffectStatus;

  public SimplifyBooleanExpressionFix(@NotNull PsiExpression subExpression, boolean subExpressionValue) {
    super(subExpression);
    mySubExpressionValue = subExpressionValue;
    mySideEffectStatus = computeStatus(subExpression);
  }

  private @IntentionName @NotNull String getText(@NotNull PsiExpression subExpression) {
    String suffix = switch (mySideEffectStatus) {
      case NO_SIDE_EFFECTS, BREAKS_COMPILATION -> "";
      case CAN_BE_EXTRACTED -> QuickFixBundle.message("simplify.boolean.expression.extracting.side.effects");
      case MAY_CHANGE_SEMANTICS -> JavaBundle.message("quickfix.text.suffix.may.change.semantics");
    };
    return getIntentionText(subExpression, mySubExpressionValue) + suffix;
  }

  private SideEffectStatus computeStatus(@NotNull PsiExpression expression) {
    return StreamEx.of(SideEffectChecker.extractSideEffectExpressions(expression))
      .map(sideEffectExpression -> {
        if (sideEffectExpression instanceof PsiInstanceOfExpression instanceOf) {
          return shouldIgnore(instanceOf, expression) ? SideEffectStatus.NO_SIDE_EFFECTS :
                 canExtractSideEffect(sideEffectExpression) ? SideEffectStatus.CAN_BE_EXTRACTED :
                 SideEffectStatus.BREAKS_COMPILATION;
        }
        else {
          return canExtractSideEffect(sideEffectExpression) ? SideEffectStatus.CAN_BE_EXTRACTED : SideEffectStatus.MAY_CHANGE_SEMANTICS;
        }
      })
      .max(Comparator.naturalOrder())
      .orElse(SideEffectStatus.NO_SIDE_EFFECTS);
  }

  private boolean shouldIgnore(PsiElement e, PsiExpression subExpression) {
    return e instanceof PsiInstanceOfExpression &&
           !ContainerUtil.exists(JavaPsiPatternUtil.getExposedPatternVariables(((PsiInstanceOfExpression)e)),
                                 var -> PatternResolveState.fromBoolean(mySubExpressionValue)
                                   .equals(PatternResolveState.stateAtParent(var, subExpression)) &&
                                        newTargetForPatternVariable(subExpression, var) == null);
  }

  private boolean canExtractSideEffect(PsiExpression subExpression) {
    if (CodeBlockSurrounder.canSurround(subExpression)) return true;
    if (!mySubExpressionValue) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(subExpression.getParent());
      if (parent instanceof PsiWhileStatement || parent instanceof PsiForStatement) return true;
      // code like "if (foo || alwaysFalseWithSideEffects) {}"
      return parent instanceof PsiPolyadicExpression polyadic && polyadic.getOperationTokenType().equals(JavaTokenType.OROR)
             && PsiTreeUtil.isAncestor(ArrayUtil.getLastElement(polyadic.getOperands()), subExpression, false)
             && PsiUtil.skipParenthesizedExprUp(parent.getParent()) instanceof PsiIfStatement;
    }
    return false;
  }

  public static @NotNull @IntentionName String getIntentionText(@NotNull PsiExpression expression, boolean constantValue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (constantValue && parent instanceof PsiSwitchLabelStatementBase switchLabel &&
        PsiTreeUtil.isAncestor(switchLabel.getGuardExpression(), expression, false)) {
      return CommonQuickFixBundle.message("fix.remove.guard");
    }
    if (parent instanceof PsiIfStatement) {
      return constantValue ?
             CommonQuickFixBundle.message("fix.unwrap.statement", JavaKeywords.IF) :
             CommonQuickFixBundle.message("fix.remove.statement", JavaKeywords.IF);
    }
    if (parent instanceof PsiSwitchLabelStatementBase && !constantValue) {
      return JavaAnalysisBundle.message("remove.switch.label");
    }
    if (!constantValue) {
      if (parent instanceof PsiWhileStatement) return CommonQuickFixBundle.message("fix.remove.statement", JavaKeywords.WHILE);
      if (parent instanceof PsiDoWhileStatement) return CommonQuickFixBundle.message("fix.unwrap.statement", "do-while");
      if (parent instanceof PsiForStatement) return CommonQuickFixBundle.message("fix.remove.statement", JavaKeywords.FOR);
    }
    return QuickFixBundle.message("simplify.boolean.expression.text", PsiExpressionTrimRenderer.render(expression), constantValue);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getFamilyNameText();
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpression expression) {
    if (!isAvailable(expression)) return null;
    return Presentation.of(getText(expression));
  }

  public boolean isAvailable(@NotNull PsiExpression expression) {
    if (PsiUtil.isAccessedForWriting(expression)) return false;
    if (mySideEffectStatus == SideEffectStatus.BREAKS_COMPILATION) return false;
    PsiElement element = PsiUtil.skipParenthesizedExprUp(expression);
    PsiElement parent = element == null ? null : element.getParent();
    if (parent instanceof PsiDoWhileStatement && containsBreakOrContinue((PsiDoWhileStatement)parent)) return false;
    PsiConditionalExpression parentConditionalExpr = ObjectUtils.tryCast(parent, PsiConditionalExpression.class);
    if (parentConditionalExpr != null && NullabilityProblemKind.fromContext(parentConditionalExpr, Collections.emptyMap()) != null) {
      PsiExpression exprToCheck = mySubExpressionValue ? parentConditionalExpr.getThenExpression()
                                                       : parentConditionalExpr.getElseExpression();
      return exprToCheck == null || !TypeConversionUtil.isNullType(exprToCheck.getType());
    }
    return true;
  }

  private static @Nullable PsiInstanceOfExpression newTargetForPatternVariable(@NotNull PsiExpression expression,
                                                                               @NotNull PsiPatternVariable variable) {
    if (!(variable.getPattern() instanceof PsiTypeTestPattern typeTest)) return null;
    PsiTypeElement checkType = typeTest.getCheckType();
    if (checkType == null) return null;
    if (!(typeTest.getParent() instanceof PsiInstanceOfExpression instanceOf)) return null;

    PsiTypeCastExpression cast =
      (PsiTypeCastExpression)JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText("(a)b", expression);
    Objects.requireNonNull(cast.getCastType()).replace(checkType);
    Objects.requireNonNull(cast.getOperand()).replace(instanceOf.getOperand());
    PsiInstanceOfExpression candidate = InstanceOfUtils.findPatternCandidate(cast, variable);
    if (candidate == null) return null;
    PsiPrimaryPattern pattern = candidate.getPattern();
    if (pattern != null) {
      if (!(pattern instanceof PsiTypeTestPattern existingTypeTest)) return null;
      PsiPatternVariable existingVar = existingTypeTest.getPatternVariable();
      if (existingVar != null && VariableAccessUtils.variableIsAssigned(existingVar)) return null;
    }
    return candidate;
  }

  private static boolean containsBreakOrContinue(PsiDoWhileStatement doWhileLoop) {
    return SyntaxTraverser.psiTraverser(doWhileLoop).filter(e -> isBreakOrContinue(e, doWhileLoop)).iterator().hasNext();
  }

  private static boolean isBreakOrContinue(PsiElement e, PsiDoWhileStatement doWhileLoop) {
    return e instanceof PsiBreakStatement && doWhileLoop == ((PsiBreakStatement)e).findExitedStatement() ||
           e instanceof PsiContinueStatement && doWhileLoop == ((PsiContinueStatement)e).findContinuedStatement();
  }

  /**
   * Tries to replace a boolean expression with a given value, simplifying code further if possible (e.g., unwrapping if statement).
   * May do nothing if simplification is not possible (e.g., expression is l-value).
   *
   * @param expression expression to simplify
   * @param value      expected value of the expression
   */
  public static void trySimplify(@NotNull PsiExpression expression, boolean value) {
    SimplifyBooleanExpressionFix fix = new SimplifyBooleanExpressionFix(expression, value);
    if (fix.isAvailable(expression)) {
      fix.invoke(expression);
    }
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpression subExpression, @NotNull ModPsiUpdater updater) {
    invoke(subExpression);
  }

  public void invoke(@NotNull PsiExpression subExpression) {
    CommentTracker ct = new CommentTracker();
    processPatternVariables(subExpression);
    if (SideEffectChecker.mayHaveSideEffects(subExpression) && canExtractSideEffect(subExpression)) {
      PsiExpression orig = subExpression;
      subExpression = ensureCodeBlock(subExpression.getProject(), subExpression);
      if (subExpression == null) {
        LOG.error("ensureCodeBlock returned null", new Attachment("subExpression.txt", orig.getText()));
        return;
      }
      PsiStatement anchor = ObjectUtils.tryCast(CommonJavaRefactoringUtil.getParentStatement(subExpression, false), PsiStatement.class);
      if (anchor == null) {
        LOG.error("anchor is null", new Attachment("subExpression.txt", subExpression.getText()));
        return;
      }
      PsiExpression finalSubExpression = subExpression;
      List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(
        subExpression, e -> shouldIgnore(e, finalSubExpression));
      sideEffects.forEach(ct::markUnchanged);
      PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, subExpression);
      if (statements.length > 0) {
        BlockUtils.addBefore(anchor, statements);
      }
      if (!subExpression.isValid()) {
        LOG.error("subExpression became invalid", new Attachment("subExpression.txt", subExpression.getText()));
        return;
      }
    }
    PsiExpression expression = (PsiExpression)ct.replaceAndRestoreComments(subExpression, Boolean.toString(mySubExpressionValue));
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    simplifyExpression(expression);
  }

  private static void processPatternVariables(@NotNull PsiExpression subExpression) {
    List<PsiPatternVariable> variables = JavaPsiPatternUtil.getExposedPatternVariables(subExpression);
    for (PsiPatternVariable variable : variables) {
      retargetPatternVariable(subExpression, variable);
    }
  }

  private static void retargetPatternVariable(@NotNull PsiExpression subExpression, @NotNull PsiPatternVariable variable) {
    List<PsiReferenceExpression> refs = VariableAccessUtils.getVariableReferences(variable);
    if (refs.isEmpty()) return;
    PsiInstanceOfExpression target = newTargetForPatternVariable(subExpression, variable);
    if (target == null) return;
    if (target.getPattern() instanceof PsiTypeTestPattern existingPattern) {
      PsiPatternVariable existingVar = existingPattern.getPatternVariable();
      if (existingVar != null) {
        for (PsiReferenceExpression ref : refs) {
          if (ref.isValid()) {
            ref.handleElementRename(existingVar.getName());
          }
        }
        return;
      }
    }
    PsiInstanceOfExpression updated = (PsiInstanceOfExpression)JavaPsiFacade.getElementFactory(subExpression.getProject())
      .createExpressionFromText("x instanceof T t", target);
    updated.getOperand().replace(target.getOperand());
    PsiTypeTestPattern newPattern = (PsiTypeTestPattern)Objects.requireNonNull(updated.getPattern());
    PsiTypeElement checkType = target.getCheckType();
    if (checkType == null) return;
    PsiPatternVariable newVariable = (PsiPatternVariable)Objects.requireNonNull(newPattern.getPatternVariable()).replace(variable);
    Objects.requireNonNull(newPattern.getCheckType()).replace(checkType);
    variable.delete();
    String name = new VariableNameGenerator(target, VariableKind.LOCAL_VARIABLE).byName(variable.getName())
      .generate(true);
    if (!name.equals(newVariable.getName())) {
      newVariable.setName(name);
      for (PsiReferenceExpression ref : refs) {
        if (ref.isValid()) {
          ref.handleElementRename(name);
        }
      }
    }
    target.replace(updated);
  }

  private PsiExpression ensureCodeBlock(@NotNull Project project, PsiExpression subExpression) {
    if (!mySubExpressionValue) {
      // Prevent extracting while condition to internal 'if'
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(subExpression.getParent());
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (parent instanceof PsiWhileStatement whileStatement) {
        if (whileStatement.getCondition() != null) {
          PsiStatement replacement =
            factory.createStatementFromText("if(" + whileStatement.getCondition().getText() + ");", whileStatement);
          PsiIfStatement ifStatement = (PsiIfStatement)whileStatement.replace(replacement);
          subExpression = Objects.requireNonNull(ifStatement.getCondition());
        }
      }
      else if (parent instanceof PsiPolyadicExpression polyadicExpression &&
               JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
        PsiExpression expression = expandLastIfDisjunct(polyadicExpression, subExpression, factory);
        if (expression != null) {
          return expression;
        }
      }
    }
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(subExpression);
    return surrounder == null ? null : surrounder.surround().getExpression();
  }

  private static @Nullable PsiExpression expandLastIfDisjunct(PsiPolyadicExpression orChain,
                                                              PsiExpression subExpression,
                                                              PsiElementFactory factory) {
    PsiIfStatement ifStatement = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprUp(orChain.getParent()), PsiIfStatement.class);
    if (ifStatement == null) return null;
    PsiExpression lastOperand = ArrayUtil.getLastElement(orChain.getOperands());
    if (!PsiTreeUtil.isAncestor(lastOperand, subExpression, false)) return null;
    orChain.replace(SplitConditionUtil.getLOperands(orChain, Objects.requireNonNull(orChain.getTokenBeforeOperand(lastOperand))));
    ControlFlowUtils.ensureElseBranch(ifStatement);
    PsiBlockStatement elseBranch = (PsiBlockStatement)Objects.requireNonNull(ifStatement.getElseBranch());
    PsiCodeBlock codeBlock = elseBranch.getCodeBlock();
    PsiStatement replacement = factory.createStatementFromText("if(" + subExpression.getText() + ");", ifStatement);
    PsiIfStatement alwaysFalseIf = (PsiIfStatement)codeBlock.addAfter(replacement, codeBlock.getLBrace());
    return Objects.requireNonNull(alwaysFalseIf.getCondition());
  }

  private static boolean simplifyIfOrLoopStatement(final PsiExpression expression) throws IncorrectOperationException {
    boolean condition = Boolean.parseBoolean(expression.getText());
    if (!(expression instanceof PsiLiteralExpression) || !PsiTypes.booleanType().equals(expression.getType())) return false;

    PsiElement parent = expression.getParent();
    if (parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getCondition() == expression) {
      simplifyIfStatement(condition, (PsiIfStatement)parent);
      return true;
    }
    if (parent instanceof PsiWhileStatement && !condition) {
      parent.delete();
      return true;
    }
    if (parent instanceof PsiDoWhileStatement && !condition) {
      replaceWithStatements((PsiDoWhileStatement)parent, ((PsiDoWhileStatement)parent).getBody());
      return true;
    }
    if (parent instanceof PsiForStatement && !condition) {
      simplifyForStatement(parent);
      return true;
    }

    return false;
  }

  private static void simplifyForStatement(PsiElement parent) {
    PsiStatement initialization = ((PsiForStatement)parent).getInitialization();
    if (initialization != null && !SyntaxTraverser.psiTraverser(initialization).filter(PsiExpression.class).filter(SideEffectChecker::mayHaveSideEffects).isEmpty()) {
      replaceWithStatements((PsiForStatement)parent, initialization);
    } else {
      parent.delete();
    }
  }

  private static void simplifyIfStatement(boolean conditionAlwaysTrue, PsiIfStatement ifStatement) {
    if (conditionAlwaysTrue) {
      replaceWithStatements(ifStatement, ifStatement.getThenBranch());
    }
    else {
      PsiStatement elseBranch = ifStatement.getElseBranch();
      if (elseBranch == null) {
        ifStatement.delete();
      }
      else {
        replaceWithStatements(ifStatement, elseBranch);
      }
    }
  }

  private static void replaceWithStatements(@NotNull PsiStatement orig, @Nullable PsiStatement statement)
    throws IncorrectOperationException {
    if (statement == null) {
      orig.delete();
      return;
    }
    PsiElement parent = orig.getParent();
    if (parent == null) return;

    PsiElement grandParent = parent.getParent();
    if (parent instanceof PsiCodeBlock && blockAlwaysReturns(statement)) {
      removeFollowingStatements(orig, (PsiCodeBlock)parent);
    }
    else if (grandParent instanceof PsiCodeBlock && parent instanceof PsiIfStatement ifStmt) {
      if (ifStmt.getElseBranch() == orig && blockAlwaysReturns(ifStmt.getThenBranch()) && blockAlwaysReturns(statement)) {
        removeFollowingStatements(ifStmt, (PsiCodeBlock)grandParent);
      }
    }

    if (parent instanceof PsiCodeBlock) {
      if (statement instanceof PsiBlockStatement) {
        // See IDEADEV-24277
        // Code block can only be inlined into another (parent) code block.
        // Code blocks, which are if or loop statement branches should not be inlined.
        PsiCodeBlock codeBlock = ((PsiBlockStatement)statement).getCodeBlock();
        if (!BlockUtils.containsConflictingDeclarations(codeBlock, (PsiCodeBlock)parent)) {
          BlockUtils.inlineCodeBlock(orig, codeBlock);
          return;
        }
      }
      if (hasConflictingDeclarations(statement, (PsiCodeBlock)parent)) {
        orig.replace(wrapWithCodeBlock(statement));
        return;
      }
    }
    orig.replace(statement);
  }

  private static boolean hasConflictingDeclarations(@Nullable PsiStatement statement, PsiCodeBlock parent) {
    return statement instanceof PsiDeclarationStatement &&
           ContainerUtil.exists(((PsiDeclarationStatement)statement).getDeclaredElements(), e -> isConflictingLocalVariable(parent, e));
  }

  private static boolean isConflictingLocalVariable(PsiCodeBlock parent, PsiElement declaration) {
    if (!(declaration instanceof PsiLocalVariable)) return false;
    String name = ((PsiLocalVariable)declaration).getName();
    return PsiResolveHelper.getInstance(declaration.getProject()).resolveAccessibleReferencedVariable(name, parent) != null;
  }

  private static PsiBlockStatement wrapWithCodeBlock(PsiStatement replacement) {
    PsiBlockStatement newBlock = BlockUtils.createBlockStatement(replacement.getProject());
    newBlock.getCodeBlock().add(replacement);
    return newBlock;
  }

  private static boolean blockAlwaysReturns(@Nullable PsiStatement statement) {
    if (statement == null) return false;
    try {
      return ControlFlowUtil.returnPresent(ControlFlowFactory.getControlFlowNoConstantEvaluate(statement));
    }
    catch (AnalysisCanceledException e) {
      return false;
    }
  }

  private static void removeFollowingStatements(@NotNull PsiStatement anchor, @NotNull PsiCodeBlock parentBlock) {
    PsiStatement[] siblingStatements = parentBlock.getStatements();
    List<PsiStatement> statements = Arrays.asList(siblingStatements);
    int ifIndex = statements.indexOf(anchor);
    if (ifIndex >= 0 && ifIndex < siblingStatements.length - 1) {
      int labelIndex = ContainerUtil.indexOf(statements.subList(ifIndex, statements.size()), st -> st instanceof PsiSwitchLabelStatement);
      int limit = labelIndex != -1 ? labelIndex + ifIndex : siblingStatements.length;
      int startOffset = ifIndex + 1;
      int endOffset = limit - 1;
      if (startOffset <= endOffset) {
        parentBlock.deleteChildRange(siblingStatements[startOffset], siblingStatements[endOffset]);
      }
    }
  }

  public static void simplifyExpression(PsiExpression expression) throws IncorrectOperationException {
    final PsiExpression result = createSimplifiedReplacement(expression);
    PsiExpression newExpression = (PsiExpression)new CommentTracker().replaceAndRestoreComments(expression, result);
    if (newExpression instanceof PsiLiteralExpression literal) {
      final PsiElement parent = newExpression.getParent();
      Object value = literal.getValue();
      if (parent instanceof PsiAssertStatement && Boolean.TRUE.equals(value)) {
        parent.delete();
        return;
      }
      if (parent instanceof PsiSwitchLabelStatementBase label && PsiTreeUtil.isAncestor(label.getGuardExpression(), newExpression, false)) {
        if (Boolean.TRUE.equals(value)) {
          deleteUnnecessaryGuard(label);
          return;
        }
        if (Boolean.FALSE.equals(value)) {
          DeleteSwitchLabelFix.deleteLabel(label);
          return;
        }
      }
    }
    if (!simplifyIfOrLoopStatement(newExpression)) {
      ParenthesesUtils.removeParentheses(newExpression, false);
    }
  }

  private static void deleteUnnecessaryGuard(@NotNull PsiSwitchLabelStatementBase label) {
    CommentTracker tracker = new CommentTracker();
    PsiExpression guardExpression = label.getGuardExpression();
    if (guardExpression == null) return;
    PsiKeyword psiKeyword = PsiTreeUtil.getPrevSiblingOfType(guardExpression, PsiKeyword.class);

    if (psiKeyword != null && psiKeyword.getTokenType() == WHEN_KEYWORD) {
      tracker.delete(psiKeyword);
    }
    tracker.delete(guardExpression);
    PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(label, PsiSwitchBlock.class);
    if (switchBlock == null) return;
    PsiExpression selector = switchBlock.getExpression();
    if (selector == null) return;
    PsiType selectorType = selector.getType();
    if (selectorType == null) return;
    PsiCaseLabelElementList elementList = label.getCaseLabelElementList();
    if (elementList == null) return;
    PsiCaseLabelElement target = null;
    for (PsiCaseLabelElement element : elementList.getElements()) {
      boolean isUnconditional = JavaPsiPatternUtil.isUnconditionalForType(element, selectorType, false);
      if (isUnconditional) {
        target = element;
        break;
      }
    }
    if (target == null) return;
    boolean afterTarget = false;
    for (PsiElement element : JavaPsiSwitchUtil.getSwitchBranches(switchBlock)) {
      if (!element.isValid()) continue;
      if (element == target) {
        afterTarget = true;
        continue;
      }
      if (!afterTarget) continue;
      if (element instanceof PsiSwitchLabelStatementBase base && base.isDefaultCase()) {
        tracker.delete(element);
        continue;
      }
      boolean deleteCaseElement = false;
      if (element instanceof PsiCaseLabelElement caseLabelElement &&
          (caseLabelElement instanceof PsiDefaultCaseLabelElement ||
           JavaPsiPatternUtil.isUnconditionalForType(caseLabelElement, selectorType, false) ||
           JavaPsiSwitchUtil.isDominated(caseLabelElement, target, selectorType))) {
        deleteCaseElement = true;
      }
      if (deleteCaseElement) {
        PsiSwitchLabelStatementBase statementBase = PsiTreeUtil.getParentOfType(element, PsiSwitchLabelStatementBase.class);
        if (statementBase == null) continue;
        PsiCaseLabelElementList labelElementList = statementBase.getCaseLabelElementList();
        if (labelElementList == null) continue;
        if (labelElementList.getElements().length == 1) {
          tracker.delete(statementBase);
        }
        else {
          tracker.delete(element);
        }
      }
    }
  }

  private static PsiExpression createSimplifiedReplacement(PsiExpression expression) {
    final PsiExpression[] result = {(PsiExpression)expression.copy()};
    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), true);
    final IncorrectOperationException[] exception = {null};
    result[0].accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        // read in all children in advance since due to element replacement involving its siblings invalidation
        PsiElement[] children = element.getChildren();
        for (PsiElement child : children) {
          child.accept(this);
        }
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
        expression.accept(expressionVisitor);
        if (expressionVisitor.resultExpression != null) {
          LOG.assertTrue(expressionVisitor.resultExpression.isValid());
          try {
            if (expression != result[0]) {
              expression.replace(expressionVisitor.resultExpression);
            }
            else {
              result[0] = expressionVisitor.resultExpression;
            }
          }
          catch (IncorrectOperationException e) {
            exception[0] = e;
          }
        }
      }
    });
    if (exception[0] != null) {
      throw exception[0];
    }
    return result[0];
  }

  public static boolean canBeSimplified(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiConditionalExpression) && !PsiTypes.booleanType().equals(expression.getType())) return false;
    PsiElement parent = expression.getParent();
    if (parent instanceof PsiLambdaExpression &&
        !LambdaUtil.isSafeLambdaBodyReplacement((PsiLambdaExpression)parent, () -> createSimplifiedReplacement(expression))) {
      return false;
    }

    final ExpressionVisitor expressionVisitor = new ExpressionVisitor(expression.getManager(), false);
    final Ref<Boolean> canBeSimplified = new Ref<>(Boolean.FALSE);
    expression.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!canBeSimplified.get().booleanValue()) {
          super.visitElement(element);
        }
      }

      @Override
      public void visitExpression(@NotNull PsiExpression expression) {
        super.visitExpression(expression);
        expressionVisitor.clear();
        expression.accept(expressionVisitor);
        if (expressionVisitor.canBeSimplifiedFlag) {
          canBeSimplified.set(Boolean.TRUE);
        }
      }
    });
    return canBeSimplified.get().booleanValue();
  }

  private static final class ExpressionVisitor extends JavaElementVisitor {
    private static final TokenSet SIMPLIFIABLE_POLYADIC_TOKENS = TokenSet.create(
      JavaTokenType.AND, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.OROR, JavaTokenType.XOR, JavaTokenType.EQEQ,
      JavaTokenType.NE);
    private PsiExpression resultExpression;
    private final PsiExpression trueExpression;
    private final PsiExpression falseExpression;
    private final boolean isCreateResult;
    boolean canBeSimplifiedFlag;

    private ExpressionVisitor(PsiManager psiManager, final boolean createResult) {
      isCreateResult = createResult;
      trueExpression = createResult ? createExpression(psiManager, Boolean.toString(true)) : null;
      falseExpression = createResult ? createExpression(psiManager, Boolean.toString(false)) : null;
    }

    private static PsiExpression createExpression(final PsiManager psiManager, @NonNls String text) {
      try {
        return JavaPsiFacade.getElementFactory(psiManager.getProject()).createExpressionFromText(text, null);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    private boolean markAndCheckCreateResult() {
      canBeSimplifiedFlag = true;
      return isCreateResult;
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      PsiExpression[] operands = expression.getOperands();
      PsiExpression lExpr = operands[0];
      IElementType tokenType = expression.getOperationTokenType();
      if (!SIMPLIFIABLE_POLYADIC_TOKENS.contains(tokenType)) return;
      if (JavaTokenType.XOR == tokenType) {

        boolean negate = false;
        List<PsiExpression> expressions = new ArrayList<>();
        for (PsiExpression operand : operands) {
          final Boolean constBoolean = getConstBoolean(operand);
          if (constBoolean != null) {
            markAndCheckCreateResult();
            if (constBoolean) {
              negate = !negate;
            }
            continue;
          }
          expressions.add(operand);
        }
        if (expressions.isEmpty()) {
          resultExpression = negate ? trueExpression : falseExpression;
        }
        else {
          String simplifiedText = StringUtil.join(expressions, PsiElement::getText, " ^ ");
          if (negate) {
            if (expressions.size() > 1) {
              simplifiedText = "!(" + simplifiedText + ")";
            }
            else {
              simplifiedText = BoolUtils.getNegatedExpressionText(expressions.get(0));
            }
          }
          resultExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(simplifiedText, expression);
        }
      }
      else {
        for (int i = 1; i < operands.length; i++) {
          Boolean l = getConstBoolean(lExpr);
          PsiExpression operand = operands[i];
          Boolean r = getConstBoolean(operand);
          if (l != null) {
            simplifyBinary(tokenType, l, operand);
          }
          else if (r != null) {
            simplifyBinary(tokenType, r, lExpr);
          }
          else {
            final PsiJavaToken javaToken = expression.getTokenBeforeOperand(operand);
            if (javaToken != null && !PsiTreeUtil.hasErrorElements(operand) && !PsiTreeUtil.hasErrorElements(lExpr)) {
              try {
                resultExpression = JavaPsiFacade.getElementFactory(expression.getProject())
                  .createExpressionFromText(lExpr.getText() + javaToken.getText() + operand.getText(), expression);
              }
              catch (IncorrectOperationException e) {
                resultExpression = null;
              }
            }
            else {
              resultExpression = null;
            }
          }
          if (resultExpression != null) {
            lExpr = resultExpression;
          }
        }
      }
    }

    private void simplifyBinary(IElementType tokenType, Boolean lConstBoolean, PsiExpression rOperand) {
      if (!markAndCheckCreateResult()) {
        return;
      }
      if (JavaTokenType.ANDAND == tokenType || JavaTokenType.AND == tokenType) {
        if (lConstBoolean.booleanValue()) {
          resultExpression = rOperand;
        }
        else if (!SideEffectChecker.mayHaveSideEffects(rOperand)) {
          resultExpression = falseExpression;
        }
      }
      else if (JavaTokenType.OROR == tokenType || JavaTokenType.OR == tokenType) {
        if (!lConstBoolean.booleanValue()) {
          resultExpression = rOperand;
        }
        else if (!SideEffectChecker.mayHaveSideEffects(rOperand)) {
          resultExpression = trueExpression;
        }
      }
      else if (JavaTokenType.EQEQ == tokenType) {
        simplifyEquation(lConstBoolean, rOperand);
      }
      else if (JavaTokenType.NE == tokenType) {
        PsiPrefixExpression negatedExpression = createNegatedExpression(rOperand);
        resultExpression = negatedExpression;
        visitPrefixExpression(negatedExpression);
        simplifyEquation(lConstBoolean, resultExpression);
      }
    }

    private void simplifyEquation(Boolean constBoolean, PsiExpression otherOperand) {
      if (constBoolean.booleanValue()) {
        resultExpression = otherOperand;
      }
      else {
        PsiPrefixExpression negated = createNegatedExpression(otherOperand);
        resultExpression = negated;
        visitPrefixExpression(negated);
      }
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      Boolean condition = getConstBoolean(expression.getCondition());
      if (condition == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = condition.booleanValue() ? expression.getThenExpression() : expression.getElseExpression();
    }

    private static PsiPrefixExpression createNegatedExpression(PsiExpression otherOperand) {
      PsiPrefixExpression expression = (PsiPrefixExpression)createExpression(otherOperand.getManager(), "!(xxx)");
      assert expression != null;
      PsiExpression operand = expression.getOperand();
      assert operand != null;
      try {
        operand.replace(otherOperand);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return expression;
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      PsiExpression operand = expression.getOperand();
      Boolean constBoolean = getConstBoolean(operand);
      if (constBoolean == null) return;
      IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.EXCL == tokenType) {
        if (!markAndCheckCreateResult()) {
          return;
        }
        resultExpression = constBoolean.booleanValue() ? falseExpression : trueExpression;
      }
    }


    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      PsiExpression subExpr = expression.getExpression();
      Boolean constBoolean = getConstBoolean(subExpr);
      if (constBoolean == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void clear() {
      resultExpression = null;
    }
  }

  public static Boolean getConstBoolean(PsiExpression operand) {
    if (operand == null) return null;
    operand = PsiUtil.deparenthesizeExpression(operand);
    if (operand == null) return null;
    String text = operand.getText();
    return JavaKeywords.TRUE.equals(text) ? Boolean.TRUE : JavaKeywords.FALSE.equals(text) ? Boolean.FALSE : null;
  }

  public static @IntentionFamilyName String getFamilyNameText() {
    return QuickFixBundle.message("simplify.boolean.expression.family");
  }
}
