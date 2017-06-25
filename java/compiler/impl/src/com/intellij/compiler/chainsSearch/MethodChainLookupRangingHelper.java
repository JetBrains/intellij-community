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
import com.intellij.openapi.util.Couple;
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
  @NotNull
  public static LookupElement toLookupElement(MethodChain chain,
                                              ChainCompletionContext context) {
    int unreachableParametersCount = 0;
    int matchedParametersInContext = 0;
    LookupElement chainLookupElement = null;

    for (PsiMethod[] psiMethods : chain.getPath()) {
      PsiMethod method = ObjectUtils.notNull(MethodChainsSearchUtil.getMethodWithMinNotPrimitiveParameters(psiMethods, context.getTarget().getTargetClass()));
      Couple<Integer> info = calculateParameterInfo(method, context);
      unreachableParametersCount += info.getFirst();
      matchedParametersInContext += info.getSecond();

      if (chainLookupElement == null) {
        LookupElement qualifierLookupElement = createQualifierLookupElement(method, chain.getQualifierClass(), context);
        LookupElement headLookupElement = createMethodLookupElement(method);
        chainLookupElement = qualifierLookupElement == null ? headLookupElement : new JavaChainLookupElement(qualifierLookupElement, headLookupElement);
      } else {
        chainLookupElement = new JavaChainLookupElement(chainLookupElement, new JavaMethodCallElement(method));
      }
    }

    if (context.getTarget().isIteratorAccess()) {
      chainLookupElement = decorateWithIteratorAccess(chain.getFirst()[0], chainLookupElement);
    }

    return new JavaRelevantChainLookupElement(ObjectUtils.notNull(chainLookupElement),
                                              new ChainRelevance(chain.length(), unreachableParametersCount, matchedParametersInContext));
  }

  @NotNull
  private static LookupElementDecorator<LookupElement> decorateWithIteratorAccess(PsiMethod method, LookupElement chainLookupElement) {
    return new LookupElementDecorator<LookupElement>(chainLookupElement) {
      @Override
      public void handleInsert(InsertionContext context) {
        super.handleInsert(context);
        Document document = context.getDocument();
        int tail = context.getTailOffset();
        PsiType tailReturnType = method.getReturnType();
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

  @Nullable
  private static LookupElement createQualifierLookupElement(@NotNull PsiMethod method, @NotNull PsiClass qualifierClass, @NotNull ChainCompletionContext context) {
    if (method.hasModifierProperty(PsiModifier.STATIC)) return null;
    PsiNamedElement element = context.getQualifiers(qualifierClass).findFirst().orElse(null);
    if (element == null) {
      return new ChainCompletionNewVariableLookupElement(qualifierClass, context);
    } else {
      if (element instanceof PsiVariable) {
        return new VariableLookupItem((PsiVariable)element);
      }
      else if (element instanceof PsiMethod) {
        return createMethodLookupElement((PsiMethod)element);
      }
      throw new AssertionError("unexpected element: " + element);
    }
  }

  @NotNull
  private static Couple<Integer> calculateParameterInfo(@NotNull PsiMethod method,
                                                        @NotNull ChainCompletionContext context) {
    NullableNotNullManager nullableNotNullManager = NullableNotNullManager.getInstance(method.getProject());
    int unreachableParametersCount = 0;
    int matchedParametersInContext = 0;
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
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

    return Couple.of(unreachableParametersCount, matchedParametersInContext);
  }

  @NotNull
  private static LookupElement createMethodLookupElement(@NotNull PsiMethod method) {
    LookupElement result;
    if (method.isConstructor()) {
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(method.getProject());
      result = new ExpressionLookupItem(elementFactory.createExpressionFromText("new " + method.getContainingClass().getQualifiedName() + "()", null));
    } else if (method.hasModifierProperty(PsiModifier.STATIC)) {
      result = new JavaMethodCallElement(method, false, true);
    } else {
      result = new JavaMethodCallElement(method);
    }
    return result;
  }
}

