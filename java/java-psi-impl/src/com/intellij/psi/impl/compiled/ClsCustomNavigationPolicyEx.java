// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** @deprecated use {@link ClsCustomNavigationPolicy} directly */
@SuppressWarnings("DeprecatedIsStillUsed")
@ApiStatus.ScheduledForRemoval(inVersion = "2019")
public abstract class ClsCustomNavigationPolicyEx implements ClsCustomNavigationPolicy {
  public PsiFile getFileNavigationElement(@NotNull ClsFileImpl file) {
    return (PsiFile)getNavigationElement(file);
  }
}