// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.FrozenDocument;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.PsiDocumentManagerEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * All implementers of {@link SmartPointerManager} should extend this class.
 * One should not downcast {@link SmartPointerManager} to  {@link SmartPointerManagerImpl}.
 * Even though these methods are solely used by smart pointer implementations,
 * this abstract class is necessary for Analyzer to be able to redirect them to the right SmartPointerManagerImpl.
 * The lifetimes of smart pointers and the actual SmartPointerManagerImpl may differ.
 */
@ApiStatus.Internal
public abstract class SmartPointerManagerEx extends SmartPointerManager implements Disposable {
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
