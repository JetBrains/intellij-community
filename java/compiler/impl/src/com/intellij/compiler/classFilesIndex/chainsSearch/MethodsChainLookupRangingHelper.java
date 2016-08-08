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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ChainCompletionContext;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextRelevantStaticMethod;
import com.intellij.compiler.classFilesIndex.chainsSearch.context.ContextRelevantVariableGetter;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.ChainCompletionNewVariableLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.WeightableChainLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.GetterLookupSubLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.SubLookupElement;
import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.VariableSubLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.ChainCompletionLookupElementUtil.createLookupElement;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class MethodsChainLookupRangingHelper {

  public static List<LookupElement> chainsToWeightableLookupElements(final List<MethodsChain> chains,
                                                                     final ChainCompletionContext context) {
    final CachedRelevantStaticMethodSearcher staticMethodSearcher = new CachedRelevantStaticMethodSearcher(context);
    final List<LookupElement> lookupElements = new ArrayList<>(chains.size());
    for (final MethodsChain chain : chains) {
      final LookupElement lookupElement = chainToWeightableLookupElement(chain, context, staticMethodSearcher);
      if (lookupElement != null) {
        lookupElements.add(lookupElement);
      }
    }
    return lookupElements;
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  private static LookupElement chainToWeightableLookupElement(final MethodsChain chain,
                                                              final ChainCompletionContext context,
                                                              final CachedRelevantStaticMethodSearcher staticMethodSearcher) {
    final int chainSize = chain.size();
    assert chainSize != 0;
    final int lastMethodWeight = chain.getChainWeight();
    int unreachableParametersCount = 0;
    int notMatchedStringVars = 0;
    int matchedParametersInContext = 0;
    Boolean isFirstMethodStatic = null;
    Boolean hasCallingVariableInContext = null;
    LookupElement chainLookupElement = null;
    PsiClass newVariableClass = null;
    final NullableNotNullManager nullableNotNullManager = NullableNotNullManager.getInstance(context.getProject());

    for (final PsiMethod[] psiMethods : chain.getPath()) {
      final PsiMethod method =
        MethodChainsSearchUtil.getMethodWithMinNotPrimitiveParameters(psiMethods, Collections.singleton(context.getTarget().getClassQName()));
      if (method == null) {
        return null;
      }
      if (isFirstMethodStatic == null) {
        isFirstMethodStatic = psiMethods[0].hasModifierProperty(PsiModifier.STATIC);
      }
      final PsiClass qualifierClass;
      final boolean isHead = chainLookupElement == null;
      if (isHead) {
        final String qualifierClassName = chain.getQualifierClassName();
        qualifierClass = JavaPsiFacade.getInstance(context.getProject()).
          findClass(qualifierClassName, context.getResolveScope());
      }
      else {
        qualifierClass = null;
      }

      final MethodProcResult procResult = processMethod(method, qualifierClass, isHead, lastMethodWeight, context, staticMethodSearcher,
                                                        nullableNotNullManager);
      if (procResult == null) {
        return null;
      }
      if (hasCallingVariableInContext == null) {
        hasCallingVariableInContext = procResult.hasCallingVariableInContext();
      }
      if (isHead && procResult.isIntroduceNewVariable()) {
        newVariableClass = qualifierClass;
      }
      matchedParametersInContext += procResult.getMatchedParametersInContext();
      unreachableParametersCount += procResult.getUnreachableParametersCount();
      notMatchedStringVars += procResult.getNotMatchedStringVars();
      chainLookupElement =
        isHead ? procResult.getLookupElement() : new JavaChainLookupElement(chainLookupElement, procResult.getLookupElement());
    }

    if (newVariableClass != null) {
      chainLookupElement = ChainCompletionNewVariableLookupElement.create(newVariableClass, chainLookupElement);
    }

    final ChainRelevance relevance =
      new ChainRelevance(chainSize, lastMethodWeight, unreachableParametersCount, notMatchedStringVars, hasCallingVariableInContext,
                         isFirstMethodStatic, matchedParametersInContext);

    return new WeightableChainLookupElement(chainLookupElement, relevance);
  }


  @Nullable
  private static MethodProcResult processMethod(@NotNull final PsiMethod method,
                                                @Nullable final PsiClass qualifierClass,
                                                final boolean isHeadMethod,
                                                final int weight,
                                                final ChainCompletionContext context,
                                                final CachedRelevantStaticMethodSearcher staticMethodSearcher,
                                                final NullableNotNullManager nullableNotNullManager) {
    int unreachableParametersCount = 0;
    int notMatchedStringVars = 0;
    int matchedParametersInContext = 0;
    boolean hasCallingVariableInContext = false;
    boolean introduceNewVariable = false;
    final PsiParameterList parameterList = method.getParameterList();
    final TIntObjectHashMap<SubLookupElement> parametersMap = new TIntObjectHashMap<>(parameterList.getParametersCount());
    final PsiParameter[] parameters = parameterList.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiParameter parameter = parameters[i];
      final String typeQName = parameter.getType().getCanonicalText();
      if (JAVA_LANG_STRING.equals(typeQName)) {
        final PsiVariable relevantStringVar = context.findRelevantStringInContext(parameter.getName());
        if (relevantStringVar == null) {
          notMatchedStringVars++;
        }
        else {
          parametersMap.put(i, new VariableSubLookupElement(relevantStringVar));
        }
      }
      else if (!ChainCompletionStringUtil.isPrimitiveOrArrayOfPrimitives(typeQName)) {
        final Collection<PsiVariable> contextVariables = context.getVariables(typeQName);
        final PsiVariable contextVariable = ContainerUtil.getFirstItem(contextVariables, null);
        if (contextVariable != null) {
          if (contextVariables.size() == 1) parametersMap.put(i, new VariableSubLookupElement(contextVariable));
          matchedParametersInContext++;
          continue;
        }
        final Collection<ContextRelevantVariableGetter> relevantVariablesGetters = context.getRelevantVariablesGetters(typeQName);
        final ContextRelevantVariableGetter contextVariableGetter = ContainerUtil.getFirstItem(relevantVariablesGetters, null);
        if (contextVariableGetter != null) {
          if (relevantVariablesGetters.size() == 1) parametersMap.put(i, contextVariableGetter.createSubLookupElement());
          matchedParametersInContext++;
          continue;
        }
        final Collection<PsiMethod> containingClassMethods = context.getContainingClassMethods(typeQName);
        final PsiMethod contextRelevantGetter = ContainerUtil.getFirstItem(containingClassMethods, null);
        if (contextRelevantGetter != null) {
          if (containingClassMethods.size() == 1) parametersMap.put(i, new GetterLookupSubLookupElement(method.getName()));
          matchedParametersInContext++;
          continue;
        }
        final ContextRelevantStaticMethod contextRelevantStaticMethod =
          ContainerUtil.getFirstItem(staticMethodSearcher.getRelevantStaticMethods(typeQName, weight), null);
        if (contextRelevantStaticMethod != null) {
          //
          // In most cases it is not really relevant
          //
          //parametersMap.put(i, contextRelevantStaticMethod.createLookupElement());
          matchedParametersInContext++;
          continue;
        }
        if (!nullableNotNullManager.isNullable(parameter, true)) {
          unreachableParametersCount++;
        }
      }
    }
    final LookupElement lookupElement;
    if (isHeadMethod) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        hasCallingVariableInContext = true;
        lookupElement = createLookupElement(method, parametersMap);
      }
      else if (method.isConstructor()) {
        return null;
      }
      else {
        @SuppressWarnings("ConstantConditions")
        final String classQName = qualifierClass.getQualifiedName();
        if (classQName == null) return null;
        final Object e = ContainerUtil.getFirstItem(context.getContextRefElements(classQName), null);
        if (e != null) {
          final LookupElement firstChainElement;
          if (e instanceof PsiVariable) {
            firstChainElement = new VariableLookupItem((PsiVariable)e);
          }
          else if (e instanceof PsiMethod) {
            firstChainElement = createLookupElement((PsiMethod)e, null);
          }
          else if (e instanceof LookupElement) {
            firstChainElement = (LookupElement)e;
          }
          else {
            throw new AssertionError();
          }
          hasCallingVariableInContext = true;
          lookupElement = new JavaChainLookupElement(firstChainElement, createLookupElement(method, parametersMap));
        }
        else {
          lookupElement = createLookupElement(method, parametersMap);
          if (!context.getContainingClassQNames().contains(classQName)) {
            introduceNewVariable = true;
          }
        }
      }
    }
    else {
      lookupElement = createLookupElement(method, parametersMap);
    }
    return new MethodProcResult(lookupElement,
                                unreachableParametersCount,
                                notMatchedStringVars,
                                hasCallingVariableInContext,
                                introduceNewVariable,
                                matchedParametersInContext);
  }

  private static class MethodProcResult {
    private final LookupElement myMethodLookup;
    private final int myUnreachableParametersCount;
    private final int myNotMatchedStringVars;
    private final boolean myHasCallingVariableInContext;
    private final boolean myIntroduceNewVariable;
    private final int myMatchedParametersInContext;

    private MethodProcResult(final LookupElement methodLookup,
                             final int unreachableParametersCount,
                             final int notMatchedStringVars,
                             final boolean hasCallingVariableInContext,
                             final boolean introduceNewVariable,
                             final int matchedParametersInContext) {
      myMethodLookup = methodLookup;
      myUnreachableParametersCount = unreachableParametersCount;
      myNotMatchedStringVars = notMatchedStringVars;
      myHasCallingVariableInContext = hasCallingVariableInContext;
      myIntroduceNewVariable = introduceNewVariable;
      myMatchedParametersInContext = matchedParametersInContext;
    }

    private boolean isIntroduceNewVariable() {
      return myIntroduceNewVariable;
    }

    private boolean hasCallingVariableInContext() {
      return myHasCallingVariableInContext;
    }

    private LookupElement getLookupElement() {
      return myMethodLookup;
    }

    private int getUnreachableParametersCount() {
      return myUnreachableParametersCount;
    }

    private int getNotMatchedStringVars() {
      return myNotMatchedStringVars;
    }

    public int getMatchedParametersInContext() {
      return myMatchedParametersInContext;
    }
  }
}

