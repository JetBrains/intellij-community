// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageVisitorBase;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.intellij.codeInspection.deprecation.DeprecationInspectionBase.*;

/**
 * PSI visitor that detects usages of deprecated APIs, which are deprecated
 * with {@code @Deprecated, @ScheduledForRemoval} annotations or {@code @deprecated} Javadoc tag.
 */
public class DeprecationElementVisitor extends ApiUsageVisitorBase {
  private final ProblemsHolder myHolder;
  private final boolean myIgnoreInsideDeprecated;
  private final boolean myIgnoreAbstractDeprecatedOverrides;
  private final boolean myIgnoreImportStatements;
  private final boolean myIgnoreMethodsOfDeprecated;
  private final boolean myIgnoreInSameOutermostClass;
  private final boolean myForRemoval;
  private final ProblemHighlightType myHighlightType;

  DeprecationElementVisitor(@NotNull ProblemsHolder holder,
                            boolean ignoreInsideDeprecated,
                            boolean ignoreAbstractDeprecatedOverrides,
                            boolean ignoreImportStatements,
                            boolean ignoreMethodsOfDeprecated,
                            boolean ignoreInSameOutermostClass,
                            boolean forRemoval,
                            @Nullable HighlightSeverity severity) {
    myHolder = holder;
    myIgnoreInsideDeprecated = ignoreInsideDeprecated;
    myIgnoreAbstractDeprecatedOverrides = ignoreAbstractDeprecatedOverrides;
    myIgnoreImportStatements = ignoreImportStatements;
    myIgnoreMethodsOfDeprecated = ignoreMethodsOfDeprecated;
    myIgnoreInSameOutermostClass = ignoreInSameOutermostClass;
    myForRemoval = forRemoval;
    myHighlightType = forRemoval && severity == HighlightSeverity.ERROR
                      ? ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL
                      : ProblemHighlightType.LIKE_DEPRECATED;
  }

  @Override
  public void processReference(@NotNull PsiReference reference) {
    if (reference instanceof ResolvingHint && !((ResolvingHint)reference).canResolveTo(PsiModifierListOwner.class)) {
      return;
    }
    PsiElement resolved = reference.resolve();
    if (resolved != null) {
      PsiElement elementToHighlight = getElementToHighlight(reference);
      checkDeprecated(resolved, elementToHighlight, null, myIgnoreInsideDeprecated, myIgnoreImportStatements,
                      myIgnoreMethodsOfDeprecated, myIgnoreInSameOutermostClass, myHolder, myForRemoval, myHighlightType);
    }
  }

  @NotNull
  private static PsiElement getElementToHighlight(@NotNull PsiReference reference) {
    if (reference instanceof PsiJavaCodeReferenceElement) {
      PsiElement referenceNameElement = ((PsiJavaCodeReferenceElement)reference).getReferenceNameElement();
      if (referenceNameElement != null) {
        return referenceNameElement;
      }
    }
    return reference.getElement();
  }

  @Override
  public void processConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass, @NotNull PsiMethod constructor) {
    checkDeprecated(constructor, instantiatedClass, null, myIgnoreInsideDeprecated, myIgnoreImportStatements,
                    true, myIgnoreInSameOutermostClass, myHolder, myForRemoval, myHighlightType);
  }

  @Override
  public void processDefaultConstructorInvocation(@NotNull PsiJavaCodeReferenceElement instantiatedClass) {
    PsiElement createdClass = instantiatedClass.resolve();
    if (createdClass instanceof PsiClass && hasEmptyDeprecatedConstructor((PsiClass) createdClass, myForRemoval)) {
      registerDefaultConstructorProblem((PsiClass) createdClass, instantiatedClass);
    }
  }

  @Override
  public void processMethodOverriding(@NotNull PsiMethod method, @NotNull PsiMethod overriddenMethod) {
    PsiClass aClass = overriddenMethod.getContainingClass();
    if (aClass == null) return;

    PsiIdentifier nameIdentifier = method.getNameIdentifier();
    if (nameIdentifier == null) return;

    //Do not show deprecated warning for class implementing deprecated methods
    if (myIgnoreAbstractDeprecatedOverrides && !aClass.isDeprecated() && overriddenMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return;
    }

    if (overriddenMethod.isDeprecated() && myForRemoval == isForRemovalAttributeSet(overriddenMethod)) {
      String description = JavaErrorMessages.message(myForRemoval ? "overrides.marked.for.removal.method" : "overrides.deprecated.method",
                                                     getPresentableName(aClass));
      myHolder.registerProblem(nameIdentifier, getDescription(description, myForRemoval, myHighlightType), myHighlightType);
    }
  }

  @Override
  public void processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassConstructor(@NotNull PsiClass superClass,
                                                                                         @NotNull PsiMethod subclassConstructor) {
    if (hasEmptyDeprecatedConstructor(superClass, myForRemoval)) {
      PsiIdentifier nameIdentifier = subclassConstructor.getNameIdentifier();
      if (nameIdentifier != null) {
        registerDefaultConstructorProblem(superClass, nameIdentifier);
      }
    }
  }

  @Override
  public void processEmptyConstructorOfSuperClassImplicitInvocationAtSubclassDeclaration(@NotNull PsiClass subclass,
                                                                                         @NotNull PsiClass superClass) {
    if (hasEmptyDeprecatedConstructor(superClass, myForRemoval)) {
      final boolean isAnonymous = subclass instanceof PsiAnonymousClass;
      if (isAnonymous) {
        final PsiExpressionList argumentList = ((PsiAnonymousClass)subclass).getArgumentList();
        if (argumentList != null && !argumentList.isEmpty()) return;
      }
      PsiElement elementToHighlight = isAnonymous ? ((PsiAnonymousClass) subclass).getBaseClassReference() : subclass.getNameIdentifier();
      if (elementToHighlight != null) {
        registerDefaultConstructorProblem(superClass, elementToHighlight);
      }
    }
  }

  private static boolean hasEmptyDeprecatedConstructor(@NotNull PsiClass aClass, boolean forRemoval) {
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      /*
        The default constructor of a class can be externally annotated (IDEA-200832).
        There cannot be inferred annotations for a default constructor,
        so here is no need to check all annotations returned
        by `AnnotationUtil.findAnnotations()`, but only external ones.
       */
      List<PsiAnnotation> externalDeprecated = ExternalAnnotationsManager
        .getInstance(aClass.getProject())
        .findDefaultConstructorExternalAnnotations(aClass, CommonClassNames.JAVA_LANG_DEPRECATED);

      return externalDeprecated != null
             && !externalDeprecated.isEmpty()
             && ContainerUtil.exists(externalDeprecated, annotation -> isForRemovalAttributeSet(annotation) == forRemoval);
    }
    return Arrays.stream(constructors)
      .anyMatch(constructor -> constructor.getParameterList().isEmpty() &&
                               constructor.isDeprecated() &&
                               forRemoval == isForRemovalAttributeSet(constructor));
  }

  private void registerDefaultConstructorProblem(@NotNull PsiClass constructorOwner,
                                                 @NotNull PsiElement elementToHighlight) {
    String description =
      JavaErrorMessages.message(myForRemoval ? "marked.for.removal.default.constructor" : "deprecated.default.constructor",
                                constructorOwner.getQualifiedName());
    myHolder.registerProblem(elementToHighlight, getDescription(description, myForRemoval, myHighlightType), myHighlightType);
  }
}