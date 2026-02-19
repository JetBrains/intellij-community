// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiForStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.CountingLoop;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import com.siyeh.ipp.psiutils.ErrorUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OBJECTS;
import static com.intellij.util.ObjectUtils.tryCast;

public final class StringRepeatCanBeUsedInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher APPEND = CallMatcher.instanceCall(JAVA_LANG_ABSTRACT_STRING_BUILDER, "append").parameterCount(1);
  private static final CallMatcher REPEAT = CallMatcher.instanceCall(JAVA_LANG_STRING, "repeat").parameterCount(1);

  public boolean ADD_MATH_MAX = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ADD_MATH_MAX", JavaBundle.message("label.add.math.max.0.count.to.avoid.possible.semantics.change")));
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    var languageLevel = PsiUtil.getLanguageLevel(holder.getFile());
    if (!languageLevel.isAtLeast(LanguageLevel.JDK_11)) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitForStatement(@NotNull PsiForStatement statement) {
        PsiMethodCallExpression call = findAppendCall(statement);
        if (call == null) return;
        if (ErrorUtil.containsDeepError(call)) return;
        PsiReferenceExpression qualifier = tryCast(PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression()),
                                                   PsiReferenceExpression.class);
        if (qualifier == null || !ExpressionUtil.isEffectivelyUnqualified(qualifier)) return;
        CountingLoop loop = CountingLoop.from(statement);
        if (loop == null) return;
        PsiLocalVariable var = loop.getCounter();
        if (var.getType().equals(PsiTypes.longType()) || VariableAccessUtils.variableIsUsed(var, call)) return;
        PsiExpression arg = call.getArgumentList().getExpressions()[0];
        if (SideEffectChecker.mayHaveSideEffects(arg)) return;
        if (languageLevel.isAtLeast(LanguageLevel.JDK_21)) {
          PsiType type = qualifier.getType();
          if (type == null) return;
          String builderClassName = type.getPresentableText();
          holder.registerProblem(statement.getFirstChild(),
                                 messageForCanBeReplacedWithBuilderRepeat(builderClassName),
                                 new ConvertForLoopToStringBuilderRepeatFix(ADD_MATH_MAX, builderClassName));
        }
        else {
          holder.registerProblem(statement.getFirstChild(), JavaBundle.message("inspection.message.can.be.replaced.with.string.repeat"),
                                 new ConvertForLoopToStringRepeatFix(ADD_MATH_MAX));
        }
      }

      /**
       * Detects AbstractStringBuilder.append() call with String.repeat() as an argument, for example
       * <pre>
       *   StringBuilder sb = new StringBuilder();
       *   sb.append("*".repeat(10))
       * </pre>
       */
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        if (!languageLevel.isAtLeast(LanguageLevel.JDK_21)) return;
        if (!APPEND.test(call)) return;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 1) return;
        PsiMethodCallExpression repeatCall = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
        if (repeatCall == null) return;
        if (!REPEAT.test(repeatCall)) return;
        if (ErrorUtil.containsDeepError(call)) return;
        PsiType type = qualifier.getType();
        if (type == null) return;
        String builderClassName = type.getPresentableText();
        PsiElement reference = call.getMethodExpression().getReferenceNameElement();
        if (reference == null) return;
        holder.registerProblem(reference, messageForCanBeReplacedWithBuilderRepeat(builderClassName),
                               new ConvertStringRepeatToStringBuilderRepeatFix(builderClassName));
      }

      private static @Nls @NotNull String messageForCanBeReplacedWithBuilderRepeat(String builderClassName) {
        return JavaBundle.message("inspection.message.can.be.replaced.with.builder.repeat", builderClassName + ".repeat()");
      }
    };
  }

  private static @Nullable PsiMethodCallExpression findAppendCall(PsiForStatement statement) {
    PsiExpressionStatement body = tryCast(ControlFlowUtils.stripBraces(statement.getBody()), PsiExpressionStatement.class);
    if (body == null) return null;
    PsiMethodCallExpression call = tryCast(body.getExpression(), PsiMethodCallExpression.class);
    if (!APPEND.test(call)) return null;
    return call;
  }

  /**
   * Replaces a for loop containing a call to AbstractStringBuilder.append(s), for example
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * for(int i=0; i<100; i++) {
   *   sb.append(" ");
   * }
   * </pre>
   * with single call to AbstractStringBuilder.repeat()
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * sb.repeat(" ", 100);
   * </pre>
   */
  private static final class ConvertForLoopToStringBuilderRepeatFix extends ConvertToRepeatFix {
    private final String builderClassShortName;

    private ConvertForLoopToStringBuilderRepeatFix(boolean addMathMax, String builderClassShortName) {
      super(addMathMax);
      this.builderClassShortName = builderClassShortName;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", builderClassShortName + ".repeat()");
    }

    @Override
    protected void replaceWithRepeatCall(PsiExpression qualifierExpression,
                                         String repeatedStringExpression,
                                         String countText,
                                         CommentTracker ct,
                                         PsiExpression arg,
                                         PsiForStatement forStatement,
                                         PsiMethodCallExpression appendCall) {
      String replacement = qualifierExpression.getText() + ".repeat(" + repeatedStringExpression + ", " + countText + ");";
      PsiExpressionStatement result = (PsiExpressionStatement)ct.replaceAndRestoreComments(forStatement, replacement);
      if (myAddMathMax) {
        PsiMethodCallExpression repeatCall = (PsiMethodCallExpression)result.getExpression();
        PsiMethodCallExpression maxCall = (PsiMethodCallExpression)repeatCall.getArgumentList().getExpressions()[1];
        simplifyMaxCall(maxCall);
      }
    }
  }

  /**
   * Replaces a for loop containing a call to AbstractStringBuilder.append(s), for example
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * for(int i=0; i<100; i++) {
   *   sb.append(" ");
   * }
   * </pre>
   * with single call to AbstractStringBuilder.append() that uses String.repeat()
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * sb.append(" ".repeat(100));
   * </pre>
   */
  private static final class ConvertForLoopToStringRepeatFix extends ConvertToRepeatFix {
    private ConvertForLoopToStringRepeatFix(boolean addMathMax) {
      super(addMathMax);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "String.repeat()");
    }

    @Override
    protected void replaceWithRepeatCall(PsiExpression qualifierExpression,
                                         String repeatedStringExpression,
                                         String countText,
                                         CommentTracker ct,
                                         PsiExpression arg,
                                         PsiForStatement forStatement,
                                         PsiMethodCallExpression call) {
      String replacement = repeatedStringExpression + ".repeat(" + countText + ")";
      ct.replace(arg, replacement);
      PsiExpressionStatement result = (PsiExpressionStatement)ct.replaceAndRestoreComments(forStatement, call.getParent());
      if (myAddMathMax) {
        PsiMethodCallExpression appendCall = (PsiMethodCallExpression)result.getExpression();
        PsiMethodCallExpression repeatCall = (PsiMethodCallExpression)appendCall.getArgumentList().getExpressions()[0];
        PsiMethodCallExpression maxCall = (PsiMethodCallExpression)repeatCall.getArgumentList().getExpressions()[0];
        simplifyMaxCall(maxCall);
      }
    }
  }

  private static abstract class ConvertToRepeatFix extends PsiUpdateModCommandQuickFix {
    protected final boolean myAddMathMax;

    private ConvertToRepeatFix(boolean addMathMax) {
      myAddMathMax = addMathMax;
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiForStatement forStatement = PsiTreeUtil.getParentOfType(element, PsiForStatement.class);
      if (forStatement == null) return;
      CountingLoop loop = CountingLoop.from(forStatement);
      if (loop == null) return;
      PsiMethodCallExpression appendCall = findAppendCall(forStatement);
      if (appendCall == null) return;
      if (ErrorUtil.containsDeepError(appendCall)) return;
      PsiExpression qualifierExpression = appendCall.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return;
      PsiExpression arg = appendCall.getArgumentList().getExpressions()[0];
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
      String repeatedStringExpression = getRepeatedStringExpression(arg, ct);
      String countText = getCountText(from, to, loop.isIncluding(), ct);
      if (myAddMathMax) {
        countText = CommonClassNames.JAVA_LANG_MATH + ".max(0," + countText + ")";
      }
      replaceWithRepeatCall(qualifierExpression, repeatedStringExpression, countText, ct, arg, forStatement, appendCall);
    }

    protected abstract void replaceWithRepeatCall(
      PsiExpression qualifierExpression,
      String repeatedStringExpression,
      String countText,
      CommentTracker ct,
      PsiExpression arg,
      PsiForStatement forStatement,
      PsiMethodCallExpression appendCall);

    protected void simplifyMaxCall(PsiMethodCallExpression maxCall) {
      PsiExpression count = maxCall.getArgumentList().getExpressions()[1];
      LongRangeSet range = CommonDataflow.getExpressionRange(count);
      if (range != null && !range.isEmpty() && range.min() >= 0) {
        maxCall.replace(count);
      }
    }

    private static @NotNull String getCountText(PsiExpression from, PsiExpression to, boolean including, CommentTracker ct) {
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

    private static @NotNull String getRepeatedStringExpression(PsiExpression arg, CommentTracker ct) {
      boolean isStringType = TypeUtils.isJavaLangString(arg.getType());
      if (arg instanceof PsiLiteralExpression literal && !isStringType) {
        Object value = literal.getValue();
        if (value instanceof Character) {
          return PsiLiteralUtil.stringForCharLiteral(literal.getText());
        }
        return StringUtil.wrapWithDoubleQuote(StringUtil.escapeStringCharacters(String.valueOf(value)));
      }
      if (isStringType && NullabilityUtil.getExpressionNullability(arg, true) == Nullability.NOT_NULL) {
        return ct.text(arg, ParenthesesUtils.METHOD_CALL_PRECEDENCE);
      }
      return JAVA_LANG_STRING + ".valueOf(" + ct.text(arg) + ")";
    }
  }

  /**
   * Replaces AbstractStringBuilder.append() containing String.repeat(...) argument
   * with AbstractStringBuilder.repeat(), for example
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * sb.append(" ".repeat(100));
   * </pre>
   * with call to AbstractStringBuilder.repeat()
   * <pre>
   * StringBuilder sb = new StringBuilder();
   * sb.repeat(" ", 100));
   * </pre>
   */
  private static final class ConvertStringRepeatToStringBuilderRepeatFix extends PsiUpdateModCommandQuickFix {
    private final String builderClassShortName;

    private ConvertStringRepeatToStringBuilderRepeatFix(String builderClassShortName) {
      this.builderClassShortName = builderClassShortName;
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", builderClassShortName + ".repeat()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiMethodCallExpression appendCall = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class);
      if (appendCall == null || !APPEND.test(appendCall)) return;
      if (ErrorUtil.containsDeepError(appendCall)) return;

      PsiExpression qualifierExpression = appendCall.getMethodExpression().getQualifierExpression();
      if (qualifierExpression == null) return;

      PsiExpression[] args = appendCall.getArgumentList().getExpressions();
      if (args.length != 1) return;
      PsiMethodCallExpression repeatCall = tryCast(PsiUtil.skipParenthesizedExprDown(args[0]), PsiMethodCallExpression.class);
      if (repeatCall == null) return;
      PsiExpression[] repeatArgs = repeatCall.getArgumentList().getExpressions();
      if (repeatArgs.length != 1) return;
      PsiExpression count = repeatArgs[0];

      PsiExpression stringExpression = PsiUtil.skipParenthesizedExprDown(repeatCall.getMethodExpression().getQualifierExpression());
      if (stringExpression == null) return;
      CommentTracker ct = new CommentTracker();
      String replacement =
        ct.text(qualifierExpression) + ".repeat(" + sanitizedStringExpression(stringExpression, ct) + ", " + ct.text(count) + ")";
      var replaced = ct.replaceAndRestoreComments(appendCall, replacement);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
    }

    private static String sanitizedStringExpression(PsiExpression stringExpression, CommentTracker ct) {
      if (NullabilityUtil.getExpressionNullability(stringExpression, true) == Nullability.NOT_NULL) {
        return ct.text(stringExpression);
      }
      return JAVA_UTIL_OBJECTS + ".requireNonNull(" + ct.text(stringExpression) + ")";
    }
  }
}
