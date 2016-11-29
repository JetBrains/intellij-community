/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextRelevantStaticMethod;
import com.intellij.compiler.classFilesIndex.impl.MethodsUsageIndexReader;
import com.intellij.compiler.classFilesIndex.impl.UsageIndexValue;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.compiler.classFilesIndex.impl.MethodIncompleteSignature;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class CachedRelevantStaticMethodSearcher {
  private final HashMap<MethodIncompleteSignature, PsiMethod> myCachedResolveResults = new HashMap<>();
  private final MethodsUsageIndexReader myIndexReader;
  private final ChainCompletionContext myCompletionContext;

  public CachedRelevantStaticMethodSearcher(final ChainCompletionContext completionContext) {
    myIndexReader = MethodsUsageIndexReader.getInstance(completionContext.getProject());
    myCompletionContext = completionContext;
  }

  @NotNull
  public List<ContextRelevantStaticMethod> getRelevantStaticMethods(final String resultQualifiedClassName, final int minOccurrence) {
    if (resultQualifiedClassName == null ||
        ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(resultQualifiedClassName) ||
        myCompletionContext.getTarget().getClassQName().equals(resultQualifiedClassName)) {
      return Collections.emptyList();
    }
    final TreeSet<UsageIndexValue> indexValues = myIndexReader.getMethods(resultQualifiedClassName);
    if (!indexValues.isEmpty()) {
      int occurrences = 0;
      final List<ContextRelevantStaticMethod> relevantMethods = new ArrayList<>();
      for (final UsageIndexValue indexValue : extractStaticMethods(indexValues)) {
        final MethodIncompleteSignature methodInvocation = indexValue.getMethodIncompleteSignature();
        final PsiMethod method;
        if (myCachedResolveResults.containsKey(methodInvocation)) {
          method = myCachedResolveResults.get(methodInvocation);
        }
        else {
          final PsiMethod[] methods = myCompletionContext.resolveNotDeprecated(methodInvocation);
          method = MethodChainsSearchUtil
            .getMethodWithMinNotPrimitiveParameters(methods, Collections.singleton(myCompletionContext.getTarget().getClassQName()));
          myCachedResolveResults.put(methodInvocation, method);
          if (method == null) {
            return Collections.emptyList();
          }
        }
        if (method == null) {
          return Collections.emptyList();
        }
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
          if (isMethodValid(method, myCompletionContext, resultQualifiedClassName)) {
            occurrences += indexValue.getOccurrences();
            if (myCompletionContext.getResolveScope().contains(method.getContainingFile().getVirtualFile())) {
              relevantMethods.add(new ContextRelevantStaticMethod(method, null));
            }
            if (occurrences >= minOccurrence) {
              return relevantMethods;
            }
          }
        }
      }
    }
    return Collections.emptyList();
  }

  private static List<UsageIndexValue> extractStaticMethods(final TreeSet<UsageIndexValue> indexValues) {
    final List<UsageIndexValue> relevantStaticMethods = new SmartList<>();
    for (final UsageIndexValue indexValue : indexValues) {
      if (indexValue.getMethodIncompleteSignature().isStatic()) {
        relevantStaticMethods.add(indexValue);
      }
    }
    return relevantStaticMethods;
  }

  private static boolean isMethodValid(final @Nullable PsiMethod method,
                                       final ChainCompletionContext completionContext,
                                       final String targetTypeShortName) {
    if (method == null) return false;
    for (final PsiParameter parameter : method.getParameterList().getParameters()) {
      final PsiType type = parameter.getType();
      final String shortClassName = typeAsShortString(type);
      if (targetTypeShortName.equals(shortClassName)) return false;
      if (!ChainCompletionStringUtil.isShortNamePrimitiveOrArrayOfPrimitives(shortClassName) &&
          !completionContext.contains(type.getCanonicalText())) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static String typeAsShortString(final PsiType type) {
    if (type instanceof PsiClassType)
      return ((PsiClassType) type).getClassName();
    else if (type instanceof PsiPrimitiveType)
      return type.getCanonicalText();
    else if (type instanceof PsiArrayType) {
      final String componentTypeAsString = typeAsShortString(((PsiArrayType)type).getComponentType());
      if (componentTypeAsString == null) return null;
      return String.format("%s[]", componentTypeAsString);
    }
    return null;
  }
}