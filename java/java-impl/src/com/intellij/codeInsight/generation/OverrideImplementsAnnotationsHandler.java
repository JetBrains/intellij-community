// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_EXTERNAL;
import static com.intellij.codeInsight.AnnotationUtil.CHECK_TYPE;

/**
 * @author anna
 * @since 19-Aug-2008
 */
public interface OverrideImplementsAnnotationsHandler {
  ExtensionPointName<OverrideImplementsAnnotationsHandler> EP_NAME = ExtensionPointName.create("com.intellij.overrideImplementsAnnotationsHandler");

  /**
   * Returns annotations which should be copied from a source to an implementation (by default, no annotations are copied).
   */
  String[] getAnnotations(Project project);

  @Deprecated
  @NotNull
  @SuppressWarnings("unused")
  default String[] annotationsToRemove(Project project, @NotNull String fqName) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  static void repeatAnnotationsFromSource(PsiModifierListOwner source, @Nullable PsiElement targetClass, PsiModifierListOwner target) {
    Module module = ModuleUtilCore.findModuleForPsiElement(targetClass != null ? targetClass : target);
    GlobalSearchScope moduleScope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : null;
    Project project = target.getProject();
    JavaPsiFacade facade = JavaPsiFacade.getInstance(project);

    for (OverrideImplementsAnnotationsHandler each : Extensions.getExtensions(EP_NAME)) {
      for (String annotation : each.getAnnotations(project)) {
        if (moduleScope != null && facade.findClass(annotation, moduleScope) == null) continue;

        int flags = CHECK_EXTERNAL | CHECK_TYPE;
        if (AnnotationUtil.isAnnotated(source, annotation, flags) && !AnnotationUtil.isAnnotated(target, annotation, flags)) {
          PsiModifierList modifierList = target.getModifierList();
          assert modifierList != null : target;
          AddAnnotationPsiFix.addPhysicalAnnotation(annotation, PsiNameValuePair.EMPTY_ARRAY, modifierList);
        }
      }
    }
  }
}