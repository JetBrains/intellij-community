// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference;

import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.model.Symbol;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implement this interface and register it as a {@code com.intellij.copyReference.symbolTargetProvider} extension to provide one or more
 * {@link CopyReferenceTarget}s for a symbol.
 * <p>
 * The platform asks the providers in the extension order. The first provider that returns a non-empty list wins. The platform does not ask
 * the other providers. If no provider returns a target, the platform uses {@link CopyReferenceSymbol#getCopyReferenceTarget}.
 *
 * @see CopyReferenceAction
 * @see CopyReferenceSymbol
 * @see CopyReferenceTargetProvider
 */
@Experimental
@OverrideOnly
public interface SymbolCopyReferenceTargetProvider {
  /**
   * Gets the {@link CopyReferenceTarget}s for the given {@code symbol}.
   *
   * @return the targets for the symbol, or an empty list if the provider does not support the symbol.
   * @apiNote If multiple targets are returned, then their order is maintained and will be reflected in the popup.
   * The platform removes the duplicate targets of multiple symbols with {@link CopyReferenceTarget#equals(Object)}.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull List<? extends @NotNull CopyReferenceTarget> copyReferenceTargets(final @NotNull Project project, final @NotNull Symbol symbol);
}
