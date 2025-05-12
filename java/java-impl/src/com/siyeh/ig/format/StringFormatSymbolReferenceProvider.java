// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.find.usages.api.SearchTarget;
import com.intellij.find.usages.api.UsageHandler;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceHints;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.model.search.SearchRequest;
import com.intellij.navigation.NavigatableSymbol;
import com.intellij.navigation.SymbolNavigationService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.navigation.NavigationTarget;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

public final class StringFormatSymbolReferenceProvider implements PsiSymbolReferenceProvider {
  @Override
  public @NotNull Collection<? extends @NotNull PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost element,
                                                                                  @NotNull PsiSymbolReferenceHints hints) {
    if (!(element instanceof PsiLiteralExpression expression)) return List.of();
    if (!hintsCheck(hints)) return List.of();
    return getReferences(expression);
  }

  static @NotNull List<@NotNull PsiSymbolReference> getReferences(@NotNull PsiLiteralExpression expression) {
    PsiCallExpression callExpression = findContextCall(expression);
    if (callExpression == null) return List.of();
    List<@NotNull PsiSymbolReference> refs = getPrintFormatRefs(expression, callExpression);
    return refs.isEmpty() ? getMessageFormatRefs(expression, callExpression) : refs;
  }

  private static @NotNull List<@NotNull PsiSymbolReference> getMessageFormatRefs(@NotNull PsiLiteralExpression expression,
                                                                                 @NotNull PsiCallExpression callExpression) {
    if (!MessageFormatUtil.PATTERN_METHODS.matches(callExpression)) return List.of();
    String formatString = ObjectUtils.tryCast(expression.getValue(), String.class);
    if (formatString == null) return List.of();
    MessageFormatUtil.MessageFormatResult format = MessageFormatUtil.checkFormat(formatString);
    List<MessageFormatUtil.MessageFormatPlaceholder> placeholders = format.placeholders();
    if (placeholders.isEmpty()) return List.of();
    return createReferences(callExpression, 0, expression, placeholders);
  }

  private static @NotNull List<@NotNull PsiSymbolReference> getPrintFormatRefs(@NotNull PsiLiteralExpression expression,
                                                                               @NotNull PsiCallExpression callExpression) {
    FormatDecode.FormatArgument argument = FormatDecode.FormatArgument.extract(callExpression, List.of(), List.of(), true);
    if (argument == null || !PsiTreeUtil.isAncestor(resolve(argument.getExpression()), expression, false)) return List.of();
    String formatString = ObjectUtils.tryCast(expression.getValue(), String.class);
    if (formatString == null) return List.of();
    PsiExpression[] arguments = Objects.requireNonNull(callExpression.getArgumentList()).getExpressions();
    int index = argument.getIndex();
    int argumentCount = arguments.length - index;
    FormatDecode.Validator[] validators;
    try {
      validators = FormatDecode.decodeNoVerify(formatString, argumentCount);
    }
    catch (FormatDecode.IllegalFormatException e) {
      return List.of();
    }
    List<@NotNull FormatPlaceholder> placeholders = FormatDecode.asPlaceholders(validators);
    return createReferences(callExpression, index - 1, expression, placeholders);
  }

  private static @NotNull List<@NotNull PsiSymbolReference> createReferences(@NotNull PsiCallExpression callExpression,
                                                                             int formatStringIndex,
                                                                             @NotNull PsiLiteralExpression formatExpression,
                                                                             @NotNull List<? extends FormatPlaceholder> placeholders) {
    List<PsiExpression> formatArguments = getFormatArguments(callExpression, formatStringIndex);
    List<@NotNull PsiSymbolReference> result = new ArrayList<>();
    for (FormatPlaceholder placeholder : placeholders) {
      int index = placeholder.index();
      if (index >= formatArguments.size()) continue;
      TextRange stringRange = placeholder.range();
      TextRange range = ExpressionUtils.findStringLiteralRange(formatExpression, stringRange.getStartOffset(),
                                                               stringRange.getEndOffset());
      if (range == null) continue;
      PsiExpression arg = formatArguments.get(index);
      result.add(new JavaFormatArgumentSymbolReference(formatExpression, range, () -> new JavaFormatArgumentSymbol(arg, formatStringIndex)));
    }
    return result;
  }

  private static List<PsiExpression> getFormatArguments(@NotNull PsiCallExpression callExpression, int formatIndex) {
    PsiExpression[] arguments = Objects.requireNonNull(callExpression.getArgumentList()).getExpressions();
    int firstArgument = formatIndex + 1;
    if (arguments.length <= firstArgument) return List.of();
    if (MethodCallUtils.isVarArgCall(callExpression)) {
      return Arrays.asList(arguments).subList(firstArgument, arguments.length);
    }
    if (arguments.length != firstArgument + 1) return List.of();
    if (!(PsiUtil.skipParenthesizedExprDown(arguments[firstArgument]) instanceof PsiNewExpression array)) return List.of();
    PsiArrayInitializerExpression initializer = array.getArrayInitializer();
    if (initializer == null) return List.of();
    return Arrays.asList(initializer.getInitializers());
  }

  private static boolean hintsCheck(@NotNull PsiSymbolReferenceHints hints) {
    if (!hints.getReferenceClass().isAssignableFrom(JavaFormatArgumentSymbolReference.class)) return false;
    Class<? extends Symbol> targetClass = hints.getTargetClass();
    if (targetClass != null && !targetClass.isAssignableFrom(JavaFormatArgumentSymbol.class)) return false;
    Symbol target = hints.getTarget();
    return target == null || target instanceof JavaFormatArgumentSymbol;
  }

  private static PsiCallExpression findContextCall(PsiElement context) {
    if (!(context instanceof PsiExpression expr)) return null;
    expr = ExpressionUtils.getPassThroughExpression(expr);
    if (expr.getParent() instanceof PsiLocalVariable variable) {
      PsiReferenceExpression ref = ContainerUtil.getOnlyItem(VariableAccessUtils.getVariableReferences(variable));
      if (ref != null) {
        expr = ref;
      }
    }
    if (expr.getParent() instanceof PsiExpressionList list && list.getParent() instanceof PsiCallExpression call) {
      return call;
    }
    return ExpressionUtils.getCallForQualifier(expr);
  }

  @Override
  public @NotNull Collection<? extends @NotNull SearchRequest> getSearchRequests(@NotNull Project project, @NotNull Symbol target) {
    return List.of();
  }

  private static class JavaFormatArgumentSymbolReference implements PsiSymbolReference {
    private final PsiExpression myFormat;
    private final TextRange myRange;
    private final Supplier<JavaFormatArgumentSymbol> myResolver;

    private JavaFormatArgumentSymbolReference(@NotNull PsiExpression format,
                                              @NotNull TextRange range,
                                              @NotNull Supplier<JavaFormatArgumentSymbol> resolver) {
      myFormat = format;
      myRange = range;
      myResolver = resolver;
    }

    @Override
    public @NotNull PsiElement getElement() {
      return myFormat;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
      return myRange;
    }

    @Override
    public @NotNull Collection<? extends Symbol> resolveReference() {
      return List.of(myResolver.get());
    }
  }

  @ApiStatus.Internal
  public static final class JavaFormatArgumentSymbol implements Symbol, SearchTarget, NavigatableSymbol {
    private final @NotNull PsiExpression myExpression;
    private final int myFormatStringIndex;

    JavaFormatArgumentSymbol(@NotNull PsiExpression argument, int index) {
      myExpression = argument;
      myFormatStringIndex = index;
    }

    @Override
    public @NotNull Pointer<JavaFormatArgumentSymbol> createPointer() {
      return Pointer.delegatingPointer(SmartPointerManager.createPointer(myExpression),
                                       argument -> new JavaFormatArgumentSymbol(argument, myFormatStringIndex));
    }

    @Override
    public @NotNull Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project) {
      return List.of(SymbolNavigationService.getInstance().psiElementNavigationTarget(myExpression));
    }

    public @NotNull PsiExpression getExpression() {
      return myExpression;
    }

    private @NlsSafe @NotNull String getTargetText() {
      return myExpression.getText();
    }

    public @Nullable PsiExpression getFormatString() {
      if (!(myExpression.getParent() instanceof PsiExpressionList list) ||
          !(list.getParent() instanceof PsiCallExpression call)) {
        return null;
      }
      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length <= myFormatStringIndex) {
        return null;
      }
      return resolve(expressions[myFormatStringIndex]);
    }

    @Override
    public SearchScope getMaximalSearchScope() {
      if (myExpression.getParent() instanceof PsiExpressionList list && list.getParent() instanceof PsiCallExpression call) {
        return new LocalSearchScope(call);
      }
      return LocalSearchScope.EMPTY;
    }

    @Override
    public @NotNull TargetPresentation presentation() {
      return TargetPresentation.builder(getTargetText()).presentation();
    }

    @Override
    public @NotNull UsageHandler getUsageHandler() {
      return UsageHandler.createEmptyUsageHandler(getTargetText());
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof JavaFormatArgumentSymbol that && myExpression.equals(that.myExpression);
    }

    @Override
    public int hashCode() {
      return myExpression.hashCode();
    }
  }

  private static @Nullable PsiExpression resolve(PsiExpression target) {
    if (target instanceof PsiReferenceExpression ref && ref.resolve() instanceof PsiLocalVariable local) {
      return local.getInitializer();
    }
    return target;
  }
}
