// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class JavaNullMethodArgumentUtil {
  private static final Logger LOG = Logger.getInstance(JavaNullMethodArgumentUtil.class);

  /**
   * Returns true if a given method accepts null as an argument. Calls that resolve to overridden methods are ignored.
   *
   * @param method target method
   * @param argumentIdx argument index (zero-based)
   */
  public static boolean hasNullArgument(@NotNull PsiMethod method, final int argumentIdx) {
    final boolean[] result = {false};
    searchNullArgument(method, argumentIdx, expression -> {
      result[0] = true;
      return false;
    });
    return result[0];
  }

  /**
   * Looks for method calls that pass null to a given method. Calls that resolve to overridden methods are not processed.
   *  
   * @param method target method
   * @param argumentIdx argument index (zero-based)
   * @param nullArgumentProcessor a processor that accepts null literals passed as a specified argument to a specified method calls.
   */
  public static void searchNullArgument(@NotNull PsiMethod method, final int argumentIdx, @NotNull Processor<? super PsiExpression> nullArgumentProcessor) {
    final PsiParameter parameter = Objects.requireNonNull(method.getParameterList().getParameter(argumentIdx));
    if (parameter.getType() instanceof PsiEllipsisType) {
      return;
    }
    Collection<VirtualFile> candidateFiles = getFilesWithPotentialNullPassingCalls(method, argumentIdx);

    long start = System.currentTimeMillis();

    processCallsWithNullArguments(method, argumentIdx, nullArgumentProcessor, candidateFiles);

    long duration = System.currentTimeMillis() - start;
    if (duration > 200) {
      LOG.trace("Long nullable argument search for " + method.getName() + "(" + PsiUtil.getMemberQualifiedName(method) + "): " + duration + "ms, " + candidateFiles.size() + " files");
    }
  }

  private static void processCallsWithNullArguments(@NotNull PsiMethod method,
                                                    int argumentIdx,
                                                    @NotNull Processor<? super PsiExpression> nullArgumentProcessor,
                                                    Collection<? extends VirtualFile> candidateFiles) {
    if (candidateFiles.isEmpty()) return;

    GlobalSearchScope scope = GlobalSearchScope.filesScope(method.getProject(), candidateFiles);
    MethodReferencesSearch.search(method, scope, true).forEach(ref -> {
      PsiExpressionList argumentList = getCallArgumentList(ref.getElement());
      PsiExpression argument = getCallArgument(argumentList, argumentIdx);
      if (argument instanceof PsiLiteralExpression && argument.textMatches(JavaKeywords.NULL)) {
        if (argumentList.getParent() instanceof PsiMethodCallExpression methodCall) {
          PsiMethod target = methodCall.resolveMethod();
          // Could be resolved to overriding method
          if (target == null || !target.isEquivalentTo(method)) return true;
        }
        return nullArgumentProcessor.process(argument);
      }
      return true;
    });
  }

  @Contract("null, _ -> null")
  private static @Nullable PsiExpression getCallArgument(@Nullable PsiExpressionList argumentList, int argumentIdx) {
    if (argumentList == null) return null;
    PsiExpression[] arguments = argumentList.getExpressions();
    return argumentIdx < arguments.length ? arguments[argumentIdx] : null;
  }

  private static @Nullable PsiExpressionList getCallArgumentList(@Nullable PsiElement psi) {
    PsiElement parent = psi == null ? null :psi.getParent();
    if (parent instanceof PsiCallExpression) {
      return ((PsiCallExpression)parent).getArgumentList();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)parent).getArgumentList();
    }
    return null;
  }

  private static @NotNull Collection<VirtualFile> getFilesWithPotentialNullPassingCalls(@NotNull PsiMethod method, int parameterIndex) {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final CommonProcessors.CollectProcessor<VirtualFile> collector = new CommonProcessors.CollectProcessor<>(new ArrayList<>());
    GlobalSearchScope searchScope = GlobalSearchScopeUtil.toGlobalSearchScope(method.getUseScope(), method.getProject());
    searchScope = searchScope.intersectWith(GlobalSearchScopesCore.projectProductionScope(method.getProject()));
    fileBasedIndex.getFilesWithKey(JavaNullMethodArgumentIndex.INDEX_ID,
                                   Collections.singleton(new JavaNullMethodArgumentIndex.MethodCallData(method.getName(), parameterIndex)),
                                   collector,
                                   searchScope);
    return collector.getResults();
  }
}
