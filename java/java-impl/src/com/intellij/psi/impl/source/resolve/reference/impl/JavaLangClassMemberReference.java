/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Konstantin Bulenkov
 */
public class JavaLangClassMemberReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {
  private static final String FIELD = "getField";
  private static final String DECLARED_FIELD = "getDeclaredField";
  private static final String METHOD = "getMethod";
  private static final String DECLARED_METHOD = "getDeclaredMethod";

  private final PsiExpression myContext;

  public JavaLangClassMemberReference(@NotNull PsiLiteralExpression literal, @NotNull PsiExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Override
  public PsiElement resolve() {
    Object value = myElement.getValue();
    if (value instanceof String) {
      final String name = (String)value;
      final String type = getMemberType();

      if (type != null) {
        final PsiClass psiClass = getPsiClass();
        if (psiClass != null) {
          switch (type) {

            case FIELD: {
              return psiClass.findFieldByName(name, true);
            }

            case DECLARED_FIELD: {
              PsiField field = psiClass.findFieldByName(name, false);
              return isPotentiallyAccessible(field, psiClass) ? field : null;
            }

            case METHOD: {
              final PsiMethod[] methods = psiClass.findMethodsByName(name, true);
              final PsiMethod publicMethod = ContainerUtil.find(methods, method -> isRegularMethod(method) && isPublic(method));
              if (publicMethod != null) {
                return publicMethod;
              }
              return ContainerUtil.find(methods, method -> isRegularMethod(method));
            }

            case DECLARED_METHOD: {
              final PsiMethod[] methods = psiClass.findMethodsByName(name, false);
              return ContainerUtil.find(methods, method -> isRegularMethod(method) && isPotentiallyAccessible(method, psiClass));
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private PsiClass getPsiClass() {
    return getReflectiveClass(myContext);
  }

  @Nullable
  private String getMemberType() {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
    return methodCall != null ? methodCall.getMethodExpression().getReferenceName() : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final String type = getMemberType();
    if (type != null) {
      final PsiClass psiClass = getPsiClass();
      if (psiClass != null) {
        switch (type) {

          case DECLARED_FIELD:
            return Arrays.stream(psiClass.getFields())
              .filter(field -> field.getName() != null)
              .sorted(Comparator.comparing(PsiField::getName))
              .map(field -> lookupField(field))
              .toArray();

          case FIELD: {
            final Set<String> uniqueNames = new THashSet<>();
            return Arrays.stream(psiClass.getAllFields())
              .filter(field -> isPotentiallyAccessible(field, psiClass) && field.getName() != null && uniqueNames.add(field.getName()))
              .sorted(Comparator.comparingInt((PsiField field) -> isPublic(field) ? 0 : 1).thenComparing(PsiField::getName))
              .map(field -> withPriority(lookupField(field), isPublic(field)))
              .toArray();
          }

          case DECLARED_METHOD:
            return Arrays.stream(psiClass.getMethods())
              .filter(method -> isRegularMethod(method))
              .sorted(Comparator.comparing(PsiMethod::getName))
              .map(method -> lookupMethod(method))
              .toArray();

          case METHOD: {
            return psiClass.getVisibleSignatures()
              .stream()
              .map(MethodSignatureBackedByPsiMethod::getMethod)
              .filter(method -> isRegularMethod(method) && isPotentiallyAccessible(method, psiClass))
              .sorted(Comparator.comparingInt((PsiMethod method) -> getMethodSortOrder(method)).thenComparing(PsiMethod::getName))
              .map(method -> withPriority(lookupMethod(method), -getMethodSortOrder(method)))
              .toArray();
          }
        }
      }
    }
    return EMPTY_ARRAY;
  }

  private static int getMethodSortOrder(PsiMethod method) {
    return isJavaLangObject(method.getContainingClass()) ? 1 : isPublic(method) ? -1 : 0;
  }

  @NotNull
  private static LookupElement lookupField(PsiField field) {
    return JavaLookupElementBuilder.forField(field);
  }

  @NotNull
  private LookupElement lookupMethod(PsiMethod method) {
    return JavaLookupElementBuilder.forMethod(method, PsiSubstitutor.EMPTY).withInsertHandler(this);
  }

  @Override
  public void handleInsert(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof PsiMethod) {
      final PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
      final int start = newElement.getTextRange().getEndOffset();
      final PsiElement params = newElement.getParent().getParent();
      final int end = params.getTextRange().getEndOffset() - 1;
      String types = getParameterTypesText((PsiMethod)object);
      if (!types.isEmpty()) types = ", " + types;
      context.getDocument().replaceString(start, end, types);
      context.commitDocument();
      shortenArgumentsClassReferences(context);
    }
  }
}
