// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.ExpressionLookupItem;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.IconManager;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

public class JavaMethodHandleCompletionContributor extends CompletionContributor implements DumbAware {

  // MethodHandle for constructors and methods
  private static final Set<String> METHOD_HANDLE_FACTORY_NAMES = Set.of(
    FIND_CONSTRUCTOR, FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL);

  private static final PsiJavaElementPattern.Capture<PsiElement> METHOD_TYPE_ARGUMENT_PATTERN = psiElement().afterLeaf(",")
    .withParent(or(
      psiExpression().methodCallParameter(1, methodPattern(FIND_CONSTRUCTOR)),
      psiExpression().methodCallParameter(2, methodPattern(FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL))));


  // VarHandle for fields and synthetic MethodHandle for field getters/setters
  private static final Set<String> FIELD_HANDLE_FACTORY_NAMES = Set.of(
    FIND_GETTER, FIND_SETTER, FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE);

  private static final PsiJavaElementPattern.Capture<PsiElement> FIELD_TYPE_ARGUMENT_PATTERN = psiElement().afterLeaf(",")
    .withParent(
      psiExpression().methodCallParameter(2, methodPattern(ArrayUtilRt.toStringArray(FIELD_HANDLE_FACTORY_NAMES))));


  @NotNull
  private static PsiMethodPattern methodPattern(String... methodNames) {
    return psiMethod().withName(methodNames).definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP);
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) {
      return;
    }

    if (METHOD_TYPE_ARGUMENT_PATTERN.accepts(position)) {
      addMethodHandleVariants(position, result);
    }
    else if (FIELD_TYPE_ARGUMENT_PATTERN.accepts(position)) {
      addFieldHandleVariants(position, result);
    }
  }

  private static void addMethodHandleVariants(@NotNull PsiElement position, @NotNull Consumer<? super LookupElement> result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      final String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && METHOD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        final ReflectiveClass ownerClass = arguments.length != 0 ? getReflectiveClass(arguments[0]) : null;
        if (ownerClass != null) {

          switch (methodName) {
            case FIND_CONSTRUCTOR -> addConstructorSignatures(ownerClass, position, result);
            case FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL -> {
              final String name = arguments.length > 1 ? computeConstantExpression(arguments[1], String.class) : null;
              if (!StringUtil.isEmpty(name)) {
                addMethodSignatures(ownerClass, name, FIND_STATIC.equals(methodName), position, result);
              }
            }
          }
        }
      }
    }
  }

  private static void addConstructorSignatures(@NotNull ReflectiveClass ownerClass,
                                               @NotNull PsiElement context,
                                               @NotNull Consumer<? super LookupElement> result) {
    final PsiMethod[] constructors = ownerClass.getPsiClass().getConstructors();
    if (constructors.length != 0) {
      lookupMethodTypes(Arrays.stream(constructors), context, result);
    }
    else {
      result.consume(lookupSignature(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE, context));
    }
  }

  private static void addMethodSignatures(@NotNull ReflectiveClass psiClass,
                                          @NotNull String methodName,
                                          boolean isStaticExpected,
                                          @NotNull PsiElement context,
                                          @NotNull Consumer<? super LookupElement> result) {
    final PsiMethod[] methods = psiClass.getPsiClass().findMethodsByName(methodName, false);
    if (methods.length != 0) {
      final Stream<PsiMethod> methodStream = Arrays.stream(methods)
        .filter(method -> method.hasModifierProperty(PsiModifier.STATIC) == isStaticExpected);
      lookupMethodTypes(methodStream, context, result);
    }
  }

  private static void lookupMethodTypes(@NotNull Stream<? extends PsiMethod> methods,
                                        @NotNull PsiElement context,
                                        @NotNull Consumer<? super LookupElement> result) {
    methods
      .map(JavaReflectionReferenceUtil::getMethodSignature)
      .filter(Objects::nonNull)
      .sorted(ReflectiveSignature::compareTo)
      .map(signature -> lookupSignature(signature, context))
      .forEach(result::consume);
  }

  @NotNull
  private static LookupElement lookupSignature(@NotNull ReflectiveSignature signature, @NotNull PsiElement context) {
    final String expressionText = getMethodTypeExpressionText(signature);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.getProject());
    final PsiExpression expression = factory.createExpressionFromText(expressionText, context);

    final String shortTypes = signature.getText(true, type -> PsiNameHelper.getShortClassName(type) + ".class");
    final String presentableText = PsiNameHelper.getShortClassName(JAVA_LANG_INVOKE_METHOD_TYPE) + "." + METHOD_TYPE + shortTypes;
    final String lookupText = METHOD_TYPE + signature.getText(true, PsiNameHelper::getShortClassName);

    return lookupExpression(expression, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method), presentableText, lookupText);
  }

  private static void addFieldHandleVariants(@NotNull PsiElement position, @NotNull Consumer<? super LookupElement> result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      final String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && FIELD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        if (arguments.length > 2) {
          final String fieldName = computeConstantExpression(arguments[1], String.class);
          if (!StringUtil.isEmpty(fieldName)) {
            final ReflectiveClass ownerClass = getReflectiveClass(arguments[0]);
            if (ownerClass != null) {
              addFieldType(ownerClass, fieldName, position, result);
            }
          }
        }
      }
    }
  }

  private static void addFieldType(@NotNull ReflectiveClass ownerClass,
                                   @NotNull String fieldName,
                                   @NotNull PsiElement context,
                                   @NotNull Consumer<? super LookupElement> result) {
    final PsiField field = ownerClass.getPsiClass().findFieldByName(fieldName, false);
    if (field != null) {
      final String typeText = getTypeText(field.getType());
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(ownerClass.getPsiClass().getProject());
      final PsiExpression expression = factory.createExpressionFromText(typeText + ".class", context);

      final String shortType = PsiNameHelper.getShortClassName(typeText);
      result.consume(lookupExpression(expression, IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class), shortType + ".class", shortType));
    }
  }

  @NotNull
  private static LookupElement lookupExpression(@NotNull PsiExpression expression,
                                                @Nullable Icon icon,
                                                @NotNull String presentableText,
                                                @NotNull String lookupText) {
    final LookupElement element = new ExpressionLookupItem(expression, icon, presentableText, lookupText) {
      @Override
      public void handleInsert(@NotNull InsertionContext context) {
        context.getDocument().deleteString(context.getStartOffset(), context.getTailOffset());
        context.commitDocument();
        replaceText(context, getObject().getText());
      }
    };
    return PrioritizedLookupElement.withPriority(element, 1);
  }
}
