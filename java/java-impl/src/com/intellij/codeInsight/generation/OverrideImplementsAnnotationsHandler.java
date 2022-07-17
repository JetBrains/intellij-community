// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * Allows plugging in annotations processing during override/implement.
 * <p/>
 * Parameter annotations would not be copied, if they are not specified in {@link #getAnnotations(PsiFile)}.
 * 
 * {@link #transferToTarget(String, PsiModifierListOwner, PsiModifierListOwner)} can be used, to adjust annotations to the target place e.g.,
 * convert library's Nullable/NotNull annotations to project ones.
 * <p/>
 * @see JavaCodeStyleSettings#getRepeatAnnotations()
 */
public interface OverrideImplementsAnnotationsHandler {
  ExtensionPointName<OverrideImplementsAnnotationsHandler> EP_NAME = ExtensionPointName.create("com.intellij.overrideImplementsAnnotationsHandler");

  /**
   * Returns annotations which should be copied from a source to an implementation (by default, no annotations are copied).
   */
  @Contract(pure = true)
  default String[] getAnnotations(@NotNull PsiFile file) {
    return getAnnotations(file.getProject());
  }

  /**
   * @deprecated Use {@link #getAnnotations(PsiFile)}
   */
  @Deprecated(forRemoval = true)
  @Contract(pure = true)
  String[] getAnnotations(Project project);

  /**
   * @deprecated Use {@link #getAnnotations(PsiFile)}
   */
  @Deprecated(forRemoval = true)
  @Contract(pure = true)
  default String @NotNull [] annotationsToRemove(Project project, @NotNull String fqName) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
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
        if (AnnotationUtil.isAnnotated(source, annotation, flags) && !AnnotationUtil.isAnnotated(target, annotation, flags)) {
          each.transferToTarget(annotation, source, target);
        }
      }
    }

    for (OverrideImplementsAnnotationsHandler each : EP_NAME.getExtensionList()) {
      each.cleanup(source, targetClass, target);
    }
  }

  default void transferToTarget(String annotation, PsiModifierListOwner source, PsiModifierListOwner target) {
    PsiModifierList modifierList = target.getModifierList();
    assert modifierList != null : target;
    PsiAnnotation srcAnnotation = AnnotationUtil.findAnnotation(source, annotation);
    PsiNameValuePair[] valuePairs = srcAnnotation != null ? srcAnnotation.getParameterList().getAttributes() : PsiNameValuePair.EMPTY_ARRAY;
    AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(annotation, valuePairs, modifierList);
  }
}