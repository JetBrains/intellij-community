// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.meta;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 *
 * @see MetaDataRegistrar#registerMetaData(com.intellij.psi.filters.ElementFilter, Class)
 * @see PsiMetaOwner#getMetaData()
 */
public interface PsiMetaData {
  PsiElement getDeclaration();

  @NonNls
  String getName(PsiElement context);

  @NonNls
  String getName();

  void init(PsiElement element);

  /**
   * @return objects this meta data depends on.
   * @see com.intellij.psi.util.CachedValue
   */
  @NotNull
  default Object[] getDependencies() {
    //noinspection deprecation
    return getDependences();
  }

  /** @deprecated use {@link PsiMetaData#getDependencies()} */
  @Deprecated
  @NotNull
  default Object[] getDependences() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }
}
