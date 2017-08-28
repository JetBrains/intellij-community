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
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaLangInvokeHandleReference extends PsiReferenceBase<PsiLiteralExpression> implements InsertHandler<LookupElement> {

  private final PsiExpression myContext;

  public JavaLangInvokeHandleReference(@NotNull PsiLiteralExpression literal, @NotNull PsiExpression context) {
    super(literal);
    myContext = context;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return element;
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String name = (String)value;
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass ownerClass = getReflectiveClass(myContext);
        if (ownerClass != null) {
          switch (type) {
            case FIND_GETTER:
            case FIND_SETTER:
              return resolveField(name, ownerClass, JavaLangInvokeHandleReference::isNonStaticField);

            case FIND_STATIC_GETTER:
            case FIND_STATIC_SETTER:
              return resolveField(name, ownerClass, JavaLangInvokeHandleReference::isStaticField);

            case FIND_VIRTUAL:
              return resolveMethod(name, ownerClass, JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC:
              return resolveMethod(name, ownerClass, JavaLangInvokeHandleReference::isStaticMethod);

            case FIND_VAR_HANDLE:
              return resolveField(name, ownerClass, JavaLangInvokeHandleReference::isNonStaticField);

            case FIND_STATIC_VAR_HANDLE:
              return resolveField(name, ownerClass, JavaLangInvokeHandleReference::isStaticField);
          }
        }
      }
    }
    return null;
  }

  private static PsiElement resolveField(@NotNull String name, @NotNull ReflectiveClass ownerClass, Condition<? super PsiField> filter) {
    final PsiField field = ownerClass.getPsiClass().findFieldByName(name, true);
    return field != null && filter.value(field) ? field : null;
  }

  private PsiElement resolveMethod(@NotNull String name, @NotNull ReflectiveClass ownerClass, Condition<? super PsiMethod> filter) {
    PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(name, true);
    if (methods.length != 0) {
      methods = ContainerUtil.filter(methods, filter).toArray(PsiMethod.EMPTY_ARRAY);
      if (methods.length > 1) {
        final PsiMethodCallExpression definitionCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
        if (definitionCall != null) {
          final PsiExpression[] arguments = definitionCall.getArgumentList().getExpressions();
          if (arguments.length > 2) {
            final PsiExpression typeExpression = ParenthesesUtils.stripParentheses(arguments[2]);
            final ReflectiveSignature expectedSignature = composeMethodSignature(typeExpression);
            if (expectedSignature != null) {
              return ContainerUtil.find(methods, method -> expectedSignature.equals(getMethodSignature(method)));
            }
          }
        }
      }
    }
    return methods.length != 0 ? methods[0] : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass ownerClass = getReflectiveClass(myContext);
        if (ownerClass != null) {
          switch (type) {
            case FIND_GETTER:
            case FIND_SETTER:
              return lookupFields(ownerClass, JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_GETTER:
            case FIND_STATIC_SETTER:
              return lookupFields(ownerClass, JavaLangInvokeHandleReference::isStaticField);

            case FIND_VIRTUAL:
              return lookupMethods(ownerClass, JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC:
              return lookupMethods(ownerClass, JavaLangInvokeHandleReference::isStaticMethod);

            case FIND_VAR_HANDLE:
              return lookupFields(ownerClass, JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_VAR_HANDLE:
              return lookupFields(ownerClass, JavaLangInvokeHandleReference::isStaticField);
          }
        }
      }
    }
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  private Object[] lookupMethods(@NotNull ReflectiveClass ownerClass, Predicate<? super PsiMethod> filter) {
    return ownerClass.getPsiClass().getVisibleSignatures()
      .stream()
      .map(MethodSignatureBackedByPsiMethod::getMethod)
      .filter(filter)
      .sorted(Comparator.comparingInt((PsiMethod method) -> getMethodSortOrder(method)).thenComparing(PsiMethod::getName))
      .map(method -> withPriority(lookupMethod(method, this),
                                  -getMethodSortOrder(method)))
      .filter(Objects::nonNull)
      .toArray();
  }

  private Object[] lookupFields(@NotNull ReflectiveClass ownerClass, Predicate<? super PsiField> filter) {
    final Set<String> uniqueNames = new THashSet<>();
    return Arrays.stream(ownerClass.getPsiClass().getAllFields())
      .filter(field -> field != null &&
                       (field.getContainingClass() == ownerClass.getPsiClass() || !field.hasModifierProperty(PsiModifier.PRIVATE)) &&
                       field.getName() != null && uniqueNames.add(field.getName()))
      .filter(filter)
      .sorted(Comparator.comparing((PsiField field) -> isPublic(field) ? 0 : 1).thenComparing(PsiField::getName))
      .map(field -> withPriority(JavaLookupElementBuilder.forField(field).withInsertHandler(this), isPublic(field)))
      .toArray();
  }

  private static boolean isNonStaticField(PsiField field) {
    return field != null && !field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticField(PsiField field) {
    return field != null && field.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isNonStaticMethod(@Nullable PsiMethod method) {
    return isRegularMethod(method) && !method.hasModifierProperty(PsiModifier.STATIC);
  }

  private static boolean isStaticMethod(@Nullable PsiMethod method) {
    return isRegularMethod(method) && method.hasModifierProperty(PsiModifier.STATIC);
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
    final Object object = item.getObject();

    if (object instanceof ReflectiveSignature) {
      final String text = ", " + getMethodTypeExpressionText((ReflectiveSignature)object);
      replaceText(context, text);
    }
    else if (object instanceof PsiField) {
      final PsiField field = (PsiField)object;
      final String typeText = getTypeText(field.getType());
      final String text = ", " + typeText + ".class";
      replaceText(context, text);
    }
  }

  static class JavaLangInvokeHandleReferenceProvider extends PsiReferenceProvider {
    @NotNull
    @Override
    public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      if (element instanceof PsiLiteralExpression) {
        final PsiLiteralExpression literal = (PsiLiteralExpression)element;
        if (literal.getValue() instanceof String) {
          final PsiElement parent = element.getParent();
          if (parent instanceof PsiExpressionList) {
            final PsiExpression[] expressions = ((PsiExpressionList)parent).getExpressions();
            final PsiExpression qualifier = expressions.length != 0 ? expressions[0] : null;
            if (qualifier != null) {
              return new PsiReference[]{new JavaLangInvokeHandleReference(literal, qualifier)};
            }
          }
        }
      }
      return PsiReference.EMPTY_ARRAY;
    }
  }
}
