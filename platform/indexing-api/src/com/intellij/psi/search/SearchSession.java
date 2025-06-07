// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class SearchSession extends UserDataHolderBase {
  private final PsiElement[] myElements;

  public SearchSession() {
    this(PsiElement.EMPTY_ARRAY);
  }
  public SearchSession(PsiElement @NotNull ... elements) {
    myElements = elements;
  }

  public @NotNull @Unmodifiable List<VirtualFile> getTargetVirtualFiles() {
    return ContainerUtil.mapNotNull(myElements, e -> PsiUtilCore.getVirtualFile(e));
  }
}
