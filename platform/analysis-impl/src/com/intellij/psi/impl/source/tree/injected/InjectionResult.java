// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.injection.ReferenceInjector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class InjectionResult implements Supplier<InjectionResult> {
  private final PsiFile myHostFile;
  final @Nullable List<? extends PsiFile> files;
  final @Nullable List<? extends Pair<ReferenceInjector, Place>> references;
  private final long myModificationCount;

  InjectionResult(@NotNull PsiFile hostFile,
                  @Nullable List<? extends PsiFile> files,
                  @Nullable List<? extends Pair<ReferenceInjector, Place>> references) {
    myHostFile = hostFile;
    this.files = files;
    this.references = references;
    myModificationCount = calcModCount();
  }

  @Override
  public InjectionResult get() {
    return this;
  }

  boolean isEmpty() {
    return files == null && references == null;
  }

  @ApiStatus.Internal
  public boolean isValid() {
    if (files != null) {
      for (PsiFile file : files) {
        if (!file.isValid()) return false;
      }
    }
    else if (references != null) {
      for (Pair<ReferenceInjector, Place> pair : references) {
        Place place = pair.getSecond();
        if (!place.isValid()) return false;
      }
    }
    return true;
  }

  boolean isModCountUpToDate() {
    return myModificationCount == calcModCount();
  }

  private long calcModCount() {
    return (myHostFile.getModificationStamp() << 32) + myHostFile.getManager().getModificationTracker().getModificationCount();
  }
}
