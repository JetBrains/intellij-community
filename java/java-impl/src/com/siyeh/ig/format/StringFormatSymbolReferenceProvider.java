// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    FormatDecode.FormatArgument argument = FormatDecode.FormatArgument.extract(callExpression, List.of(), List.of(), true);
    if (argument == null || !PsiTreeUtil.isAncestor(argument.getExpression(), expression, false)) return List.of();
    String formatString = ObjectUtils.tryCast(expression.getValue(), String.class);
    if (formatString == null) return List.of();
    PsiExpression[] arguments = Objects.requireNonNull(callExpression.getArgumentList()).getExpressions();
    int argumentCount = arguments.length - argument.getIndex();
    FormatDecode.Validator[] validators;
    try {
      validators = FormatDecode.decodeNoVerify(formatString, argumentCount);
    }
    catch (FormatDecode.IllegalFormatException e) {
      return List.of();
    }
    List<PsiSymbolReference> result = new ArrayList<>();
    for (int i = 0; i < validators.length; i++) {
      int index = argument.getIndex() + i;
      if (index >= arguments.length) break;

      FormatDecode.Validator metaValidator = validators[i];
      if (metaValidator == null) continue;
      Collection<FormatDecode.Validator> unpacked = metaValidator instanceof FormatDecode.MultiValidator multi ?
                                                    multi.getValidators() : List.of(metaValidator);
      for (FormatDecode.Validator validator : unpacked) {
        TextRange stringRange = validator.getRange();
        if (stringRange == null) continue;
        TextRange range = ExpressionUtils.findStringLiteralRange(expression, stringRange.getStartOffset(),
                                                                 stringRange.getEndOffset());
        if (range == null) continue;
        result.add(new JavaFormatArgumentSymbolReference(expression, range, arguments[index]));
      }
    }
    return result;
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
    private final PsiExpression myArgument;

    private JavaFormatArgumentSymbolReference(@NotNull PsiExpression format, @NotNull TextRange range, @NotNull PsiExpression argument) {
      myFormat = format;
      myRange = range;
      myArgument = argument;
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
      return List.of(new JavaFormatArgumentSymbol(myArgument));
    }
  }

  @ApiStatus.Internal
  public static final class JavaFormatArgumentSymbol implements Symbol, SearchTarget, NavigatableSymbol {
    private final @NotNull PsiExpression myExpression;

    JavaFormatArgumentSymbol(@NotNull PsiExpression argument) {
      myExpression = argument;
    }

    @Override
    public @NotNull Pointer<JavaFormatArgumentSymbol> createPointer() {
      return Pointer.delegatingPointer(SmartPointerManager.createPointer(myExpression), JavaFormatArgumentSymbol::new);
    }

    @Override
    public @NotNull Collection<? extends NavigationTarget> getNavigationTargets(@NotNull Project project) {
      return List.of(SymbolNavigationService.getInstance().psiElementNavigationTarget(myExpression));
    }

    @NotNull PsiExpression getExpression() {
      return myExpression;
    }

    private @NlsSafe @NotNull String getTargetText() {
      return myExpression.getText();
    }

    @Nullable PsiExpression getFormatString() {
      if (!(myExpression.getParent() instanceof PsiExpressionList list) ||
          !(list.getParent() instanceof PsiCallExpression call)) {
        return null;
      }
      FormatDecode.FormatArgument argument = FormatDecode.FormatArgument.extract(call, List.of(), List.of(), true);
      if (argument == null) return null;
      return argument.getExpression();
    }

    @Override
    public SearchScope getMaximalSearchScope() {
      if (myExpression.getParent() instanceof PsiExpressionList list && list.getParent() instanceof PsiCallExpression call) {
        return new LocalSearchScope(call);
      }
      return LocalSearchScope.EMPTY;
    }

    @NotNull
    @Override
    public TargetPresentation presentation() {
      return TargetPresentation.builder(getTargetText()).presentation();
    }

    @NotNull
    @Override
    public UsageHandler getUsageHandler() {
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
}
