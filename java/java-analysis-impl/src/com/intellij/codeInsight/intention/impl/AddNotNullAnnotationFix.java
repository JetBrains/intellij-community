// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @deprecated use {@link com.intellij.codeInsight.intention.AddAnnotationModCommandAction#createAddNotNullFix(PsiModifierListOwner)}
 */
@Deprecated(forRemoval = true)
public class AddNotNullAnnotationFix extends AddNullableNotNullAnnotationFix {
  public AddNotNullAnnotationFix(@NotNull PsiModifierListOwner owner) {
    super(NullableNotNullManager.getInstance(owner.getProject()).getDefaultNotNull(),
          owner,
          getNullables(owner));
  }

  private static String @NotNull [] getNullables(@NotNull PsiModifierListOwner owner) {
    final List<String> nullables = NullableNotNullManager.getInstance(owner.getProject()).getNullables();
    return ArrayUtilRt.toStringArray(nullables);
  }
}
