// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Stores and retrieves values, associated with a {@link  VirtualFile} and surviving IDE restarts.
 * <p>
 * Main use case is to provide a storage for pushed file properties by Indexes, so on IDE restart previous state can be retrieved from
 * hard drive directly, not from pushers.
 *
 * @param <T> type of value to store and retrieve
 */
@ApiStatus.Internal
@ApiStatus.Experimental
public interface FilePropertyKey<T> {
  /**
   * Retrieves persistent value associated with {@code virtualFile}
   *
   * @param virtualFile file to associate value with
   * @return previously stored value, or {@code null}
   */
  @Contract("null -> null")
  T getPersistentValue(@Nullable VirtualFile virtualFile);

  /**
   * Updates persistent value associated with {@code virtualFile}
   *
   * @param virtualFile file to store new value to
   * @param value       new value to store
   * @return {@code true} if value has changed. {@code false} otherwise
   */
  @Contract("null,_ -> false")
  boolean setPersistentValue(@Nullable VirtualFile virtualFile, T value);
}
