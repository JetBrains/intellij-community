// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 * A local quick-fix which may not be available on particular elements
 */
public interface ElementAwareLocalQuickFix extends LocalQuickFix {
  /**
   * Returns true if local quick-fix is available on given element
   *
   * @param project current project
   * @param descriptor a problem descriptor associated with the fix
   * @param element an element at which the fix was invoked
   * @return true if fix is available at given element
   */
  boolean isAvailable(Project project, ProblemDescriptor descriptor, PsiElement element);
}
