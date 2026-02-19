// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Nullable;

public interface SingleClassConfiguration {
  void setMainClass(final PsiClass psiClass);

  /**
   * Returns the class containing the main method (or null if the class doesn't exist).
   * <p>
   * Keep in mind that for nested classes, the fully qualified name of the main class is different from the binary name. See JLS 13.1.
   */
  PsiClass getMainClass();

  void setMainClassName(@Nullable String qualifiedName);
}
