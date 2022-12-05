// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugging in annotations processing during override/implement.
 * <p/>
 * Parameter annotations would not be copied, if they are not specified in {@link #getAnnotations(PsiFile)}.
 */
public interface OverrideImplementsAnnotationsFilter {
  ExtensionPointName<OverrideImplementsAnnotationsFilter> EP_NAME =
    ExtensionPointName.create("com.intellij.overrideImplementsAnnotationsFilter");

  /**
   * Returns annotations which should be copied from a source to an implementation (by default, no annotations are copied).
   */
  @Contract(pure = true)
  String[] getAnnotations(@NotNull PsiFile file);

  /**
   * Checks whether a given annotation (identified by fully-qualified name) should be kept when implemented an override in the give file.
   */
  static boolean keepAnnotationOnOverrideMember(@NotNull String fqName, @NotNull PsiFile file) {
    for (OverrideImplementsAnnotationsFilter filter : EP_NAME.getExtensionList()) {
      for (String annotation : filter.getAnnotations(file)) {
        if (fqName.equals(annotation)) {
          return true;
        }
      }
    }

    return false;
  }
}
