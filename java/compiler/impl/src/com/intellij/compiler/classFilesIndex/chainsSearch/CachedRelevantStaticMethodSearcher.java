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

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextRelevantStaticMethod;
import com.intellij.psi.*;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class CachedRelevantStaticMethodSearcher {
  private final HashMap<MethodIncompleteSignature, PsiMethod> myCachedResolveResults = new HashMap<>();
  private final CompilerReferenceServiceEx myIndexReader;
  private final ChainCompletionContext myCompletionContext;

  public CachedRelevantStaticMethodSearcher(ChainCompletionContext completionContext) {
    myIndexReader = (CompilerReferenceServiceEx)CompilerReferenceService.getInstance(completionContext.getProject());
    myCompletionContext = completionContext;
  }

  @NotNull
  public List<ContextRelevantStaticMethod> getRelevantStaticMethods(PsiType type, int minOccurrence) {
    String resultQualifiedClassName = type.getCanonicalText();
    if (resultQualifiedClassName == null ||
        ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(type) ||
        myCompletionContext.getTarget().getClassQName().equals(resultQualifiedClassName)) {
      return Collections.emptyList();
    }
    SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues = myIndexReader.getMethods(resultQualifiedClassName);
    if (!indexValues.isEmpty()) {
      int occurrences = 0;
      List<ContextRelevantStaticMethod> relevantMethods = new ArrayList<>();
      for (OccurrencesAware<MethodIncompleteSignature> indexValue : extractStaticMethods(indexValues)) {
        MethodIncompleteSignature methodInvocation = indexValue.getUnderlying();
        PsiMethod method;
        if (myCachedResolveResults.containsKey(methodInvocation)) {
          method = myCachedResolveResults.get(methodInvocation);
        }
        else {
          PsiMethod[] methods = myCompletionContext.resolve(methodInvocation);
          method = MethodChainsSearchUtil
            .getMethodWithMinNotPrimitiveParameters(methods, myCompletionContext.getTarget().getTargetClass());
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

  private static List<OccurrencesAware<MethodIncompleteSignature>> extractStaticMethods(SortedSet<OccurrencesAware<MethodIncompleteSignature>> indexValues) {
    List<OccurrencesAware<MethodIncompleteSignature>> relevantStaticMethods = new SmartList<>();
    for (OccurrencesAware<MethodIncompleteSignature> indexValue : indexValues) {
      if (indexValue.getUnderlying().isStatic()) {
        relevantStaticMethods.add(indexValue);
      }
    }
    return relevantStaticMethods;
  }

  private static boolean isMethodValid(@Nullable PsiMethod method,
                                       ChainCompletionContext completionContext,
                                       String targetTypeShortName) {
    if (method == null) return false;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      PsiType type = parameter.getType();
      String shortClassName = typeAsShortString(type);
      if (targetTypeShortName.equals(shortClassName)) return false;
      if (!ChainCompletionStringUtil.isShortNamePrimitiveOrArrayOfPrimitives(shortClassName) &&
          !completionContext.contains(type)) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  public static String typeAsShortString(PsiType type) {
    if (type instanceof PsiClassType)
      return ((PsiClassType) type).getClassName();
    else if (type instanceof PsiPrimitiveType)
      return type.getCanonicalText();
    else if (type instanceof PsiArrayType) {
      String componentTypeAsString = typeAsShortString(((PsiArrayType)type).getComponentType());
      if (componentTypeAsString == null) return null;
      return String.format("%s[]", componentTypeAsString);
    }
    return null;
  }
}