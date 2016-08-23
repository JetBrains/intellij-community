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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public class JavaNullMethodArgumentUtil {

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
    final GlobalSearchScope scope = findScopeWhereNullArgumentCanPass(method, argumentIdx);
    if (scope == null) return;
    MethodReferencesSearch.search(method, scope, true).forEach(ref -> {
      final PsiElement psi = ref.getElement();
      if (psi != null) {
        final PsiElement parent = psi.getParent();
        PsiExpressionList argumentList = null;
        if (parent instanceof PsiCallExpression) {
          argumentList = ((PsiCallExpression)parent).getArgumentList();
        }
        else if (parent instanceof PsiAnonymousClass) {
          argumentList = ((PsiAnonymousClass)parent).getArgumentList();
        }
        if (argumentList != null) {
          final PsiExpression[] arguments = argumentList.getExpressions();
          if (argumentIdx < arguments.length) {
            final PsiExpression argument = arguments[argumentIdx];
            if (argument instanceof PsiLiteralExpression && PsiKeyword.NULL.equals(argument.getText())) {
              return nullArgumentProcessor.process(argument);
            }
          }
        }
      }
      return true;
    });
  }

  @Nullable
  private static GlobalSearchScope findScopeWhereNullArgumentCanPass(@NotNull PsiMethod method, int parameterIndex) {
    final FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
    final CommonProcessors.CollectProcessor<VirtualFile> collector = new CommonProcessors.CollectProcessor<>(new ArrayList<>());
    fileBasedIndex.getFilesWithKey(JavaNullMethodArgumentIndex.INDEX_ID,
                                   Collections.singleton(new JavaNullMethodArgumentIndex.MethodCallData(method.getName(), parameterIndex)),
                                   collector,
                                   GlobalSearchScopeUtil.toGlobalSearchScope(method.getUseScope(), method.getProject()));
    final Collection<VirtualFile> candidateFiles = collector.getResults();
    return candidateFiles.isEmpty() ? null : GlobalSearchScope.filesScope(method.getProject(), candidateFiles);
  }

}
