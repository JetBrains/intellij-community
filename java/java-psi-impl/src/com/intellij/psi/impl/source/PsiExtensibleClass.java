// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PsiExtensibleClass extends PsiClass {
  @NotNull
  List<PsiField> getOwnFields();

  @NotNull
  List<PsiMethod> getOwnMethods();

  @NotNull
  List<PsiClass> getOwnInnerClasses();
}
