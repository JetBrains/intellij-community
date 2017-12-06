// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.psi.PsiClass;

/**
 * @author dyoma
 */
public interface SingleClassConfiguration {
  void setMainClass(final PsiClass psiClass);

  PsiClass getMainClass();
  void setMainClassName(String qualifiedName);
}
