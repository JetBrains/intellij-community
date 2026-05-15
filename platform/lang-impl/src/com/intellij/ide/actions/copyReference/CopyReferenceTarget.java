// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference;

import com.intellij.ide.PasteProvider;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.model.Pointer;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a minimal entity participating in copy reference actions. Provision of a {@link CopyReferenceTarget} allows it to be copied to
 * the clipboard. By default, only support for pasting the {@link #getReference()} is provided. For more complex paste actions (e.g., for
 * removing unnecessary qualified prefixes), a custom {@link PasteProvider} is required to be used.
 *
 * @see CopyReferenceAction
 * @see CopyReferenceSymbol
 * @see CopyReferenceTargetProvider
 * @see SymbolCopyReferenceTargetProvider
 */
@Experimental
public interface CopyReferenceTarget {
  /**
   * @return a pointer used to restore the instance in subsequent read actions.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Pointer<? extends CopyReferenceTarget> createPointer();

  /**
   * Two different symbols may have the same copy reference target. Duplicate targets will be removed from any displayed popups.
   * <p>
   * Two targets are equal when they refer to the same entity. Equal targets must have the same {@link #getReference()}.
   */
  @Override
  boolean equals(final @Nullable Object obj);

  /**
   * This method is called to retrieve a stringified version of this reference. The stringified version will be used for simple paste
   * actions from the clipboard.
   *
   * @return a stringified version of this reference.
   * @implNote This method is expected to be fast, as it may be invoked during the update of the action.
   */
  @NotNull String getReference();

  /**
   * Must be consistent with {@link #equals(Object)}.
   */
  @Override
  int hashCode();

  /**
   * This method is called when the platform decides to display the target in the UI. For example, to disambiguate multiple targets using a
   * popup, and to update the status bar.
   *
   * @return the presentation to render this target in the copy reference popup and status bar.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull TargetPresentation presentation();
}
