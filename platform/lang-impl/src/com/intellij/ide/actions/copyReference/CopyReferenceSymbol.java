// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference;

import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

/**
 * To provide a {@link CopyReferenceTarget} for a {@link Symbol}, either:
 * <ul>
 *   <li>implement this interface and {@link #getCopyReferenceTarget(Project)} inside the {@link Symbol}</li>
 *   <li>implement {@link SymbolCopyReferenceTargetProvider}</li>
 * </ul>
 * A {@link SymbolCopyReferenceTargetProvider} that returns a target wins over {@link #getCopyReferenceTarget(Project)}.
 *
 * @see CopyReferenceTarget
 * @see SymbolCopyReferenceTargetProvider
 */
@Experimental
public interface CopyReferenceSymbol extends Symbol {
  @Override
  @NotNull Pointer<? extends CopyReferenceSymbol> createPointer();

  /**
   * @return the copy reference target for this symbol.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull CopyReferenceTarget getCopyReferenceTarget(final @NotNull Project project);
}
