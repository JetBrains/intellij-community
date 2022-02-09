// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.impl.SplitConditionUtil;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SimplifyBooleanExpressionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(SimplifyBooleanExpressionFix.class);

  private final boolean mySubExpressionValue;

  public SimplifyBooleanExpressionFix(@NotNull PsiExpression subExpression, boolean subExpressionValue) {
    super(subExpression);
    mySubExpressionValue = subExpressionValue;
  }

  @Override
  @NotNull
  public String getText() {
    PsiExpression subExpression = getSubExpression();
    if (subExpression == null) {
      return getFamilyName();
    }
    String suffix = "";
    if (SideEffectChecker.mayHaveSideEffects(subExpression, e -> shouldIgnore(e, subExpression))) {
      suffix = canExtractSideEffect(subExpression) ? QuickFixBundle.message("simplify.boolean.expression.extracting.side.effects")
                                                   : JavaBundle.message("quickfix.text.suffix.may.change.semantics");
    }
    return getIntentionText(subExpression, mySubExpressionValue) + suffix;
  }

  private boolean shouldIgnore(PsiElement e, PsiExpression subExpression) {
    return e instanceof PsiInstanceOfExpression &&
           !ContainerUtil.exists(JavaPsiPatternUtil.getExposedPatternVariables(((PsiInstanceOfExpression)e)),
                                 var -> PatternResolveState.fromBoolean(mySubExpressionValue)
                                   .equals(PatternResolveState.stateAtParent(var, subExpression)));
  }

  private boolean canExtractSideEffect(PsiExpression subExpression) {
    if (CodeBlockSurrounder.canSurround(subExpression)) return true;
    if (!mySubExpressionValue) {
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(subExpression.getParent());
      if (parent instanceof PsiWhileStatement || parent instanceof PsiForStatement) return true;
      // code like "if (foo || alwaysFalseWithSideEffects) {}"
      if (parent instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
        if (polyadic.getOperationTokenType().equals(JavaTokenType.OROR)
            && PsiTreeUtil.isAncestor(ArrayUtil.getLastElement(polyadic.getOperands()), subExpression, false)
            && PsiUtil.skipParenthesizedExprUp(parent.getParent()) instanceof PsiIfStatement) {
          return true;
        }
      }
    }
    return false;
  }

  @NotNull
  public static @IntentionName String getIntentionText(@NotNull PsiExpression expression, boolean constantValue) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiIfStatement) {
      return constantValue ?
             CommonQuickFixBundle.message("fix.unwrap.statement", PsiKeyword.IF) :
             CommonQuickFixBundle.message("fix.remove.statement", PsiKeyword.IF);
    }
    if (!constantValue) {
      if (parent instanceof PsiWhileStatement) return CommonQuickFixBundle.message("fix.remove.statement", PsiKeyword.WHILE);
      if (parent instanceof PsiDoWhileStatement) return CommonQuickFixBundle.message("fix.unwrap.statement", "do-while");
      if (parent instanceof PsiForStatement) return CommonQuickFixBundle.message("fix.remove.statement", PsiKeyword.FOR);
    }
    return QuickFixBundle.message("simplify.boolean.expression.text", PsiExpressionTrimRenderer.render(expression), constantValue);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getFamilyNameText();
  }

  @Override
  public boolean isAvailable() {
    PsiExpression expression = getSubExpression();
    if (!super.isAvailable() ||
        expression == null ||
        !BaseIntentionAction.canModify(expression) ||
        PsiUtil.isAccessedForWriting(expression)) {
      return false;
    }
    PsiElement element = PsiUtil.skipParenthesizedExprUp(expression);
    PsiElement parent = element == null ? null : element.getParent();
    if (parent instanceof PsiDoWhileStatement && containsBreakOrContinue((PsiDoWhileStatement)parent)) {
      return false;
    }
    PsiConditionalExpression parentConditionalExpr = ObjectUtils.tryCast(parent, PsiConditionalExpression.class);
    if (parentConditionalExpr != null && NullabilityProblemKind.fromContext(parentConditionalExpr, Collections.emptyMap()) != null) {
      PsiExpression exprToCheck = mySubExpressionValue ? parentConditionalExpr.getThenExpression()
                                                       : parentConditionalExpr.getElseExpression();
      if (exprToCheck != null && TypeConversionUtil.isNullType(exprToCheck.getType())) {
        return false;
      }
    }
    return true;
  }

  private static boolean containsBreakOrContinue(PsiDoWhileStatement doWhileLoop) {
    return SyntaxTraverser.psiTraverser(doWhileLoop).filter(e -> isBreakOrContinue(e, doWhileLoop)).iterator().hasNext();
  }

  private static boolean isBreakOrContinue(PsiElement e, PsiDoWhileStatement doWhileLoop) {
    return e instanceof PsiBreakStatement && doWhileLoop == ((PsiBreakStatement)e).findExitedStatement() ||
           e instanceof PsiContinueStatement && doWhileLoop == ((PsiContinueStatement)e).findContinuedStatement();
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiFile file, @Nullable Editor editor,
                     @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    if (!isAvailable()) return;
    PsiExpression subExpression = getSubExpression();
    if (subExpression == null) return;
    CommentTracker ct = new CommentTracker();
    if (SideEffectChecker.mayHaveSideEffects(subExpression) && canExtractSideEffect(subExpression)) {
      subExpression = ensureCodeBlock(project, subExpression);
      if (subExpression == null) {
        LOG.error("ensureCodeBlock returned null", new Attachment("subExpression.txt", getSubExpression().getText()));
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

  private PsiExpression ensureCodeBlock(@NotNull Project project, PsiExpression subExpression) {
    if (!mySubExpressionValue) {
      // Prevent extracting while condition to internal 'if'
      PsiElement parent = PsiUtil.skipParenthesizedExprUp(subExpression.getParent());
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (parent instanceof PsiWhileStatement) {
        PsiWhileStatement whileStatement = (PsiWhileStatement)parent;
        if (whileStatement.getCondition() != null) {
          PsiStatement replacement =
            factory.createStatementFromText("if(" + whileStatement.getCondition().getText() + ");", whileStatement);
          PsiIfStatement ifStatement = (PsiIfStatement)whileStatement.replace(replacement);
          subExpression = Objects.requireNonNull(ifStatement.getCondition());
        }
      }
      else if (parent instanceof PsiPolyadicExpression) {
        PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
        if (JavaTokenType.OROR.equals(polyadicExpression.getOperationTokenType())) {
          PsiExpression expression = expandLastIfDisjunct(polyadicExpression, subExpression, factory);
          if (expression != null) {
            return expression;
          }
        }
      }
    }
    CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(subExpression);
    return surrounder == null ? null : surrounder.surround().getExpression();
  }

  @Nullable
  private static PsiExpression expandLastIfDisjunct(PsiPolyadicExpression orChain,
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
    if (!(expression instanceof PsiLiteralExpression) || !PsiType.BOOLEAN.equals(expression.getType())) return false;

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

  private static void replaceWithStatements(@NotNull PsiStatement orig, @Nullable PsiStatement statement) throws IncorrectOperationException {
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
    else if (grandParent instanceof PsiCodeBlock && parent instanceof PsiIfStatement) {
      PsiIfStatement ifStmt = (PsiIfStatement)parent;
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
    return PsiResolveHelper.SERVICE.getInstance(declaration.getProject()).resolveAccessibleReferencedVariable(name, parent) != null;
  }

  private static PsiBlockStatement wrapWithCodeBlock(PsiStatement replacement) {
    PsiBlockStatement newBlock = BlockUtils.createBlockStatement(replacement.getProject());
    newBlock.getCodeBlock().add(replacement);
    return newBlock;
  }

  private static boolean blockAlwaysReturns(@Nullable PsiStatement statement) {
    if (statement == null) return false;
    try {
      return ControlFlowUtil.returnPresent(HighlightControlFlowUtil.getControlFlowNoConstantEvaluate(statement));
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
    if (newExpression instanceof PsiLiteralExpression) {
      final PsiElement parent = newExpression.getParent();
      if (parent instanceof PsiAssertStatement && ((PsiLiteralExpression)newExpression).getValue() == Boolean.TRUE) {
        parent.delete();
        return;
      }
    }
    if (!simplifyIfOrLoopStatement(newExpression)) {
      ParenthesesUtils.removeParentheses(newExpression, false);
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
      public void visitExpression(PsiExpression expression) {
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
    if (!(expression instanceof PsiConditionalExpression) && !PsiType.BOOLEAN.equals(expression.getType())) return false;
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
      public void visitExpression(PsiExpression expression) {
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

  private PsiExpression getSubExpression() {
    PsiElement element = getStartElement();
    return element instanceof PsiExpression ? (PsiExpression)element : null;
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
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
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
            if (constBoolean == Boolean.TRUE) {
              negate = !negate;
            }
            continue;
          }
          expressions.add(operand);
        }
        if (expressions.isEmpty()) {
          resultExpression = negate ? trueExpression : falseExpression;
        } else {
          String simplifiedText = StringUtil.join(expressions, PsiElement::getText, " ^ ");
          if (negate) {
            if (expressions.size() > 1) {
              simplifiedText = "!(" + simplifiedText + ")";
            } else {
              simplifiedText = BoolUtils.getNegatedExpressionText(expressions.get(0));
            }
          }
          resultExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(simplifiedText, expression);
        }
      } else {
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
                resultExpression = JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(lExpr.getText() + javaToken.getText() + operand.getText(), expression);
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
    public void visitConditionalExpression(PsiConditionalExpression expression) {
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
    public void visitPrefixExpression(PsiPrefixExpression expression) {
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
    public void visitParenthesizedExpression(PsiParenthesizedExpression expression) {
      PsiExpression subExpr = expression.getExpression();
      Boolean constBoolean = getConstBoolean(subExpr);
      if (constBoolean == null) return;
      if (!markAndCheckCreateResult()) {
        return;
      }
      resultExpression = constBoolean.booleanValue() ? trueExpression : falseExpression;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
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
    return PsiKeyword.TRUE.equals(text) ? Boolean.TRUE : PsiKeyword.FALSE.equals(text) ? Boolean.FALSE : null;
  }

  public static @IntentionFamilyName String getFamilyNameText() {
    return QuickFixBundle.message("simplify.boolean.expression.family");
  }
}
