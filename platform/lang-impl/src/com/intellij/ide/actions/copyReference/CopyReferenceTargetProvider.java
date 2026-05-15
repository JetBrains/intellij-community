// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference;

import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implement this interface and register it as a {@code com.intellij.copyReference.targetProvider} extension to provide one or more
 * {@link CopyReferenceTarget}s for a file and offset pair.
 * <p>
 * {@link CopyReferenceTarget}s from this interface are favoured and returned before any from {@link SymbolCopyReferenceTargetProvider} or
 * {@link CopyReferenceSymbol} (with an exception when there is an active lookup shown).
 * <p>
 * The platform asks the providers in the extension order. The first provider that returns a non-empty list wins. The platform does not ask
 * the other providers.
 *
 * @see CopyReferenceAction
 * @see CopyReferenceSymbol
 * @see SymbolCopyReferenceTargetProvider
 */
@Experimental
@OverrideOnly
public interface CopyReferenceTargetProvider {
  /**
   * Gets the {@link CopyReferenceTarget}s at the given offset inside the file.
   *
   * @return the targets at the offset, or an empty list if the provider has no target there.
   * @apiNote If multiple targets are returned, then their order is maintained and will be reflected in the popup.
   * Additionally, this method may be invoked multiple times for a file (e.g., to support multiple carets).
   * The platform removes the duplicate targets of multiple carets with {@link CopyReferenceTarget#equals(Object)}.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull List<? extends @NotNull CopyReferenceTarget> copyReferenceTargets(final @NotNull PsiFile file, final int offset);
}
