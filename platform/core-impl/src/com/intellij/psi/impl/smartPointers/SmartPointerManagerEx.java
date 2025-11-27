// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerBase;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public abstract class SmartPointerManagerEx extends SmartPointerManager {
  public abstract void fastenBelts(@NotNull VirtualFile file);

  public abstract @NotNull <E extends PsiElement> SmartPsiElementPointer<E> createSmartPsiElementPointer(@NotNull E element,
                                                                                                         PsiFile containingFile,
                                                                                                         boolean forInjected);

  public abstract @NotNull SmartPsiFileRange createSmartPsiFileRangePointer(@NotNull PsiFile file,
                                                                            @NotNull TextRange range,
                                                                            boolean forInjected);

  @Nullable
  public abstract SmartPointerTracker getTracker(@NotNull VirtualFile file);

  public abstract void updatePointers(@NotNull Document document,
                                      @NotNull FrozenDocument frozen,
                                      @NotNull List<? extends DocumentEvent> events);

  public abstract void updatePointerTargetsAfterReparse(@NotNull VirtualFile file);

  @NotNull
  public abstract Project getProject();

  @NotNull
  public abstract PsiDocumentManagerEx getPsiDocumentManager();
}
