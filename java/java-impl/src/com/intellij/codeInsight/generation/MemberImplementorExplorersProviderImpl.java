// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation;

import com.intellij.codeInsight.MethodImplementor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MemberImplementorExplorersProviderImpl implements OverrideImplementExploreUtil.MemberImplementorExplorersProvider {
  @NotNull
  @Override
  public List<MethodImplementor> getExplorers() {
    return OverrideImplementUtil.getImplementors();
  }
}
