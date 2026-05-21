// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * Extends {@link OverrideImplementsAnnotationsFilter} with java-specific functionality.
 * 
 * {@link #transferToTarget(String, PsiModifierListOwner, PsiModifierListOwner)} can be used, to adjust annotations to the target place
 * e.g., convert library's Nullable/NotNull annotations to project ones.
 * <p/>
 * @see JavaCodeStyleSettings#getRepeatAnnotations()
 */
public interface OverrideImplementsAnnotationsHandler extends OverrideImplementsAnnotationsFilter {
  ExtensionPointName<OverrideImplementsAnnotationsHandler> EP_NAME = ExtensionPointName.create("com.intellij.overrideImplementsAnnotationsHandler");

  /**
   * Returns annotations which should be copied from a source to an implementation (by default, no annotations are copied).
   */
  @Contract(pure = true)
  @Override
  default String[] getAnnotations(@NotNull PsiFile file) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  /** Perform post processing on the annotations, such as deleting or renaming or otherwise updating annotations in the override */
  default void cleanup(PsiModifierListOwner source, @Nullable PsiElement targetClass, PsiModifierListOwner target) {
  }

  static void repeatAnnotationsFromSource(PsiModifierListOwner source, @Nullable PsiElement targetClass, PsiModifierListOwner target) {
    Module module = ModuleUtilCore.findModuleForPsiElement(targetClass != null ? targetClass : target);
    GlobalSearchScope moduleScope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : null;
    Project project = target.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (OverrideImplementsAnnotationsHandler each : EP_NAME.getExtensionList()) {
      for (String annotation : each.getAnnotations(target.getContainingFile())) {
        if (moduleScope != null && facade.findClass(annotation, moduleScope) == null) continue;

        int flags = CHECK_EXTERNAL | CHECK_TYPE;
        if (AnnotationUtil.isAnnotated(source, annotation, flags) && !hasAnnotation(target, annotation)) {
          each.transferToTarget(annotation, source, target);
        }
      }
    }

    for (OverrideImplementsAnnotationsHandler each : EP_NAME.getExtensionList()) {
      each.cleanup(source, targetClass, target);
    }
  }

  private static boolean hasAnnotation(@NotNull PsiModifierListOwner target, @NotNull String annotation) {
    if (AnnotationUtil.isAnnotated(target, annotation, CHECK_EXTERNAL | CHECK_TYPE)) return true;
    // Handle case when the annotation present in the source modifier list
    // has been 'hidden' after the package qualifier when generating target.
    // For example for:
    // source = @NotNull Map.Entry param
    // target = java.util.@org.jetbrains.annotations.NotNull Map.Entry
    // AnnotationUtil.hasAnnotation(.., ..@NotNull) returns true for source but false for the target
    PsiTypeElement typeElement = getTypeElement(target);
    if (typeElement == null) return false;
    return hasAnnotationOnFirstNonPackageSubElement(typeElement, annotation);
  }

  private static @Nullable PsiTypeElement getTypeElement(@NotNull PsiModifierListOwner target) {
    if (target instanceof PsiMethod) return ((PsiMethod)target).getReturnTypeElement();
    if (target instanceof PsiVariable) return ((PsiVariable)target).getTypeElement();
    return null;
  }

  /**
   * Checks if a specified annotation is present at the innermost component reference element of the given type element
   * after skipping the initial package qualifier.
   *
   * <p>Examples:
   * <pre>{@code
   * Returns true for annotation "org.jetbrains.annotations.NotNull"
   * when typeElement represents: java.lang.@NotNull String
   *
   * Returns true for annotation "org.jetbrains.annotations.NotNull"
   * when typeElement represents: @NotNull String
   *
   * Returns false for annotation "org.jetbrains.annotations.NotNull"
   * when typeElement represents: @NotNull java.lang.String
   *
   * Returns true for annotation "org.jetbrains.annotations.Nullable"
   * when typeElement represents: java.util.@Nullable List<String>
   * }</pre>
   *
   * @param typeElement The {@link PsiTypeElement} to inspect. This represents a type in Java source code,
   *                    such as a method return type or a parameter type.
   * @param annotation  The fully qualified name of the annotation to search for.
   * @return true if the annotation is found after the package qualifier in the type reference;
   * false otherwise, or if the type element has no innermost component reference.
   */
  private static boolean hasAnnotationOnFirstNonPackageSubElement(PsiTypeElement typeElement, @NotNull String annotation) {
    PsiJavaCodeReferenceElement reference = typeElement.getInnermostComponentReferenceElement();
    if (reference == null) return false;
    var leftMostResolvedQualifier = getLeftmostNonPackageElement(reference);
    if (leftMostResolvedQualifier == null) return false;
    return hasDirectAnnotation(leftMostResolvedQualifier, annotation);
  }

  private static @Nullable PsiElement getLeftmostNonPackageElement(@NotNull PsiJavaCodeReferenceElement reference) {
    PsiJavaCodeReferenceElement qualifier =
      reference.getQualifier() instanceof PsiJavaCodeReferenceElement referenceElement ? referenceElement : null;
    if (qualifier != null) {
      PsiElement result = getLeftmostNonPackageElement(qualifier);
      if (result != null) return result;
    }
    PsiElement resolved = reference.resolve();
    return resolved == null || resolved instanceof PsiPackage ? null : reference;
  }

  private static boolean hasDirectAnnotation(@NotNull PsiElement element, @NotNull String annotation) {
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (child instanceof PsiAnnotation psiAnnotation && psiAnnotation.hasQualifiedName(annotation)) {
        return true;
      }
    }
    return false;
  }

  default void transferToTarget(String annotation, PsiModifierListOwner source, PsiModifierListOwner target) {
    PsiModifierList modifierList = target.getModifierList();
    assert modifierList != null : target;
    PsiAnnotation srcAnnotation = AnnotationUtil.findAnnotation(source, annotation);
    PsiNameValuePair[] valuePairs = srcAnnotation != null ? srcAnnotation.getParameterList().getAttributes() : PsiNameValuePair.EMPTY_ARRAY;
    AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(annotation, valuePairs, modifierList);
  }
}
