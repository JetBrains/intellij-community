// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract base that stores metadata for a {@link com.intellij.psi.SmartPsiElementPointer}.
 * Each inheritor captures a different restoration strategy depending on the kind of PSI element
 * (file, directory, regular element, compiled element, injected element, or a hard reference).
 */
@ApiStatus.Internal
public abstract class SmartPointerElementInfo {
  @Nullable
  Document getDocumentToSynchronize() {
    return null;
  }

  void fastenBelt(@NotNull SmartPointerManagerEx manager) {
  }

  abstract @Nullable PsiElement restoreElement(@NotNull SmartPointerManagerEx manager);

  abstract @Nullable PsiFile restoreFile(@NotNull SmartPointerManagerEx manager);

  abstract int elementHashCode(); // must be immutable

  abstract boolean pointsToTheSameElementAs(@NotNull SmartPointerElementInfo other, @NotNull SmartPointerManagerEx manager);

  abstract VirtualFile getVirtualFile();

  abstract @Nullable Segment getRange(@NotNull SmartPointerManagerEx manager);

  abstract @Nullable Segment getPsiRange(@NotNull SmartPointerManagerEx manager);

  void cleanup() {
  }
}
