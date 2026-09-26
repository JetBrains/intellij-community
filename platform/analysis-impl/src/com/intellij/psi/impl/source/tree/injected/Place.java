// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.AbstractList;
import java.util.List;

public class Place extends AbstractList<PsiLanguageInjectionHost.Shred> {
  private final @NotNull List<? extends PsiLanguageInjectionHost.Shred> myList;

  Place(@Unmodifiable @NotNull List<? extends PsiLanguageInjectionHost.Shred> list) {
    myList = list;
  }

  @NotNull
  SmartPsiElementPointer<PsiLanguageInjectionHost> getHostPointer() {
    return ((ShredImpl)get(0)).getSmartPointer();
  }

  boolean isValid() {
    for (PsiLanguageInjectionHost.Shred shred : this) {
      if (!shred.isValid()) {
        return false;
      }
    }
    return true;
  }

  void dispose() {
    for (PsiLanguageInjectionHost.Shred shred : this) {
      shred.dispose();
    }
  }

  @Override
  public PsiLanguageInjectionHost.Shred get(int index) {
    return myList.get(index);
  }

  @Override
  public int size() {
    return myList.size();
  }
}
