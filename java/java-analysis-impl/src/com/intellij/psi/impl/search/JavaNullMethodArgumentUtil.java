/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class JavaNullMethodArgumentUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.JavaNullMethodArgumentUtil");

  public static boolean hasNullArgument(@NotNull PsiMethod method, final int argumentIdx) {
    final boolean[] result = {false};
    searchNullArgument(method, argumentIdx, expression -> {
      result[0] = true;
      return false;
    });
    return result[0];
  }

  public static void searchNullArgument(@NotNull PsiMethod method, final int argumentIdx, @NotNull Processor<PsiExpression> nullArgumentProcessor) {
    final PsiParameter parameter = method.getParameterList().getParameters()[argumentIdx];
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
                                                    @NotNull Processor<PsiExpression> nullArgumentProcessor,
                                                    Collection<VirtualFile> candidateFiles) {
    if (candidateFiles.isEmpty()) return;

    GlobalSearchScope scope = GlobalSearchScope.filesScope(method.getProject(), candidateFiles);
    MethodReferencesSearch.search(method, scope, true).forEach(ref -> {
      PsiExpression argument = getCallArgument(ref, argumentIdx);
      if (argument instanceof PsiLiteralExpression && argument.textMatches(PsiKeyword.NULL)) {
        return nullArgumentProcessor.process(argument);
      }
      return true;
    });
  }

  @Nullable
  private static PsiExpression getCallArgument(PsiReference ref, int argumentIdx) {
    PsiExpressionList argumentList = getCallArgumentList(ref.getElement());
    PsiExpression[] arguments = argumentList == null ? PsiExpression.EMPTY_ARRAY : argumentList.getExpressions();
    return argumentIdx < arguments.length ? arguments[argumentIdx] : null;
  }

  @Nullable
  private static PsiExpressionList getCallArgumentList(@Nullable PsiElement psi) {
    PsiElement parent = psi == null ? null :psi.getParent();
    if (parent instanceof PsiCallExpression) {
      return ((PsiCallExpression)parent).getArgumentList();
    }
    else if (parent instanceof PsiAnonymousClass) {
      return ((PsiAnonymousClass)parent).getArgumentList();
    }
    return null;
  }

  @NotNull
  private static Collection<VirtualFile> getFilesWithPotentialNullPassingCalls(@NotNull PsiMethod method, int parameterIndex) {
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
