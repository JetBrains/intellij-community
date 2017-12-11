// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.pom.PomResolveResult;
import com.intellij.pom.PomTarget;
import com.intellij.pom.references.PomService;
import org.jetbrains.annotations.NotNull;

final public class PsiResolveResult implements PomResolveResult {

  private final @NotNull PomTarget myTarget;

  public PsiResolveResult(@NotNull PsiElement element) {
    myTarget = PomService.convertToPom(element);
  }

  @NotNull
  @Override
  public PomTarget getTarget() {
    return myTarget;
  }
}
