// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiParameter;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;

public interface PsiParameterStub extends NamedStub<PsiParameter> {
  @NotNull
  @Override
  String getName();

  boolean isParameterTypeEllipsis();

  @NotNull
  TypeInfo getType();

  PsiModifierListStub getModList();
}