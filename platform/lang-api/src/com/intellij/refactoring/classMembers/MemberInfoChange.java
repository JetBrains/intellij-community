// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.classMembers;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

public class MemberInfoChange<T extends PsiElement, U extends MemberInfoBase<T>> {
  private final @Unmodifiable Collection<U> myChangedMembers;

  public MemberInfoChange(@Unmodifiable Collection<U> changedMembers) {
    myChangedMembers = changedMembers;
  }

  public @Unmodifiable Collection<U> getChangedMembers() {
    return myChangedMembers;
  }
}
