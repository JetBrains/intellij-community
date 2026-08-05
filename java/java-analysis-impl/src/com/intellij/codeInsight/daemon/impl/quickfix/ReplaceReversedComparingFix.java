// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.java.codeserver.highlighting.JavaErrorCollector;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.PsiCall;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Replaces a {@code reversed()} call on a comparator factory with an equivalent call passing
 * {@code Comparator.reverseOrder()} to the factory:
 * <ul>
 *   <li>{@code Comparator.comparing(keyExtractor).reversed()} &rarr; {@code Comparator.comparing(keyExtractor, Comparator.reverseOrder())}</li>
 *   <li>{@code Map.Entry.comparingByKey().reversed()} &rarr; {@code Map.Entry.comparingByKey(Comparator.reverseOrder())}</li>
 *   <li>{@code Map.Entry.comparingByValue().reversed()} &rarr; {@code Map.Entry.comparingByValue(Comparator.reverseOrder())}</li>
 * </ul>
 * The former shape is a common source of confusing inference errors: {@code reversed()} turns the factory call into
 * a standalone expression, so its type arguments cannot be inferred from the target type anymore.
 * The latter keeps the factory call in the poly expression position, so the inference succeeds.
 */
public final class ReplaceReversedComparingFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {
  private static final CallMatcher COMPARATOR_REVERSED =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "reversed").parameterCount(0);
  private static final CallMatcher COMPARATOR_COMPARING =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_COMPARATOR, "comparing").parameterCount(1);
  private static final CallMatcher ENTRY_COMPARING =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_MAP_ENTRY, "comparingByKey", "comparingByValue").parameterCount(0);
  private static final CallMatcher COMPARATOR_FACTORY = CallMatcher.anyOf(COMPARATOR_COMPARING, ENTRY_COMPARING);

  private ReplaceReversedComparingFix(@NotNull PsiMethodCallExpression reversedCall) {
    super(reversedCall);
  }

  @Override
  public @Nls @NotNull String getFamilyName() {
    return QuickFixBundle.message("replace.reversed.comparator.family.name");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression reversedCall) {
    PsiMethodCallExpression factoryCall = getFactoryCall(reversedCall);
    if (factoryCall == null || !fixesTheCall(reversedCall)) return null;
    return Presentation.of(CommonQuickFixBundle.message("fix.replace.with.x", replacementDescription(factoryCall)))
      .withPriority(PriorityAction.Priority.HIGH);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression reversedCall, @NotNull ModPsiUpdater updater) {
    PsiMethodCallExpression factoryCall = getFactoryCall(reversedCall);
    if (factoryCall == null) return;
    CommentTracker ct = new CommentTracker();
    ct.markUnchanged(factoryCall);
    PsiElement result = ct.replaceAndRestoreComments(reversedCall, replacementText(factoryCall));
    JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(result);
  }

  /**
   * Registers the fix for every argument having the {@code comparatorFactory(...).reversed()} shape.
   * Whether the replacement actually makes the call compile is checked lazily,
   * in {@link #getPresentation(ActionContext, PsiMethodCallExpression)}.
   *
   * @param sink   sink to register the fixes at
   * @param anchor element the error is reported at: either a single mismatched argument, or the whole argument list
   * @param list   argument list of a call that cannot be applied to the supplied arguments
   */
  public static void registerFixes(@NotNull Consumer<? super CommonIntentionAction> sink,
                                   @NotNull PsiElement anchor,
                                   @NotNull PsiExpressionList list) {
    PsiExpression[] arguments = anchor instanceof PsiExpression argument && argument.getParent() == list
                                ? new PsiExpression[]{argument} : list.getExpressions();
    for (PsiExpression expression : arguments) {
      if (PsiUtil.skipParenthesizedExprDown(expression) instanceof PsiMethodCallExpression call && getFactoryCall(call) != null) {
        sink.accept(new ReplaceReversedComparingFix(call));
      }
    }
  }

  private static @Nullable PsiMethodCallExpression getFactoryCall(@NotNull PsiMethodCallExpression reversedCall) {
    if (!COMPARATOR_REVERSED.test(reversedCall)) return null;
    PsiMethodCallExpression factoryCall = MethodCallUtils.getQualifierMethodCall(reversedCall);
    return COMPARATOR_FACTORY.test(factoryCall) ? factoryCall : null;
  }

  private static @NotNull String replacementDescription(@NotNull PsiMethodCallExpression factoryCall) {
    if (COMPARATOR_COMPARING.test(factoryCall)) return "Comparator.comparing(..., Comparator.reverseOrder())";
    return "Map.Entry." + factoryCall.getMethodExpression().getReferenceName() + "(Comparator.reverseOrder())";
  }

  private static @NotNull String replacementText(@NotNull PsiMethodCallExpression factoryCall) {
    PsiExpression[] arguments = factoryCall.getArgumentList().getExpressions();
    String keyExtractor = arguments.length == 0 ? "" : arguments[0].getText() + ", ";
    return factoryCall.getMethodExpression().getText() + "(" + keyExtractor +
           CommonClassNames.JAVA_UTIL_COMPARATOR + ".reverseOrder())";
  }

  /**
   * @return true if the supplied call has the {@code comparatorFactory(...).reversed()} shape,
   * and performing the replacement removes all the compilation errors from the enclosing call
   */
  private static boolean fixesTheCall(@NotNull PsiMethodCallExpression reversedCall) {
    if (getFactoryCall(reversedCall) == null) return false;
    PsiExpression argument = reversedCall;
    while (argument.getParent() instanceof PsiParenthesizedExpression parenthesized) {
      argument = parenthesized;
    }
    if (!(argument.getParent() instanceof PsiExpressionList list) || !(list.getParent() instanceof PsiCall call)) return false;
    int index = ArrayUtil.indexOf(list.getExpressions(), argument);
    if (index < 0) return false;
    PsiCall callCopy = LambdaUtil.copyTopLevelCall(call);
    if (callCopy == null) return false;
    PsiExpressionList listCopy = callCopy.getArgumentList();
    if (listCopy == null) return false;
    PsiExpression argumentCopy = PsiUtil.skipParenthesizedExprDown(listCopy.getExpressions()[index]);
    if (!(argumentCopy instanceof PsiMethodCallExpression reversedCallCopy)) return false;
    PsiMethodCallExpression factoryCallCopy = getFactoryCall(reversedCallCopy);
    if (factoryCallCopy == null) return false;
    PsiExpression replacement = JavaPsiFacade.getElementFactory(call.getProject())
      .createExpressionFromText(replacementText(factoryCallCopy), argumentCopy);
    argumentCopy.replace(replacement);
    if (hasErrors(callCopy)) return false;
    // the copy is detached from its original context, so the compatibility with the expected type must be checked separately
    PsiType expectedType = PsiTypesUtil.getExpectedTypeByParent(call);
    if (expectedType != null && callCopy instanceof PsiCallExpression callExpressionCopy) {
      PsiType actualType = callExpressionCopy.getType();
      if (actualType == null || !TypeConversionUtil.isAssignable(expectedType, actualType)) return false;
    }
    return true;
  }

  private static boolean hasErrors(@NotNull PsiElement element) {
    Ref<Boolean> hasError = Ref.create(false);
    JavaErrorCollector collector = new JavaErrorCollector(element.getContainingFile(), error -> hasError.set(true));
    element.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement child) {
        collector.processElement(child);
        if (hasError.get()) {
          stopWalking();
          return;
        }
        super.visitElement(child);
      }
    });
    return hasError.get();
  }
}
