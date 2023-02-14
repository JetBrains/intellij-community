// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInspection.reference.PsiMemberReference;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

public class JavaLangInvokeHandleReference extends PsiReferenceBase<PsiLiteralExpression>
  implements InsertHandler<LookupElement>, PsiMemberReference {

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
    if (value instanceof String name) {
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass ownerClass = getReflectiveClass(myContext);
        if (ownerClass != null) {
          return switch (type) {
            case FIND_GETTER, FIND_SETTER, FIND_VAR_HANDLE ->
              resolveField(name, ownerClass, JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_STATIC_VAR_HANDLE ->
              resolveField(name, ownerClass, JavaLangInvokeHandleReference::isStaticField);
            case FIND_VIRTUAL, FIND_SPECIAL -> resolveMethod(name, ownerClass, JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC -> resolveMethod(name, ownerClass, JavaLangInvokeHandleReference::isStaticMethod);
            default -> null;
          };
        }
      }
    }
    return null;
  }

  private static PsiElement resolveField(@NotNull String name, @NotNull ReflectiveClass ownerClass, Predicate<? super PsiField> filter) {
    final PsiField field = ownerClass.getPsiClass().findFieldByName(name, true);
    return field != null && filter.test(field) ? field : null;
  }

  private PsiElement resolveMethod(@NotNull String name, @NotNull ReflectiveClass ownerClass, Predicate<? super PsiMethod> filter) {
    PsiMethod[] methods = ownerClass.getPsiClass().findMethodsByName(name, true);
    if (methods.length != 0) {
      methods = ContainerUtil.filter(methods, filter::test).toArray(PsiMethod.EMPTY_ARRAY);
      if (methods.length > 1) {
        final PsiMethodCallExpression definitionCall = PsiTreeUtil.getParentOfType(myElement, PsiMethodCallExpression.class);
        if (definitionCall != null) {
          final PsiExpression[] arguments = definitionCall.getArgumentList().getExpressions();
          if (arguments.length > 2) {
            final PsiExpression typeExpression = PsiUtil.skipParenthesizedExprDown(arguments[2]);
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

  @Override
  public Object @NotNull [] getVariants() {
    final Object value = myElement.getValue();
    if (value instanceof String) {
      final String type = getMemberType(myElement);

      if (type != null) {
        final ReflectiveClass ownerClass = getReflectiveClass(myContext);
        if (ownerClass != null) {
          return switch (type) {
            case FIND_GETTER, FIND_SETTER, FIND_VAR_HANDLE ->
              lookupFields(ownerClass, JavaLangInvokeHandleReference::isNonStaticField);
            case FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_STATIC_VAR_HANDLE ->
              lookupFields(ownerClass, JavaLangInvokeHandleReference::isStaticField);
            case FIND_VIRTUAL -> lookupMethods(ownerClass, JavaLangInvokeHandleReference::isNonStaticMethod);
            case FIND_STATIC -> lookupMethods(ownerClass, JavaLangInvokeHandleReference::isStaticMethod);
            default -> ArrayUtilRt.EMPTY_OBJECT_ARRAY;
          };
        }
      }
    }
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
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
    final Set<String> uniqueNames = new HashSet<>();
    return Arrays.stream(ownerClass.getPsiClass().getAllFields())
      .filter(field -> field != null &&
                       (field.getContainingClass() == ownerClass.getPsiClass() || !field.hasModifierProperty(PsiModifier.PRIVATE)) &&
                       uniqueNames.add(field.getName()))
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
    else if (object instanceof PsiField field) {
      final String typeText = getTypeText(field.getType());
      final String text = ", " + typeText + ".class";
      replaceText(context, text);
    }
  }

  static class JavaLangInvokeHandleReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
      if (element instanceof PsiLiteralExpression literal &&
          literal.getValue() instanceof String &&
          element.getParent() instanceof PsiExpressionList list) {
        final PsiExpression[] expressions = list.getExpressions();
        final PsiExpression qualifier = expressions.length != 0 ? expressions[0] : null;
        if (qualifier != null) {
          return new PsiReference[]{new JavaLangInvokeHandleReference(literal, qualifier)};
        }
      }
      return PsiReference.EMPTY_ARRAY;
    }
  }
}
