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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.patterns.PsiJavaPatterns.*;
import static com.intellij.patterns.StandardPatterns.or;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.*;

/**
 * @author Pavel.Dolgov
 */
public class JavaReflectionCompletionContributor extends CompletionContributor {

  private static final ElementPattern<? extends PsiElement> CONSTRUCTOR_ARGUMENTS = psiElement(PsiExpressionList.class)
    .withParent(psiExpression().methodCall(
      psiMethod()
        .withName("getConstructor", "getDeclaredConstructor")
        .definedInClass(CommonClassNames.JAVA_LANG_CLASS)));

  private static final ElementPattern<? extends PsiElement> ANNOTATION_ARGUMENTS = psiElement(PsiExpressionList.class)
    .withParent(psiExpression().methodCall(
      psiMethod()
        .withName("getAnnotation", "getDeclaredAnnotation", "getAnnotationsByType", "getDeclaredAnnotationsByType")
        .with(new MethodDefinedInInterfacePatternCondition("java.lang.reflect.AnnotatedElement"))));

  private static final ElementPattern<PsiElement> BEGINNING_OF_CONSTRUCTOR_ARGUMENTS = beginningOfArguments(CONSTRUCTOR_ARGUMENTS);

  private static final ElementPattern<PsiElement> BEGINNING_OF_ANNOTATION_ARGUMENTS = beginningOfArguments(ANNOTATION_ARGUMENTS);

  private static ElementPattern<PsiElement> beginningOfArguments(ElementPattern<? extends PsiElement> argumentsPattern) {
    return psiElement().afterLeaf("(").withParent(
      or(psiExpression().withParent(argumentsPattern),
         psiElement().withParent(PsiTypeElement.class) // special case for getConstructor(int.class) because 'int' is a keyword
           .withSuperParent(2, PsiClassObjectAccessExpression.class)
           .withSuperParent(3, argumentsPattern)));
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

    if (BEGINNING_OF_ANNOTATION_ARGUMENTS.accepts(position)) {
      PsiClass psiClass = getQualifierClass(position);
      if (psiClass != null) {
        addAnnotationClasses(psiClass, result);
      }
      //TODO handle annotations on fields and methods
    }
    if (BEGINNING_OF_CONSTRUCTOR_ARGUMENTS.accepts(position)) {
      PsiClass psiClass = getQualifierClass(position);
      if (psiClass != null) {
        addConstructorParameterTypes(psiClass, result);
      }
    }
  }

  @Nullable
  private static PsiClass getQualifierClass(@Nullable PsiElement position) {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    return methodCall != null ? getReflectiveClass(methodCall.getMethodExpression().getQualifierExpression()) : null;
  }

  private static void addAnnotationClasses(@NotNull PsiModifierListOwner annotationsOwner, @NotNull CompletionResultSet result) {
    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(annotationsOwner, true, null);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass) {
          PsiClass annotationClass = (PsiClass)resolved;
          String className = annotationClass.getName();
          if (className != null) {
            LookupElement lookupElement = LookupElementBuilder.createWithIcon(annotationClass)
              .withPresentableText(className + ".class")
              .withInsertHandler(JavaReflectionCompletionContributor::handleAnnotationClassInsertion);
            result.addElement(lookupElement);
          }
        }
      }
    }
  }

  private static void addConstructorParameterTypes(@NotNull PsiClass psiClass, @NotNull CompletionResultSet result) {
    PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length != 0) {
      for (PsiMethod constructor : constructors) {
        String parameterTypesText = Arrays.stream(constructor.getParameterList().getParameters())
          .map(p -> p.getType().getCanonicalText())
          .collect(Collectors.joining(",", constructor.getName() + "(", ")"));

        LookupElement lookupElement = LookupElementBuilder.createWithIcon(constructor)
          .withPresentableText(parameterTypesText)
          .withInsertHandler(JavaReflectionCompletionContributor::handleConstructorSignatureInsertion);
        result.addElement(lookupElement);
      }
    }
  }

  private static void handleAnnotationClassInsertion(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiClass) {
      String className = ((PsiClass)object).getName();
      if (className != null) {
        handleParametersInsertion(context, className + ".class");
      }
    }
  }

  private static void handleConstructorSignatureInsertion(@NotNull InsertionContext context, @NotNull LookupElement item) {
    Object object = item.getObject();
    if (object instanceof PsiMethod) {
      handleParametersInsertion(context, getParameterTypesText((PsiMethod)object));
    }
  }

  private static void handleParametersInsertion(@NotNull InsertionContext context, @NotNull String text) {
    PsiElement newElement = PsiUtilCore.getElementAtOffset(context.getFile(), context.getStartOffset());
    PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(newElement, PsiExpressionList.class);
    if (parameterList != null) {
      final TextRange range = parameterList.getTextRange();
      context.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "(" + text + ")");
      context.commitDocument();
      shortenArgumentsClassReferences(context);
    }
  }

  private static class MethodDefinedInInterfacePatternCondition extends PatternCondition<PsiMethod> {
    private final String myInterfaceName;

    public MethodDefinedInInterfacePatternCondition(@NotNull String interfaceName) {
      super("definedInInterface");
      myInterfaceName = interfaceName;
    }

    @Override
    public boolean accepts(@NotNull PsiMethod method, ProcessingContext context) {
      PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, false, myInterfaceName);
    }
  }
}
