/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaChainLookupElement;
import com.intellij.codeInsight.completion.JavaMethodCallElement;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.VariableLookupItem;
import com.intellij.compiler.chainsSearch.completion.lookup.ChainCompletionNewVariableLookupElement;
import com.intellij.compiler.chainsSearch.completion.lookup.JavaRelevantChainLookupElement;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.stream.Collectors;

public class MethodChainLookupRangingHelper {
  @Nullable
  public static LookupElement chainToWeightableLookupElement(MethodChain chain,
                                                             ChainCompletionContext context) {
    int chainLength = chain.length();
    assert chainLength != 0;
    int unreachableParametersCount = 0;
    int matchedParametersInContext = 0;
    Boolean isFirstMethodStatic = null;
    Boolean hasQualifierInContext = null;
    LookupElement chainLookupElement = null;
    PsiClass newVariableClass = null;
    NullableNotNullManager nullableNotNullManager = NullableNotNullManager.getInstance(context.getProject());

    for (PsiMethod[] psiMethods : chain.getPath()) {
      PsiMethod method =
        MethodChainsSearchUtil.getMethodWithMinNotPrimitiveParameters(psiMethods, context.getTarget().getTargetClass());
      if (method == null) {
        return null;
      }
      if (isFirstMethodStatic == null) {
        isFirstMethodStatic = psiMethods[0].hasModifierProperty(PsiModifier.STATIC);
      }
      PsiClass qualifierClass;
      boolean isHead = chainLookupElement == null;
      if (isHead) {
        qualifierClass = chain.getQualifierClass();
      }
      else {
        qualifierClass = null;
      }

      MethodProcResult procResult = processMethod(method, qualifierClass, isHead, context, nullableNotNullManager);
      if (procResult == null) {
        return null;
      }
      if (hasQualifierInContext == null) {
        hasQualifierInContext = procResult.hasCallingVariableInContext();
      }
      if (isHead && procResult.isIntroduceNewVariable()) {
        newVariableClass = qualifierClass;
      }
      matchedParametersInContext += procResult.getMatchedParametersInContext();
      unreachableParametersCount += procResult.getUnreachableParametersCount();
      chainLookupElement =
        isHead ? procResult.getLookupElement() : new JavaChainLookupElement(chainLookupElement, procResult.getLookupElement());
    }

    if (newVariableClass != null) {
      chainLookupElement = new JavaChainLookupElement(new ChainCompletionNewVariableLookupElement(newVariableClass), chainLookupElement);
    }

    ChainRelevance relevance =
      new ChainRelevance(chainLength, unreachableParametersCount, matchedParametersInContext);

    if (context.getTarget().isIteratorAccess()) {
      chainLookupElement = new LookupElementDecorator<LookupElement>(chainLookupElement) {
        @Override
        public void handleInsert(InsertionContext context) {
          super.handleInsert(context);
          Document document = context.getDocument();
          int tail = context.getTailOffset();
          PsiType tailReturnType = chain.getFirst()[0].getReturnType();
          if (tailReturnType instanceof PsiArrayType) {
            document.insertString(tail, "[0]");
            context.getEditor().getCaretModel().moveToOffset(tail + 1);
          } else {
            PsiClass returnClass = ObjectUtils.notNull(PsiUtil.resolveClassInClassTypeOnly(tailReturnType));
            PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(document);
            if (InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_LIST)) {
              document.insertString(tail, ".get(0)");
              context.getEditor().getCaretModel().moveToOffset(tail + 5);
            } else if (InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
              document.insertString(tail, ".iterator().next()");
            } else if (InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_ITERATOR)) {
              document.insertString(tail, ".next()");
            } else if (InheritanceUtil.isInheritor(returnClass, CommonClassNames.JAVA_UTIL_STREAM_STREAM)) {
              document.insertString(tail, ".findFirst().get()");
            }
          }
        }
      };
    }

    return new JavaRelevantChainLookupElement(chainLookupElement, relevance);
  }


  @Nullable
  private static MethodProcResult processMethod(@NotNull PsiMethod method,
                                                @Nullable PsiClass qualifierClass,
                                                boolean isHeadMethod,
                                                ChainCompletionContext context,
                                                NullableNotNullManager nullableNotNullManager) {
    int unreachableParametersCount = 0;
    int matchedParametersInContext = 0;
    boolean hasCallingVariableInContext = false;
    boolean introduceNewVariable = false;
    PsiParameterList parameterList = method.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      PsiType type = parameter.getType();
      if (!ChainCompletionContext.isWidelyUsed(type)) {
        Collection<PsiElement> contextVariables = context.getQualifiers(type).collect(Collectors.toList());
        PsiElement contextVariable = ContainerUtil.getFirstItem(contextVariables, null);
        if (contextVariable != null) {
          matchedParametersInContext++;
          continue;
        }
        if (!nullableNotNullManager.isNullable(parameter, true)) {
          unreachableParametersCount++;
        }
      }
    }
    LookupElement lookupElement;
    if (isHeadMethod) {
      if (method.hasModifierProperty(PsiModifier.STATIC)) {
        hasCallingVariableInContext = true;
        lookupElement = createMethodLookupElement(method);
      }
      else if (method.isConstructor()) {
        return null;
      }
      else {
        Object e = context.getQualifiers(qualifierClass).findFirst().orElse(null);
        if (e != null) {
          LookupElement firstChainElement;
          if (e instanceof PsiVariable) {
            firstChainElement = new VariableLookupItem((PsiVariable)e);
          }
          else if (e instanceof PsiMethod) {
            firstChainElement = createMethodLookupElement((PsiMethod)e);
          }
          else if (e instanceof LookupElement) {
            firstChainElement = (LookupElement)e;
          }
          else {
            throw new AssertionError();
          }
          hasCallingVariableInContext = true;
          lookupElement = new JavaChainLookupElement(firstChainElement, createMethodLookupElement(method));
        }
        else {
          lookupElement = createMethodLookupElement(method);
          if (!context.hasQualifier(qualifierClass)) {
            introduceNewVariable = true;
          }
        }
      }
    }
    else {
      lookupElement = createMethodLookupElement(method);
    }
    return new MethodProcResult(lookupElement,
                                unreachableParametersCount,
                                hasCallingVariableInContext,
                                introduceNewVariable,
                                matchedParametersInContext);
  }

  @NotNull
  private static LookupElement createMethodLookupElement(@NotNull PsiMethod method) {
    LookupElement result;
    if (method.isConstructor()) {
      //noinspection ConstantConditions
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
      result = new ExpressionLookupItem(elementFactory.createExpressionFromText("new " + method.getContainingClass().getQualifiedName() + "()", null));
    } else if (method.hasModifierProperty(PsiModifier.STATIC)) {
      result = new JavaMethodCallElement(method, false, true);
    } else {
      result = new JavaMethodCallElement(method);
    }
    return result;
  }

  private static class MethodProcResult {
    private final LookupElement myMethodLookup;
    private final int myUnreachableParametersCount;
    private final boolean myHasCallingVariableInContext;
    private final boolean myIntroduceNewVariable;
    private final int myMatchedParametersInContext;

    private MethodProcResult(LookupElement methodLookup,
                             int unreachableParametersCount,
                             boolean hasCallingVariableInContext,
                             boolean introduceNewVariable,
                             int matchedParametersInContext) {
      myMethodLookup = methodLookup;
      myUnreachableParametersCount = unreachableParametersCount;
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

    public int getMatchedParametersInContext() {
      return myMatchedParametersInContext;
    }
  }
}

