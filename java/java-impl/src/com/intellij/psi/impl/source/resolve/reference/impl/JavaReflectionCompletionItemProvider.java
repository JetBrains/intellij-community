// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaClassReferenceCompletionContributor;
import com.intellij.java.completion.modcommand.JavaModCompletionUtils;
import com.intellij.java.completion.modcommand.NonImportedClassProvider;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItemPresentation;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.MarkupText;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReference;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Set;
import java.util.function.BiConsumer;

import static com.intellij.codeInsight.completion.JavaCompletionContributor.isInJavaContext;
import static com.intellij.patterns.PsiJavaPatterns.or;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.PsiJavaPatterns.psiExpression;
import static com.intellij.patterns.PsiJavaPatterns.psiMethod;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.ReflectiveClass;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.getParameterTypesText;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.getReflectiveClass;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.isPublic;
import static com.intellij.psi.impl.source.resolve.reference.impl.JavaReflectionReferenceUtil.shortenArgumentsClassReferences;

@NotNullByDefault
public final class JavaReflectionCompletionItemProvider implements DumbAware, ModCompletionItemProvider {
  private static final String CONSTRUCTOR = "getConstructor";
  private static final String DECLARED_CONSTRUCTOR = "getDeclaredConstructor";
  private static final String ANNOTATION = "getAnnotation";
  private static final String DECLARED_ANNOTATION = "getDeclaredAnnotation";
  private static final String ANNOTATIONS_BY_TYPE = "getAnnotationsByType";
  private static final String DECLARED_ANNOTATIONS_BY_TYPE = "getDeclaredAnnotationsByType";
  private static final String ANNOTATED_ELEMENT = "java.lang.reflect.AnnotatedElement";

  private static final Set<String> DECLARED_NAMES =
    Set.of(DECLARED_CONSTRUCTOR, DECLARED_ANNOTATION, DECLARED_ANNOTATIONS_BY_TYPE);
  private static final ElementPattern<? extends PsiElement> CONSTRUCTOR_ARGUMENTS = psiElement(PsiExpressionList.class)
    .withParent(psiExpression().methodCall(
      psiMethod()
        .withName(CONSTRUCTOR, DECLARED_CONSTRUCTOR)
        .definedInClass(CommonClassNames.JAVA_LANG_CLASS)));

  private static final ElementPattern<? extends PsiElement> ANNOTATION_ARGUMENTS = psiElement(PsiExpressionList.class)
    .withParent(psiExpression().methodCall(
      psiMethod()
        .withName(ANNOTATION, DECLARED_ANNOTATION, ANNOTATIONS_BY_TYPE, DECLARED_ANNOTATIONS_BY_TYPE)
        .with(new MethodDefinedInInterfacePatternCondition(ANNOTATED_ELEMENT))));

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
  public void provideItems(CompletionContext parameters, ModCompletionResult sink) {
    if (parameters.isSmart()) return;

    final PsiElement position = parameters.getPosition();
    if (!isInJavaContext(position)) return;

    if (BEGINNING_OF_ANNOTATION_ARGUMENTS.accepts(position)) {
      addVariants(position, (psiClass, isDeclared) -> addAnnotationClasses(psiClass, isDeclared, sink));
      //TODO handle annotations on fields and methods
    }
    else if (BEGINNING_OF_CONSTRUCTOR_ARGUMENTS.accepts(position)) {
      addVariants(position, (psiClass, isDeclared) -> addConstructorParameterTypes(psiClass, isDeclared, sink));
    }
    else if (JavaReflectionReferenceContributor.Holder.CLASS_PATTERN.accepts(position.getParent())) {
      JavaClassReference ref = JavaClassReferenceCompletionContributor.findJavaClassReference(position.getContainingFile(), parameters.getOffset());
      if (ref != null && ref.getCompletionContext() instanceof PsiPackage psiPackage && psiPackage.getName() == null) {
        NonImportedClassProvider.addAllClasses(parameters, parameters.getInvocationCount() <= 1, parameters.matcher(), sink);
      }
    }
  }

  private static void addVariants(PsiElement position, BiConsumer<? super PsiClass, ? super Boolean> variantAdder) {
    PsiMethodCallExpression methodCall = PsiTreeUtil.getParentOfType(position, PsiMethodCallExpression.class);
    if (methodCall != null) {
      ReflectiveClass ownerClass = getReflectiveClass(methodCall.getMethodExpression().getQualifierExpression());
      if (ownerClass != null) {
        String methodName = methodCall.getMethodExpression().getReferenceName();
        if (methodName != null) {
          variantAdder.accept(ownerClass.getPsiClass(), DECLARED_NAMES.contains(methodName));
        }
      }
    }
  }

  private static void addAnnotationClasses(PsiClass psiClass, boolean isDeclared, ModCompletionResult sink) {
    Set<PsiAnnotation> declaredAnnotations =
      isDeclared ? Set.of(AnnotationUtil.getAllAnnotations(psiClass, false, null, false)) : null;

    PsiAnnotation[] annotations = AnnotationUtil.getAllAnnotations(psiClass, true, null, false);
    for (PsiAnnotation annotation : annotations) {
      PsiJavaCodeReferenceElement referenceElement = annotation.getNameReferenceElement();
      if (referenceElement != null) {
        PsiElement resolved = referenceElement.resolve();
        if (resolved instanceof PsiClass annotationClass) {
          String className = annotationClass.getName();
          String qualifiedName = annotationClass.getQualifiedName();
          if (className != null && qualifiedName != null) {
            CommonCompletionItem item = new CommonCompletionItem(className)
              .withObject(annotationClass)
              .withPresentation(new ModCompletionItemPresentation(MarkupText.plainText(className + ".class"))
                                  .withMainIcon(() -> annotationClass.getIcon(0)))
              .withAdditionalUpdater((_, updater) -> {
                handleParametersInsertion(updater, qualifiedName + ".class");
              })
              .withPriority(isDeclared && !declaredAnnotations.contains(annotation) ? -1 : 0);
            sink.accept(item);
          }
        }
      }
    }
  }

  private static void addConstructorParameterTypes(PsiClass psiClass, boolean isDeclared, ModCompletionResult result) {
    PsiMethod[] constructors = psiClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      String parametersText = getParameterTypesText(constructor);
      CommonCompletionItem item = JavaModCompletionUtils.forMethod(constructor, PsiSubstitutor.EMPTY)
        .withPriority(isDeclared && !isPublic(constructor) ? -1 : 0)
        .withAdditionalUpdater((_, updater) -> {
          if (parametersText != null) {
            handleParametersInsertion(updater, parametersText);
          }
        });
      result.accept(item);
    }
  }

  private static void handleParametersInsertion(ModPsiUpdater updater, String text) {
    PsiElement newElement = PsiUtilCore.getElementAtOffset(updater.getPsiFile(), updater.getCaretOffset());
    PsiExpressionList parameterList = PsiTreeUtil.getParentOfType(newElement, PsiExpressionList.class);
    if (parameterList != null) {
      TextRange range = parameterList.getTextRange();
      Document document = updater.getDocument();
      document.replaceString(range.getStartOffset(), range.getEndOffset(), "(" + text + ")");
      PsiDocumentManager.getInstance(updater.getProject()).commitDocument(document);
      shortenArgumentsClassReferences(updater);
    }
  }

  private static class MethodDefinedInInterfacePatternCondition extends PatternCondition<PsiMethod> {
    private final String myInterfaceName;

    MethodDefinedInInterfacePatternCondition(String interfaceName) {
      super("definedInInterface");
      myInterfaceName = interfaceName;
    }

    @Override
    public boolean accepts(PsiMethod method, ProcessingContext context) {
      PsiClass containingClass = method.getContainingClass();
      return InheritanceUtil.isInheritor(containingClass, false, myInterfaceName);
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }
}
