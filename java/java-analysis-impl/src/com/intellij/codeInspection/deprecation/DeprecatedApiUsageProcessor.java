// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation;

import com.intellij.codeInsight.ExternalAnnotationsManager;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;

import java.util.List;

import static com.intellij.codeInspection.deprecation.DeprecationInspectionBase.*;

/**
 * PSI visitor that detects usages of deprecated APIs, which are deprecated
 * with {@code @Deprecated, @ScheduledForRemoval} annotations or {@code @deprecated} Javadoc tag.
 */
public final class DeprecatedApiUsageProcessor implements ApiUsageProcessor {
  private final ProblemsHolder myHolder;
  private final boolean myIgnoreInsideDeprecated;
  private final boolean myIgnoreAbstractDeprecatedOverrides;
  private final boolean myIgnoreImportStatements;
  private final boolean myIgnoreMethodsOfDeprecated;
  private final boolean myIgnoreInSameOutermostClass;
  private final boolean myForRemoval;
  private final boolean myIgnoreApiDeclaredInThisProject;
  private final ProblemHighlightType myHighlightType;

  public DeprecatedApiUsageProcessor(@NotNull ProblemsHolder holder,
                                     boolean ignoreInsideDeprecated,
                                     boolean ignoreAbstractDeprecatedOverrides,
                                     boolean ignoreImportStatements,
                                     boolean ignoreMethodsOfDeprecated,
                                     boolean ignoreInSameOutermostClass,
                                     boolean forRemoval,
                                     boolean ignoreApiDeclaredInThisProject, @Nullable HighlightSeverity severity) {
    myHolder = holder;
    myIgnoreInsideDeprecated = ignoreInsideDeprecated;
    myIgnoreAbstractDeprecatedOverrides = ignoreAbstractDeprecatedOverrides;
    myIgnoreImportStatements = ignoreImportStatements;
    myIgnoreMethodsOfDeprecated = ignoreMethodsOfDeprecated;
    myIgnoreInSameOutermostClass = ignoreInSameOutermostClass;
    myForRemoval = forRemoval;
    myIgnoreApiDeclaredInThisProject = ignoreApiDeclaredInThisProject;
    myHighlightType = forRemoval && severity == HighlightSeverity.ERROR
                      ? ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL
                      : ProblemHighlightType.LIKE_DEPRECATED;
  }

  @Override
  public void processReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target, @Nullable UExpression qualifier) {
    if (sourceNode instanceof ULambdaExpression) return;
    checkTargetDeprecated(sourceNode, target);
  }

  @Override
  public void processImportReference(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
    checkTargetDeprecated(sourceNode, target);
  }

  private void checkTargetDeprecated(@NotNull UElement sourceNode, @NotNull PsiModifierListOwner target) {
    PsiElement elementToHighlight = sourceNode.getSourcePsi();
    if (elementToHighlight != null) {
      checkTargetDeprecated(elementToHighlight, target);
    }
  }

  private void checkTargetDeprecated(@NotNull PsiElement elementToHighlight, @NotNull PsiModifierListOwner target) {
    checkDeprecated(target, elementToHighlight, null, myIgnoreInsideDeprecated, myIgnoreImportStatements,
                    myIgnoreMethodsOfDeprecated, myIgnoreInSameOutermostClass, myHolder, myForRemoval, myIgnoreApiDeclaredInThisProject, myHighlightType);
  }

  @Override
  public void processConstructorInvocation(@NotNull UElement sourceNode,
                                           @NotNull PsiClass instantiatedClass,
                                           @Nullable PsiMethod constructor,
                                           @Nullable UClass subclassDeclaration) {
    if (constructor != null) {
      if (PsiImplUtil.isDeprecated(constructor) && myForRemoval == isForRemovalAttributeSet(constructor)) {
        checkTargetDeprecated(sourceNode, constructor);
        return;
      }
    }

    if (isDefaultConstructorDeprecated(instantiatedClass)) {
      PsiElement elementToHighlight = sourceNode.getSourcePsi();
      if (elementToHighlight == null) {
        return;
      }
      String description = JavaErrorBundle.message(myForRemoval
                                                     ? "marked.for.removal.default.constructor"
                                                     : "deprecated.default.constructor",
                                                   instantiatedClass.getQualifiedName());
      myHolder.registerProblem(elementToHighlight, getDescription(description, myForRemoval, myHighlightType), myHighlightType);
    }
  }

  @Override
  public void processMethodOverriding(@NotNull UMethod method, @NotNull PsiMethod overriddenMethod) {
    PsiClass aClass = overriddenMethod.getContainingClass();
    if (aClass == null) return;

    PsiElement methodNameElement = UElementKt.getSourcePsiElement(method.getUastAnchor());
    if (methodNameElement == null) return;

    //Do not show deprecated warning for class implementing deprecated methods
    if (myIgnoreAbstractDeprecatedOverrides && !aClass.isDeprecated() && overriddenMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return;
    }

    if (myIgnoreApiDeclaredInThisProject && overriddenMethod.getManager().isInProject(overriddenMethod)) return;
    
    if (overriddenMethod.isDeprecated() && myForRemoval == isForRemovalAttributeSet(overriddenMethod)) {
      String description = JavaErrorBundle.message(myForRemoval ? "overrides.marked.for.removal.method" : "overrides.deprecated.method",
                                                   getPresentableName(aClass));
      myHolder.registerProblem(methodNameElement, getDescription(description, myForRemoval, myHighlightType), myHighlightType);
    }
  }

  @Override
  public void processJavaModuleReference(@NotNull PsiJavaModuleReference javaModuleReference, @NotNull PsiJavaModule target) {
    checkTargetDeprecated(javaModuleReference.getElement(), target);
  }

  /**
   * The default constructor of a class can be externally annotated (IDEA-200832).
   */
  private boolean isDefaultConstructorDeprecated(@NotNull PsiClass aClass) {
    List<PsiAnnotation> externalDeprecated = ExternalAnnotationsManager
      .getInstance(aClass.getProject())
      .findDefaultConstructorExternalAnnotations(aClass, CommonClassNames.JAVA_LANG_DEPRECATED);

    return externalDeprecated != null &&
           ContainerUtil.exists(externalDeprecated, annotation -> isForRemovalAttributeSet(annotation) == myForRemoval);
  }
}