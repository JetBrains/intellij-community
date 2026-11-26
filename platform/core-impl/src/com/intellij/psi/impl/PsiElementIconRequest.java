// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A helper class to hold enough information to compute a PSI element icon later.
 */
@ApiStatus.Experimental
public final class PsiElementIconRequest {
  private final @NotNull SmartPsiElementPointer<?> myPointer;
  private final @Iconable.IconFlags int myFlags;

  /**
   * Creates a new request.
   * <p>
   * Takes a read lock in a blocking fashion.
   * </p>
   *
   * @param element the PSI element, must be valid
   * @param flags the icon flags
   * @return the new request
   */
  public static @NotNull PsiElementIconRequest create(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    return ReadAction.computeBlocking(() -> {
      return new PsiElementIconRequest(element, element.getProject(), flags);
    });
  }

  private PsiElementIconRequest(@NotNull PsiElement element, @NotNull Project project, @Iconable.IconFlags int flags) {
    myPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element);
    myFlags = flags;
  }

  /**
   * Returns the PSI element, if it still exists.
   * <p>
   *   Should normally be called in a read action because it resolves a PSI smart pointer.
   * </p>
   *
   * @return the PSI element (restored from a smart pointer) or {@code null} if the pointer was invalidated
   */
  @RequiresReadLock(generateAssertion = false)
  public @Nullable PsiElement getElement() {
    return myPointer.dereference();
  }

  /**
   * Returns the icon flags.
   * @return the icon flags, exactly as passed to {@link #create(PsiElement, int)}
   */
  public @Iconable.IconFlags int getFlags() {
    return myFlags;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PsiElementIconRequest)) return false;

    PsiElementIconRequest request = (PsiElementIconRequest)o;

    if (myFlags != request.myFlags) return false;
    if (myPointer != request.myPointer) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = System.identityHashCode(myPointer);
    result = 31 * result + myFlags;
    return result;
  }
}
