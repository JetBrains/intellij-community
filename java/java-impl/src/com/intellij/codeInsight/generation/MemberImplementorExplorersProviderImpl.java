// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.MethodImplementor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MemberImplementorExplorersProviderImpl implements OverrideImplementExploreUtil.MemberImplementorExplorersProvider {
  @Override
  public @NotNull List<MethodImplementor> getExplorers() {
    return OverrideImplementUtil.getImplementors();
  }
}
