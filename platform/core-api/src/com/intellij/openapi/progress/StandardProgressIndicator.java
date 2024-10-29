// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress;

/**
 * Marker interface for indicator cancellation behavior in a standard way:
 * <ul>
 * <li>{@link #checkCanceled()} checks for {@link #isCanceled()} and throws {@link ProcessCanceledException} if returned {@code true}</li>
 * <li>{@link #cancel()} sets the corresponding flag</li>
 * <li>{@link #isCanceled()} is {@code true} after {@link #cancel()} call</li>
 * <li>all methods above are {@code final}</li>
 * </ul>
 */
public interface StandardProgressIndicator extends ProgressIndicator {
}
