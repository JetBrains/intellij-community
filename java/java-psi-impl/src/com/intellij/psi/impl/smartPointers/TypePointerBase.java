// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

public abstract class TypePointerBase<T extends PsiType> implements SmartTypePointer {
  private Reference<T> myTypeRef;

  public TypePointerBase(@NotNull T type) {
    myTypeRef = new SoftReference<>(type);
  }

  @Override
  public T getType() {
    T myType = dereference(myTypeRef);
    if (myType != null && myType.isValid()) return myType;

    myType = calcType();
    myTypeRef = myType == null ? null : new SoftReference<>(myType);
    return myType;
  }

  @Nullable
  protected abstract T calcType();
}
