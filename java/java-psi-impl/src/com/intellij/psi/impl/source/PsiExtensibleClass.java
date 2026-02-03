// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A class which may be extended via {@link com.intellij.psi.augment.PsiAugmentProvider}. In particular synthetic non-physical methods 
 * could be added. This can be used to support particular annotation processors, or even language features like Java records,
 * when some methods exist and should be resolvable but don't directly appear in source code. 
 */
public interface PsiExtensibleClass extends PsiClass {
  /**
   * @return fields explicitly declared in this class, ignoring augmenters.
   */
  @NotNull
  List<PsiField> getOwnFields();

  /**
   * @return methods explicitly declared in this class, ignoring augmenters.
   */
  @NotNull
  List<PsiMethod> getOwnMethods();

  /**
   * @return inner classes explicitly declared in this class, ignoring augmenters.
   */
  @NotNull
  List<PsiClass> getOwnInnerClasses();
}
