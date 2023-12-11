/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class PointlessBooleanExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public enum BooleanExpressionKind {
    USELESS, USELESS_WITH_SIDE_EFFECTS, UNKNOWN
  }

  private static final TokenSet booleanTokens = TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.AND, JavaTokenType.OROR,
                                                                JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.EQEQ, JavaTokenType.NE);

  @SuppressWarnings("PublicField")
  public boolean m_ignoreExpressionsContainingConstants = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreExpressionsContainingConstants", InspectionGadgetsBundle.message("pointless.boolean.expression.ignore.option")));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String replacement = (String)infos[1];
    final PsiExpression expression = (PsiExpression)infos[0];
    if (replacement.isEmpty() && expression instanceof PsiAssignmentExpression) {
      return InspectionGadgetsBundle.message("boolean.expression.does.not.modify.problem.descriptor", expression.getText());
    }
    return InspectionGadgetsBundle.message("boolean.expression.can.be.simplified.problem.descriptor", replacement);
  }

  private StringBuilder buildSimplifiedExpression(@Nullable PsiExpression expression, StringBuilder out, CommentTracker tracker) {
    if (expression instanceof PsiAssignmentExpression) {
      buildSimplifiedAssignmentExpression((PsiAssignmentExpression)expression, out, tracker);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      buildSimplifiedPolyadicExpression((PsiPolyadicExpression)expression, out, tracker);
    }
    else if (expression instanceof PsiPrefixExpression) {
      buildSimplifiedPrefixExpression((PsiPrefixExpression)expression, out, tracker);
    }
    else if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      final PsiExpression expression1 = parenthesizedExpression.getExpression();
      out.append('(');
      buildSimplifiedExpression(expression1, out, tracker);
      out.append(')');
    }
    else if (expression != null) {
      out.append(tracker.text(expression));
    }
    return out;
  }

  private void buildSimplifiedPolyadicExpression(PsiPolyadicExpression expression,
                                                 StringBuilder out,
                                                 CommentTracker tracker) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression[] operands = expression.getOperands();
    final List<PsiExpression> expressions = new ArrayList<>();
    if (tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.AND)) {
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.TRUE) {
          continue;
        }
        if (evaluate(operand) == Boolean.FALSE) {
          out.append(PsiKeyword.FALSE);
          return;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        out.append(PsiKeyword.TRUE);
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.ANDAND) ? "&&" : "&", false, out, tracker);
    } else if (tokenType.equals(JavaTokenType.OROR) || tokenType.equals(JavaTokenType.OR)) {
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.FALSE) {
          continue;
        }
        if (evaluate(operand) == Boolean.TRUE) {
          out.append(PsiKeyword.TRUE);
          return;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        out.append(PsiKeyword.FALSE);
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.OROR) ? "||" : "|", false, out, tracker);
    }
    else if (tokenType.equals(JavaTokenType.XOR) || tokenType.equals(JavaTokenType.NE)) {
      boolean negate = false;
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.FALSE) {
          continue;
        }
        if (evaluate(operand) == Boolean.TRUE) {
          negate = !negate;
          continue;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        if (negate) {
          out.append(PsiKeyword.TRUE);
        }
        else {
          out.append(PsiKeyword.FALSE);
        }
        return;
      }
      buildSimplifiedExpression(expressions, tokenType.equals(JavaTokenType.XOR) ? "^" : "!=", negate, out, tracker);
    }
    else if (tokenType.equals(JavaTokenType.EQEQ)) {
      boolean negate = false;
      for (PsiExpression operand : operands) {
        if (evaluate(operand) == Boolean.TRUE) {
          continue;
        }
        if (evaluate(operand) == Boolean.FALSE) {
          negate = !negate;
          continue;
        }
        expressions.add(operand);
      }
      if (expressions.isEmpty()) {
        if (negate) {
          out.append(PsiKeyword.FALSE);
        }
        else {
          out.append(PsiKeyword.TRUE);
        }
        return;
      }
      buildSimplifiedExpression(expressions, "==", negate, out, tracker);
    }
    else {
      out.append(tracker.text(expression));
    }
  }

  private void buildSimplifiedExpression(List<PsiExpression> expressions,
                                         String token,
                                         boolean negate,
                                         StringBuilder out,
                                         CommentTracker tracker) {
    if (expressions.size() == 1) {
      final PsiExpression expression = expressions.get(0);
      if (!negate) {
        out.append(tracker.text(expression));
        return;
      }
      if (ComparisonUtils.isComparison(expression)) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
        final String negatedComparison = ComparisonUtils.getNegatedComparison(binaryExpression.getOperationTokenType());
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        assert rhs != null;
        out.append(tracker.text(lhs)).append(negatedComparison).append(tracker.text(rhs));
      }
      else {
        out.append('!').append(tracker.text(expression, ParenthesesUtils.PREFIX_PRECEDENCE));
      }
    }
    else {
      if (negate) {
        out.append("!(");
      }
      boolean useToken = false;
      for (PsiExpression expression : expressions) {
        if (useToken) {
          out.append(token);
          final PsiElement previousSibling = expression.getPrevSibling();
          if (previousSibling instanceof PsiWhiteSpace) {
            out.append(previousSibling.getText());
          }
        }
        else {
          useToken = true;
        }
        buildSimplifiedExpression(expression, out, tracker);
        final PsiElement nextSibling = expression.getNextSibling();
        if (nextSibling instanceof PsiWhiteSpace) {
          out.append(nextSibling.getText());
        }
      }
      if (negate) {
        out.append(')');
      }
    }
  }

  private void buildSimplifiedPrefixExpression(PsiPrefixExpression expression, StringBuilder out, CommentTracker tracker) {
    final PsiJavaToken sign = expression.getOperationSign();
    final IElementType tokenType = sign.getTokenType();
    final PsiExpression operand = expression.getOperand();
    if (JavaTokenType.EXCL.equals(tokenType)) {
      final Boolean value = evaluate(operand);
      if (value == Boolean.TRUE) {
        out.append(PsiKeyword.FALSE);
        return;
      }
      else if (value == Boolean.FALSE) {
        out.append(PsiKeyword.TRUE);
        return;
      }
    }
    buildSimplifiedExpression(operand, out.append(sign.getText()), tracker);
  }

  private void buildSimplifiedAssignmentExpression(PsiAssignmentExpression expression, @NonNls StringBuilder out, CommentTracker tracker) {
    final IElementType tokenType = expression.getOperationTokenType();
    final PsiExpression lhs = expression.getLExpression();

    if (tokenType == JavaTokenType.ANDEQ) {
      if (evaluate(expression.getRExpression()) == Boolean.TRUE) {
        if (expression.getParent() instanceof PsiExpressionStatement) {
          return;
        }
        out.append(lhs.getText());
      }
      else {
        out.append(lhs.getText()).append("=false");
      }
      tracker.markUnchanged(lhs);
      return;
    }
    else if (tokenType == JavaTokenType.OREQ) {
      if (evaluate(expression.getRExpression()) == Boolean.FALSE) {
        if (expression.getParent() instanceof PsiExpressionStatement) {
          return;
        }
        out.append(lhs.getText());
      }
      else {
        out.append(lhs.getText()).append("=true");
      }
      tracker.markUnchanged(lhs);
      return;
    }
    tracker.markUnchanged(lhs);
    out.append(lhs.getText()).append(expression.getOperationSign().getText());
    buildSimplifiedExpression(expression.getRExpression(), out, tracker);
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    final String replacement = (String)infos[1];
    if (replacement.isEmpty() && infos[0] instanceof PsiAssignmentExpression) {
      return new RemovePointlessBooleanExpressionFix();
    }
    boolean hasSideEffect = (boolean)infos[2];
    return new PointlessBooleanExpressionFix(hasSideEffect);
  }

  private static class RemovePointlessBooleanExpressionFix extends PsiUpdateModCommandQuickFix {

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("boolean.expression.remove.compound.assignment.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiAssignmentExpression assignmentExpression)) {
        return;
      }
      final PsiElement parent = assignmentExpression.getParent();
      assert parent instanceof PsiStatement;
      final List<PsiExpression> sideEffects = SideEffectChecker.extractSideEffectExpressions(assignmentExpression.getLExpression());
      final CommentTracker commentTracker = new CommentTracker();
      for (PsiExpression sideEffect : sideEffects) {
        commentTracker.markUnchanged(sideEffect);
      }
      final PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, assignmentExpression);
      if (statements.length > 0) {
        BlockUtils.addBefore((PsiStatement)parent, statements);
      }
      commentTracker.deleteAndRestoreComments(parent);
    }
  }

  private class PointlessBooleanExpressionFix extends PsiUpdateModCommandQuickFix {
    private final boolean myHasSideEffect;

    PointlessBooleanExpressionFix(boolean hasSideEffect) {
      myHasSideEffect = hasSideEffect;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myHasSideEffect
             ? InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix.sideEffect")
             : InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression expression)) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      String simplifiedExpression = buildSimplifiedExpression(expression, new StringBuilder(), tracker).toString();
      boolean isConstant = simplifiedExpression.equals("true") || simplifiedExpression.equals("false");
      if (isConstant && myHasSideEffect) {
        CodeBlockSurrounder surrounder = CodeBlockSurrounder.forExpression(expression);
        if (surrounder == null) return;
        CodeBlockSurrounder.SurroundResult result = surrounder.surround();
        expression = result.getExpression();
        PsiStatement anchor = result.getAnchor();
        List<PsiExpression> sideEffects = extractSideEffects(expression);
        for (PsiExpression sideEffect : sideEffects) {
          tracker.markUnchanged(sideEffect);
        }
        PsiStatement[] statements = StatementExtractor.generateStatements(sideEffects, expression);
        if (statements.length > 0) {
          BlockUtils.addBefore(anchor, statements);
        }
      }
      expression = (PsiExpression)tracker.replaceAndRestoreComments(expression, simplifiedExpression);
      PsiExpression topExpression = ExpressionUtils.getTopLevelExpression(expression);
      if (getExpressionKind(topExpression) == BooleanExpressionKind.USELESS) {
        tracker = new CommentTracker();
        simplifiedExpression = buildSimplifiedExpression(topExpression, new StringBuilder(), tracker).toString();
        tracker.replaceAndRestoreComments(topExpression, simplifiedExpression);
      }
    }

    private List<PsiExpression> extractSideEffects(PsiExpression expression) {
      if (!(expression instanceof PsiPolyadicExpression polyadicExpression)) {
        return Collections.emptyList();
      }
      IElementType sign = polyadicExpression.getOperationTokenType();
      Boolean stopper = (sign == JavaTokenType.ANDAND) ? Boolean.FALSE : sign == JavaTokenType.OROR ? Boolean.TRUE : null;
      PsiExpression[] operands = polyadicExpression.getOperands();
      List<PsiExpression> sideEffects = new ArrayList<>();
      for (PsiExpression operand : operands) {
        if (stopper != null && stopper.equals(evaluate(operand))) {
          break;
        }
        sideEffects.addAll(SideEffectChecker.extractSideEffectExpressions(operand));
      }
      return sideEffects;
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessBooleanExpressionVisitor();
  }

  private class PointlessBooleanExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      BooleanExpressionKind kind = getExpressionKind(expression);
      if (kind == BooleanExpressionKind.UNKNOWN) {
        return;
      }
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (parent instanceof PsiExpression && getExpressionKind((PsiExpression)parent) != BooleanExpressionKind.UNKNOWN) {
        return;
      }

      final String replacement = buildSimplifiedExpression(expression, new StringBuilder(), new CommentTracker()).toString();
      final Supplier<PsiElement> newBodySupplier =
        () -> JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(replacement, expression);
      if (parent instanceof PsiLambdaExpression && !LambdaUtil.isSafeLambdaBodyReplacement((PsiLambdaExpression)parent, newBodySupplier)) {
        return;
      }

      registerError(expression, expression, replacement, kind == BooleanExpressionKind.USELESS_WITH_SIDE_EFFECTS);
    }
  }

  @NotNull
  public BooleanExpressionKind getExpressionKind(PsiExpression expression) {
    if ((expression instanceof PsiPrefixExpression || expression instanceof PsiPolyadicExpression) 
        && containsEscapingPatternVariable(expression)) {
      return BooleanExpressionKind.UNKNOWN;
    }
    if (expression instanceof PsiPrefixExpression || expression instanceof PsiAssignmentExpression) {
      return evaluate(expression) != null ? BooleanExpressionKind.USELESS : BooleanExpressionKind.UNKNOWN;
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      return getPolyadicKind(polyadicExpression);
    }
    return BooleanExpressionKind.UNKNOWN;
  }

  @NotNull
  private BooleanExpressionKind getPolyadicKind(PsiPolyadicExpression expression) {
    final IElementType sign = expression.getOperationTokenType();
    if (!booleanTokens.contains(sign)) {
      return BooleanExpressionKind.UNKNOWN;
    }
    final PsiExpression[] operands = expression.getOperands();
    boolean containsConstant = false;
    boolean stopCheckingSideEffects = false;
    boolean sideEffectMayBeRemoved = false;
    boolean reducedToConstant = false;
    for (PsiExpression operand : operands) {
      if (operand == null) {
        return BooleanExpressionKind.UNKNOWN;
      }
      final PsiType type = operand.getType();
      if (type == null || !type.equals(PsiTypes.booleanType()) && !type.equalsToText(CommonClassNames.JAVA_LANG_BOOLEAN)) {
        return BooleanExpressionKind.UNKNOWN;
      }
      if (!stopCheckingSideEffects && SideEffectChecker.mayHaveSideEffects(operand)) {
        sideEffectMayBeRemoved = true;
        continue;
      }
      Boolean value = evaluate(operand);
      if (value != null) {
        containsConstant = true;
        if ((JavaTokenType.ANDAND.equals(sign) && !value) || (JavaTokenType.OROR.equals(sign) && value)) {
          stopCheckingSideEffects = true;
          reducedToConstant = true;
        }
        if ((JavaTokenType.AND.equals(sign) && !value) || (JavaTokenType.OR.equals(sign) && value)) {
          reducedToConstant = true;
        }
      }
    }
    if (containsConstant) {
      if (sideEffectMayBeRemoved && reducedToConstant) {
        return CodeBlockSurrounder.canSurround(expression)
               ? BooleanExpressionKind.USELESS_WITH_SIDE_EFFECTS
               : BooleanExpressionKind.UNKNOWN;
      }
      return BooleanExpressionKind.USELESS;
    }
    return BooleanExpressionKind.UNKNOWN;
  }

  @Nullable
  private Boolean evaluate(@Nullable PsiExpression expression) {
    if (expression == null || m_ignoreExpressionsContainingConstants && containsReference(expression)) {
      return null;
    }
    if (expression instanceof PsiParenthesizedExpression parenthesizedExpression) {
      return evaluate(parenthesizedExpression.getExpression());
    }
    if (expression instanceof PsiPolyadicExpression polyadicExpression) {
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.OROR)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (SideEffectChecker.mayHaveSideEffects(operand)) {
            return null;
          }
          if (evaluate(operand) == Boolean.TRUE) {
            return Boolean.TRUE;
          }
        }
      }
      else if (tokenType.equals(JavaTokenType.ANDAND)) {
        for (PsiExpression operand : polyadicExpression.getOperands()) {
          if (SideEffectChecker.mayHaveSideEffects(operand)) {
            return null;
          }
          if (evaluate(operand) == Boolean.FALSE) {
            return Boolean.FALSE;
          }
        }
      }
    }
    else if (expression instanceof PsiPrefixExpression prefixExpression) {
      final IElementType tokenType = prefixExpression.getOperationTokenType();
      if (JavaTokenType.EXCL.equals(tokenType)) {
        final PsiExpression operand = prefixExpression.getOperand();
        final Boolean b = evaluate(operand);
        if (b == Boolean.FALSE) {
          return Boolean.TRUE;
        } else if (b == Boolean.TRUE) {
          return Boolean.FALSE;
        }
      }
    }
    else if (expression instanceof PsiAssignmentExpression assignmentExpression) {
      final IElementType tokenType = assignmentExpression.getOperationTokenType();
      if (JavaTokenType.ANDEQ.equals(tokenType) || JavaTokenType.OREQ.equals(tokenType)) {
        return evaluate(assignmentExpression.getRExpression());
      }
    }
    return (Boolean)ConstantExpressionUtil.computeCastTo(expression, PsiTypes.booleanType());
  }

  private static boolean containsReference(@Nullable PsiExpression expression) {
    final Predicate<PsiReferenceExpression> referenceToConstant =
      r -> r.resolve() instanceof PsiVariable v && v.hasModifierProperty(PsiModifier.FINAL) && v.hasInitializer();
    return hasMatchingOffspring(expression, PsiReferenceExpression.class, referenceToConstant);
  }

  private static boolean containsEscapingPatternVariable(PsiExpression expression) {
    return hasMatchingOffspring(expression, PsiPatternVariable.class, v -> {
      final PsiElement scope = v.getDeclarationScope();
      return !PsiTreeUtil.isAncestor(expression, scope, false) &&
             hasMatchingOffspring(scope, PsiReferenceExpression.class,
                                  r -> r.isReferenceTo(v) && !PsiTreeUtil.isAncestor(expression, r, true));
    });
  }

  private static <T extends PsiElement> boolean hasMatchingOffspring(PsiElement root, Class<T> elementClass, Predicate<T> predicate) {
    return !PsiTreeUtil.processElements(root, elementClass, x -> !predicate.test(x));
  }
}