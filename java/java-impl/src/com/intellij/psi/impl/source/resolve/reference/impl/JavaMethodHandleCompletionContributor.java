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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PsiJavaElementPattern;
import com.intellij.patterns.PsiMethodPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaMethodHandleCompletionContributor extends CompletionContributor {

  // MethodHandle for constructors and methods
  private static final Set<String> METHOD_HANDLE_FACTORY_NAMES = ContainerUtil.immutableSet(
    FIND_CONSTRUCTOR, FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL);

  private static final PsiJavaElementPattern.Capture<PsiElement> METHOD_TYPE_ARGUMENT_PATTERN = psiElement().afterLeaf(",")
    .withParent(or(
      psiExpression().methodCallParameter(1, methodPattern(FIND_CONSTRUCTOR)),
      psiExpression().methodCallParameter(2, methodPattern(FIND_VIRTUAL, FIND_STATIC, FIND_SPECIAL))));


  // VarHandle for fields and synthetic MethodHandle for field getters/setters
  private static final Set<String> FIELD_HANDLE_FACTORY_NAMES = ContainerUtil.immutableSet(
    FIND_GETTER, FIND_SETTER, FIND_STATIC_GETTER, FIND_STATIC_SETTER, FIND_VAR_HANDLE, FIND_STATIC_VAR_HANDLE);

  private static final PsiJavaElementPattern.Capture<PsiElement> FIELD_TYPE_ARGUMENT_PATTERN = psiElement().afterLeaf(",")
    .withParent(
      psiExpression().methodCallParameter(2, methodPattern(ArrayUtil.toStringArray(FIELD_HANDLE_FACTORY_NAMES))));


  @NotNull
  private static PsiMethodPattern methodPattern(String... methodNames) {
    return psiMethod().withName(methodNames).definedInClass(JAVA_LANG_INVOKE_METHOD_HANDLES_LOOKUP);
  }

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.BASIC) {
      return;
    }

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

  private static void addMethodHandleVariants(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      final String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && METHOD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        final PsiClass psiClass = arguments.length != 0 ? getReflectiveClass(arguments[0]) : null;
        if (psiClass != null) {

          switch (methodName) {
            case FIND_CONSTRUCTOR:
              addConstructorSignatures(psiClass, result);
              break;

            case FIND_VIRTUAL:
            case FIND_STATIC:
            case FIND_SPECIAL:
              final String name = arguments.length > 1 ? computeConstantExpression(arguments[1], String.class) : null;
              if (!StringUtil.isEmpty(name)) {
                addMethodSignatures(psiClass, name, FIND_STATIC.equals(methodName), result);
              }
              break;
          }
        }
      }
    }
  }

  private static void addConstructorSignatures(@NotNull PsiClass psiClass, @NotNull CompletionResultSet result) {
    final String className = psiClass.getName();
    if (className != null) {
      final PsiMethod[] constructors = psiClass.getConstructors();
      if (constructors.length != 0) {
        lookupMethodTypes(Arrays.stream(constructors), result);
      }
      else {
        result.addElement(lookupSignature(ReflectiveSignature.NO_ARGUMENT_CONSTRUCTOR_SIGNATURE));
      }
    }
  }

  private static void addMethodSignatures(@NotNull PsiClass psiClass,
                                          @NotNull String methodName,
                                          boolean isStaticExpected,
                                          @NotNull CompletionResultSet result) {
    final PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
    if (methods.length != 0) {
      final Stream<PsiMethod> methodStream = Arrays.stream(methods)
        .filter(method -> method.hasModifierProperty(PsiModifier.STATIC) == isStaticExpected);
      lookupMethodTypes(methodStream, result);
    }
  }

  private static void lookupMethodTypes(@NotNull Stream<PsiMethod> methods, @NotNull CompletionResultSet result) {
    methods
      .map(JavaReflectionReferenceUtil::getMethodSignature)
      .filter(Objects::nonNull)
      .sorted(ReflectiveSignature::compareTo)
      .map(JavaMethodHandleCompletionContributor::lookupSignature)
      .forEach(result::addElement);
  }

  @NotNull
  private static LookupElement lookupSignature(@NotNull ReflectiveSignature signature) {
    final String types = signature.stream()
      .map(text -> PsiNameHelper.getShortClassName(text) + ".class")
      .collect(Collectors.joining(", "));
    final String text = PsiNameHelper.getShortClassName(JAVA_LANG_INVOKE_METHOD_TYPE) + "." + METHOD_TYPE + "(" + types + ")";

    final LookupElementBuilder element = LookupElementBuilder
      .create(signature, "")
      .withPresentableText(text)
      .withIcon(PlatformIcons.METHOD_ICON)
      .withInsertHandler(JavaMethodHandleCompletionContributor::handleInsertMethodType);

    return PrioritizedLookupElement.withPriority(element, 1);
  }

  private static void handleInsertMethodType(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof ReflectiveSignature) {
      final String text = getMethodTypeExpressionText((ReflectiveSignature)object);
      replaceText(context, text);
    }
  }

  private static void addFieldHandleVariants(@NotNull PsiElement position, @NotNull CompletionResultSet result) {
    final PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      final String methodName = methodCall.getMethodExpression().getReferenceName();
      if (methodName != null && FIELD_HANDLE_FACTORY_NAMES.contains(methodName)) {
        final PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
        if (arguments.length > 2) {
          final String fieldName = computeConstantExpression(arguments[1], String.class);
          if (!StringUtil.isEmpty(fieldName)) {
            final PsiClass psiClass = getReflectiveClass(arguments[0]);
            if (psiClass != null) {
              addFieldType(psiClass, fieldName, result);
            }
          }
        }
      }
    }
  }

  private static void addFieldType(@NotNull PsiClass psiClass, @NotNull String fieldName, @NotNull CompletionResultSet result) {
    final PsiField field = psiClass.findFieldByName(fieldName, false);
    if (field != null) {
      final String typeText = getTypeText(field.getType(), field);
      if (typeText != null) {
        final LookupElementBuilder element = LookupElementBuilder
          .create(new TypeLiteral(typeText), "")
          .withPresentableText(PsiNameHelper.getShortClassName(typeText) + ".class")
          .withIcon(PlatformIcons.CLASS_ICON)
          .withInsertHandler(JavaMethodHandleCompletionContributor::handleInsertFieldType);
        result.addElement(PrioritizedLookupElement.withPriority(element, 1));
      }
    }
  }

  private static void handleInsertFieldType(InsertionContext context, LookupElement item) {
    final Object object = item.getObject();
    if (object instanceof TypeLiteral) {
      final String text = ((TypeLiteral)object).getText();
      replaceText(context, text);
    }
  }

  private static class TypeLiteral {
    private final String myType;

    TypeLiteral(@NotNull String type) {myType = type;}

    @NotNull
    String getText() {return myType + ".class";}
  }
}
