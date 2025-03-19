// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

public abstract class CharSequenceHashStubIndexExtension<Psi extends PsiElement> extends AbstractStubIndex<CharSequence, Psi> {

  @Override
  public int getVersion() {
    return 1;
  }

  @Override
  public final @NotNull KeyDescriptor<CharSequence> getKeyDescriptor() {
    return CharSequenceHashInlineKeyDescriptor.INSTANCE;
  }

  @OverrideOnly
  public boolean doesKeyMatchPsi(@NotNull CharSequence key, @NotNull Psi psi) {
    return true;
  }
}