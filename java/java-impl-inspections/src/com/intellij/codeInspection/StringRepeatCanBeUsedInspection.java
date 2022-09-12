// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;

import static com.intellij.util.ObjectUtils.tryCast;

public class StringRepeatCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher APPEND = CallMatcher.instanceCall(Appendable.class.getName(), "append").parameterCount(1);
  private static final CallMatcher INSERT = CallMatcher.instanceCall("java.lang.AbstractStringBuilder", "insert").parameterCount(2);
  private static final CallMatcher PRINT = CallMatcher.instanceCall(PrintStream.class.getName(), "print").parameterCount(1);
  private static final CallMatcher
    WRITE_OR_PRINT = CallMatcher.anyOf(CallMatcher.instanceCall(Writer.class.getName(), "write").parameterTypes(String.class.getName())
    , CallMatcher.instanceCall(OutputStream.class.getName(), "write").parameterTypes("byte[]")
    , PRINT);
  private static final CallMatcher CONCAT = CallMatcher.instanceCall(String.class.getName(), "concat").parameterCount(1);

  public boolean ADD_MATH_MAX = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(JavaBundle.message("label.add.math.max.0.count.to.avoid.possible.semantics.change"), this, "ADD_MATH_MAX");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel11OrHigher(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null) return;
        PsiLocalVariable var = loop.getCounter();
        if (PsiType.LONG.equals(var.getType()) || VariableAccessUtils.variableIsUsed(var, statement.getBody())) return;
        PsiExpressionStatement appendingStatement = findAppendingStatement(statement);
        PsiExpression arg = findAppendedContent(loop, appendingStatement);
        if (arg == null) return;
        if (SideEffectChecker.mayHaveSideEffects(arg)) return;
        holder.registerProblem(statement.getFirstChild(), JavaBundle.message(
          "inspection.message.can.be.replaced.with.string.repeat"),
                               new StringRepeatCanBeUsedFix(ADD_MATH_MAX));
      }
    };
  }

  @Nullable
  private static PsiExpressionStatement findAppendingStatement(PsiForStatement statement) {
    PsiStatement @NotNull [] bodyStatements = ControlFlowUtils.unwrapBlock(statement.getBody());
    if (bodyStatements.length != 1) return null;
    return tryCast(bodyStatements[0], PsiExpressionStatement.class);
  }

  @Nullable
  private static PsiExpression findAppendedContent(@Nullable CountingLoop loop, @Nullable PsiExpressionStatement appendingStatement) {
    if (appendingStatement == null) return null;
    PsiExpression appendingExpression = appendingStatement.getExpression();
    PsiAssignmentExpression assignmentExpression = tryCast(appendingExpression, PsiAssignmentExpression.class);
    PsiReferenceExpression destinationVariable = null;
    if (assignmentExpression != null) {
      destinationVariable = tryCast(assignmentExpression.getLExpression(), PsiReferenceExpression.class);
      if (destinationVariable == null
          || destinationVariable.getType() == null) return null;
    }
    if (destinationVariable != null && JavaTokenType.PLUSEQ.equals(assignmentExpression.getOperationTokenType())) {
      // Case: textString += " ";
      if (!TypeUtils.isJavaLangString(destinationVariable.getType())) return null;
      return assignmentExpression.getRExpression();
    }
    if (destinationVariable != null && TypeUtils.isJavaLangString(destinationVariable.getType())) {
      if (JavaTokenType.EQ.equals(assignmentExpression.getOperationTokenType())) {
        PsiPolyadicExpression concatenation = tryCast(PsiUtil.skipParenthesizedExprDown(assignmentExpression.getRExpression()),
                                                      PsiPolyadicExpression.class);
        PsiMethodCallExpression concatCall = tryCast(assignmentExpression.getRExpression(), PsiMethodCallExpression.class);
        PsiExpression leftString = null;
        PsiExpression rightString = null;
        if (concatenation != null && JavaTokenType.PLUS.equals(concatenation.getOperationTokenType()) && concatenation.getOperands().length == 2) {
          // Case: textString = textString + " ";
          leftString = PsiUtil.skipParenthesizedExprDown(concatenation.getOperands()[0]);
          rightString = concatenation.getOperands()[1];
        } else if (CONCAT.test(concatCall)) {
          // Case: textString = textString.concat(" ");
          leftString = PsiUtil.skipParenthesizedExprDown(concatCall.getMethodExpression().getQualifierExpression());
          rightString = concatCall.getArgumentList().getExpressions()[0];
        }
        if (leftString == null || rightString == null) return null;
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(destinationVariable, leftString)) {
          // Case: textString = textString + " ";
          return rightString;
        } else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(destinationVariable, rightString)) {
          // Case: textString = " " + textString;
          return leftString;
        }
        return null;
      }
    }
    return findAppendedObject(loop, appendingExpression, assignmentExpression, destinationVariable);
  }

  @Nullable
  private static PsiExpression findAppendedObject(@Nullable CountingLoop loop,
                                             PsiExpression appendingExpression,
                                             PsiAssignmentExpression assignmentExpression,
                                             PsiReferenceExpression destinationVariable) {
    if (destinationVariable != null) {
      // Case: textBuilder = textBuilder.append(" ");
      appendingExpression = assignmentExpression.getRExpression();
    }
    PsiMethodCallExpression call = tryCast(appendingExpression, PsiMethodCallExpression.class);
    if (call != null) {
      PsiReferenceExpression caller = tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()),
                                                 PsiReferenceExpression.class);
      if (caller == null) return null;
      if (PRINT.test(call)
          && destinationVariable == null
          && "out".equals(caller.getReferenceName())) {
        // Case: System.out.print(" ");

        PsiElement referent = caller.resolve();
        if (referent instanceof PsiField) {
          PsiField field = (PsiField)referent;
          PsiClass containingClass = field.getContainingClass();
          if (containingClass != null && "java.lang.System".equals(containingClass.getQualifiedName())) {
            return call.getArgumentList().getExpressions()[0];
          }
        }
      }
      if (!ExpressionUtil.isEffectivelyUnqualified(caller)) return null;
      if (ClassUtils.isKnownClassImplementation(caller)) {
        if (APPEND.test(call)) {
          // Case: textBuilder.append(" ");
          return call.getArgumentList().getExpressions()[0];
        }
        if (INSERT.test(call)) {
          // Case: textBuilder.insert(0, " ");
          PsiExpression[] expressions = call.getArgumentList().getExpressions();
          if (loop == null || ExplicitArrayFillingInspection.isChangedInLoop(loop, expressions[0])) return null;
          return expressions[1];
        }
        if (destinationVariable != null) return null;
        if (WRITE_OR_PRINT.test(call)) {
          return call.getArgumentList().getExpressions()[0];
        }
      }
    }
    return null;
  }

  private static final class StringRepeatCanBeUsedFix implements LocalQuickFix {
    private final boolean myAddMathMax;

    private StringRepeatCanBeUsedFix(boolean addMathMax) {
      myAddMathMax = addMathMax;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String.repeat()");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiForStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiForStatement.class);
      if (statement == null) return;
      CountingLoop loop = CountingLoop.from(statement);
      if (loop == null) return;
      PsiExpressionStatement appendingStatement = findAppendingStatement(statement);
      PsiExpression arg = findAppendedContent(loop, appendingStatement);
      if (arg == null) return;
      PsiExpression from, to;
      if (loop.isDescending()) {
        from = loop.getBound();
        to = loop.getInitializer();
      }
      else {
        from = loop.getInitializer();
        to = loop.getBound();
      }
      CommentTracker ct = new CommentTracker();
      String repeatQualifier = getRepeatQualifier(arg, ct);
      String countText = getCountText(from, to, loop.isIncluding(), ct);
      if (myAddMathMax) {
        countText = CommonClassNames.JAVA_LANG_MATH + ".max(0," + countText + ")";
      }
      String replacement = repeatQualifier + ".repeat(" + countText + ")";
      ct.replace(arg, replacement);
      PsiExpressionStatement result = (PsiExpressionStatement)ct.replaceAndRestoreComments(statement, appendingStatement);
      if (myAddMathMax) {
        PsiMethodCallExpression repeatCall = tryCast(findAppendedContent(loop, result), PsiMethodCallExpression.class);
        if (repeatCall == null) return;
        PsiMethodCallExpression maxCall = (PsiMethodCallExpression)repeatCall.getArgumentList().getExpressions()[0];
        PsiExpression count = maxCall.getArgumentList().getExpressions()[1];
        LongRangeSet range = CommonDataflow.getExpressionRange(count);
        if (range != null && !range.isEmpty() && range.min() >= 0) {
          maxCall.replace(count);
        }
      }
    }

    @NotNull
    private static String getCountText(PsiExpression from, PsiExpression to, boolean including, CommentTracker ct) {
      String countText = null;
      Number fromNumber = JavaPsiMathUtil.getNumberFromLiteral(from);
      if (fromNumber instanceof Integer) {
        int origin = fromNumber.intValue();
        if (origin < Integer.MAX_VALUE) {
          if (including) {
            origin--;
          }
          countText = JavaPsiMathUtil.add(to, -origin, ct);
        }
      }
      if (countText == null) {
        countText =
          ct.text(to, ParenthesesUtils.ADDITIVE_PRECEDENCE) + "-" + ct.text(from, ParenthesesUtils.MULTIPLICATIVE_PRECEDENCE);
        if (including) {
          PsiExpression expr = JavaPsiFacade.getElementFactory(from.getProject()).createExpressionFromText(countText, from);
          countText = JavaPsiMathUtil.add(expr, 1, ct);
        }
      }
      return countText;
    }

    @NotNull
    private static String getRepeatQualifier(PsiExpression arg, CommentTracker ct) {
      if (arg instanceof PsiLiteralExpression && !TypeUtils.isJavaLangString(arg.getType())) {
        PsiLiteralExpression literal = (PsiLiteralExpression)arg;
        Object value = literal.getValue();
        if (value instanceof Character) {
          return PsiLiteralUtil.stringForCharLiteral(literal.getText());
        }
        return StringUtil.wrapWithDoubleQuote(StringUtil.escapeStringCharacters(String.valueOf(value)));
      }
      if (TypeUtils.isJavaLangString(arg.getType()) && NullabilityUtil.getExpressionNullability(arg, true) == Nullability.NOT_NULL) {
        return ct.text(arg, ParenthesesUtils.METHOD_CALL_PRECEDENCE);
      }
      return CommonClassNames.JAVA_LANG_STRING + ".valueOf(" + ct.text(arg) + ")";
    }
  }
}
