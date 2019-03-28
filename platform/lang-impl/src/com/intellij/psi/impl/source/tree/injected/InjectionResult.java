// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.injection.ReferenceInjector;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class InjectionResult {
  final List<? extends PsiFile> files;
  final List<? extends Pair<ReferenceInjector, Place>> references;
  private final long myModificationCount;

  InjectionResult(@NotNull PsiFile hostFile, List<? extends PsiFile> files, List<? extends Pair<ReferenceInjector, Place>> references) {
    this.files = files;
    this.references = references;
    myModificationCount = calcModCount(hostFile);
    if (files == null && references == null) throw new IllegalArgumentException("At least one argument must not be null");
  }

  boolean isValid() {
    if (files != null) {
      for (PsiFile file : files) {
        if (!file.isValid()) return false;
      }
    }
    else {
      for (Pair<ReferenceInjector, Place> pair : references) {
        Place place = pair.getSecond();
        if (!place.isValid()) return false;
      }
    }
    return true;
  }

  boolean isModCountUpToDate(@NotNull PsiFile hostPsiFile) {
    return myModificationCount == calcModCount(hostPsiFile);
  }

  private static long calcModCount(@NotNull PsiFile hostPsiFile) {
    return (hostPsiFile.getModificationStamp() << 32) + hostPsiFile.getManager().getModificationTracker().getModificationCount();
  }
}
