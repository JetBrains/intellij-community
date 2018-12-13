// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;

/**
 * @author traff
 */
public interface ClassFilter {
  boolean isAccepted(PsiClass aClass);

  ClassFilter INSTANTIABLE = aClass -> PsiUtil.isInstantiatable(aClass);
  ClassFilter ALL = aClass -> true;

  interface ClassFilterWithScope extends ClassFilter {
    GlobalSearchScope getScope();
  }
}