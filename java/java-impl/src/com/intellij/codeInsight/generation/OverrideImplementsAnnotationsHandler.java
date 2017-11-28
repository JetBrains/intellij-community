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

        if (AnnotationUtil.isAnnotated(source, annotation, false, false) &&
            !AnnotationUtil.isAnnotated(target, annotation, false, false)) {
          PsiAnnotation psiAnnotation = AnnotationUtil.findAnnotation(source, annotation);
          if (psiAnnotation != null && AnnotationUtil.isInferredAnnotation(psiAnnotation)) {
            continue;
          }

          PsiModifierList modifierList = target.getModifierList();
          assert modifierList != null : target;
          AddAnnotationPsiFix.addPhysicalAnnotation(annotation, PsiNameValuePair.EMPTY_ARRAY, modifierList);
        }
      }
    }
  }
}