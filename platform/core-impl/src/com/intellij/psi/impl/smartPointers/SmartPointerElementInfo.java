// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SmartPointerElementInfo {
  @Nullable
  Document getDocumentToSynchronize() {
    return null;
  }

  void fastenBelt(@NotNull SmartPointerManagerImpl manager) {
  }

  @Nullable
  abstract PsiElement restoreElement(@NotNull SmartPointerManagerImpl manager);

  @Nullable
  abstract PsiFile restoreFile(@NotNull SmartPointerManagerImpl manager);

  abstract int elementHashCode(); // must be immutable
  abstract boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerImpl manager);

  abstract VirtualFile getVirtualFile();

  @Nullable
  abstract Segment getRange(@NotNull SmartPointerManagerImpl manager);

  @Nullable
  abstract Segment getPsiRange(@NotNull SmartPointerManagerImpl manager);

  void cleanup() {
  }
}
