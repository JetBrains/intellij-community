// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.LambdaGenerationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.codeInsight.intention.impl.StreamRefactoringUtil.getMapOperationName;
import static com.intellij.util.ObjectUtils.tryCast;

public class FoldExpressionIntoStreamInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitPolyadicExpression(PsiPolyadicExpression expression) {
        TerminalGenerator generator = getGenerator(expression);
        if (generator == null) return;
        List<PsiExpression> diff = extractDiff(generator, expression);
        if (diff.isEmpty()) return;
        if (!LambdaGenerationUtil.canBeUncheckedLambda(expression)) return;
        boolean stringJoin = generator.isStringJoin(expression, diff);
        String message = InspectionsBundle.message(stringJoin ?
                                                   "inspection.fold.expression.into.string.display.name" :
                                                   "inspection.fold.expression.into.stream.display.name");
        holder.registerProblem(expression, message,
                               new FoldExpressionIntoStreamFix(stringJoin));
      }
    };
  }

  private static List<PsiExpression> extractDiff(TerminalGenerator generator,
                                                 PsiPolyadicExpression expression) {
    EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
    PsiExpression[] operands = generator.getOperands(expression);
    if (operands.length < 3) return Collections.emptyList();
    List<PsiExpression> elements = new ArrayList<>();
    for (int i = 1; i < operands.length; i++) {
      if (!Objects.equals(operands[0].getType(), operands[i].getType())) return Collections.emptyList();
      EquivalenceChecker.Match match = equivalence.expressionsMatch(operands[0], operands[i]);
      PsiExpression left = null;
      PsiExpression right = null;
      if (match.isPartialMatch()) {
        left = tryCast(match.getLeftDiff(), PsiExpression.class);
        right = tryCast(match.getRightDiff(), PsiExpression.class);
      }
      else if (match.isExactMismatch() && generator.isDittoSupported()) {
        left = operands[0];
        right = operands[i];
      }
      if (left == null || right == null) return Collections.emptyList();
      if (elements.isEmpty()) {
        if (!StreamApiUtil.isSupportedStreamElement(left.getType()) || !ExpressionUtils.isSafelyRecomputableExpression(left)) {
          return Collections.emptyList();
        }
        if (operands[0] instanceof PsiBinaryExpression) {
          PsiBinaryExpression binOp = (PsiBinaryExpression)operands[0];
          if (ComparisonUtils.isComparison(binOp) &&
              (left == binOp.getLOperand() && ExpressionUtils.isSafelyRecomputableExpression(binOp.getROperand())) ||
              (left == binOp.getROperand() && ExpressionUtils.isSafelyRecomputableExpression(binOp.getLOperand()))) {
            // Disable for simple comparison chains like "a == null && b == null && c == null":
            // using Stream API here looks an overkill
            return Collections.emptyList();
          }
        }
        elements.add(left);
      }
      else if (elements.get(0) != left) {
        return Collections.emptyList();
      }
      if (!Objects.equals(left.getType(), right.getType()) ||
          !ExpressionUtils.isSafelyRecomputableExpression(right)) {
        return Collections.emptyList();
      }
      elements.add(right);
    }
    return elements;
  }

  private interface TerminalGenerator {
    default PsiExpression[] getOperands(PsiPolyadicExpression polyadicExpression) {
      return polyadicExpression.getOperands();
    }

    default boolean isDittoSupported() {
      return false;
    }

    @NotNull
    String generateTerminal(PsiType elementType, String lambda, CommentTracker ct);

    default boolean isStringJoin(PsiPolyadicExpression expression, List<? extends PsiExpression> diff) {
      return false;
    }
  }

  @Nullable
  private static TerminalGenerator getGenerator(PsiPolyadicExpression polyadicExpression) {
    IElementType tokenType = polyadicExpression.getOperationTokenType();
    if (tokenType.equals(JavaTokenType.OROR)) {
      return (elementType, lambda, ct) -> ".anyMatch(" + lambda + ")";
    }
    else if (tokenType.equals(JavaTokenType.ANDAND)) {
      return (elementType, lambda, ct) -> ".allMatch(" + lambda + ")";
    }
    else if (tokenType.equals(JavaTokenType.PLUS)) {
      PsiType type = polyadicExpression.getType();
      if (type instanceof PsiPrimitiveType) {
        if (!StreamApiUtil.isSupportedStreamElement(type)) return null;
        return (elementType, lambda, ct) -> "." + getMapOperationName(elementType, type) + "(" + lambda + ").sum()";
      }
      if (!TypeUtils.isJavaLangString(type)) return null;
      PsiExpression[] operands = polyadicExpression.getOperands();
      String mapToString;
      PsiType operandType = operands[0].getType();
      if (!InheritanceUtil.isInheritor(operandType, "java.lang.CharSequence")) {
        if (!StreamApiUtil.isSupportedStreamElement(operandType)) return null;
        mapToString = "."+getMapOperationName(operandType, type)+"(String::valueOf)";
      } else {
        mapToString = "";
      }
      PsiExpression delimiter = null;
      PsiExpression rest = null;
      if (operands.length > 4 && ExpressionUtils.isSafelyRecomputableExpression(operands[1]) &&
          IntStreamEx.range(1, operands.length, 2).elements(operands).pairMap(PsiEquivalenceUtil::areElementsEquivalent)
                     .allMatch(Boolean.TRUE::equals)) {
        delimiter = operands[1];
        if (operands.length % 2 == 0) {
          rest = ArrayUtil.getLastElement(operands);
        }
      }
      return new JoiningTerminalGenerator(operandType, mapToString, delimiter, rest);
    }
    return null;
  }

  @NotNull
  private static String mapToString(PsiType elementType, PsiType resultType, String lambda) {
    return "." + getMapOperationName(elementType, resultType) + "(" + lambda + ")";
  }

  private static class FoldExpressionIntoStreamFix implements LocalQuickFix {
    private final boolean myStringJoin;

    private FoldExpressionIntoStreamFix(boolean stringJoin) {myStringJoin = stringJoin;}

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message(myStringJoin ?
                                       "inspection.fold.expression.into.string.fix.name" :
                                       "inspection.fold.expression.into.stream.fix.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiPolyadicExpression expression = tryCast(descriptor.getStartElement(), PsiPolyadicExpression.class);
      if (expression == null) return;
      TerminalGenerator generator = getGenerator(expression);
      if (generator == null) return;
      List<PsiExpression> diffs = extractDiff(generator, expression);
      if (diffs.isEmpty()) return;

      PsiExpression[] operands = expression.getOperands();
      PsiExpression firstExpression = diffs.get(0);
      assert PsiTreeUtil.isAncestor(operands[0], firstExpression, false);
      Object marker = new Object();
      PsiTreeUtil.mark(firstExpression, marker);
      CommentTracker ct = new CommentTracker();
      PsiExpression operandCopy = (PsiExpression)ct.markUnchanged(operands[0]).copy();
      PsiElement expressionCopy = PsiTreeUtil.releaseMark(operandCopy, marker);
      if (expressionCopy == null) return;
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      PsiType elementType = firstExpression.getType();
      SuggestedNameInfo info = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, null, elementType, true);
      String name = info.names.length > 0 ? info.names[0] : "v";
      name = codeStyleManager.suggestUniqueVariableName(name, expression, true);
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      PsiExpression expressionCopyReplaced = (PsiExpression)expressionCopy.replace(factory.createExpressionFromText(name, expressionCopy));
      if (operandCopy == expressionCopy) {
        operandCopy = expressionCopyReplaced;
      }
      String operandCopyText = operandCopy.getText();
      String lambda = operandCopyText.equals(name) ? null : name + "->" + operandCopyText;
      String streamClass = StreamApiUtil.getStreamClassForType(elementType);
      if (streamClass == null) return;
      String source = streamClass + "." + (elementType instanceof PsiClassType ? "<" + elementType.getCanonicalText() + ">" : "")
                      + "of" + StreamEx.of(diffs).map(ct::text).joining(",", "(", ")");
      String fullStream = source + generator.generateTerminal(elementType, lambda, ct);
      PsiElement result = ct.replaceAndRestoreComments(expression, fullStream);
      cleanup(result);
    }

    private static void cleanup(PsiElement result) {
      JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(result.getProject());
      result = SimplifyStreamApiCallChainsInspection.simplifyStreamExpressions(result);
      LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
      result = codeStyleManager.shortenClassReferences(result);
      PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    }
  }

  private static class JoiningTerminalGenerator implements TerminalGenerator {
    private final PsiType myOperandType;
    private final String myMapToString;
    private final PsiExpression myDelimiter;
    private final PsiExpression myRest;

    public JoiningTerminalGenerator(PsiType operandType, String mapToString, PsiExpression delimiter, PsiExpression rest) {
      myOperandType = operandType;
      myMapToString = mapToString;
      myDelimiter = delimiter;
      myRest = rest;
    }

    @Override
    public PsiExpression[] getOperands(PsiPolyadicExpression polyadicExpression) {
      PsiExpression[] ops = polyadicExpression.getOperands();
      return myDelimiter == null ? ops :
             IntStreamEx.range(0, ops.length, 2).elements(ops).toArray(PsiExpression.EMPTY_ARRAY);
    }

    @Override
    public boolean isDittoSupported() {
      return myDelimiter != null;
    }

    @Override
    public boolean isStringJoin(PsiPolyadicExpression expression, List<? extends PsiExpression> diff) {
      if (!myMapToString.isEmpty()) return false;
      PsiExpression[] operands = getOperands(expression);
      return operands[0] == diff.get(0);
    }

    @NotNull
    @Override
    public String generateTerminal(PsiType elementType, String lambda, CommentTracker ct) {
      String map = (lambda == null ? "" : mapToString(elementType, myOperandType, lambda)) + myMapToString;
      return map +
             ".collect(" + CommonClassNames.JAVA_UTIL_STREAM_COLLECTORS +
             ".joining(" + (myDelimiter == null ? "" : ct.text(myDelimiter)) + "))" +
             (myRest == null ? "" : "+" + ct.text(myRest));
    }
  }
}
