// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Predicate;

/**
 * Wraps a storage (name, opening-method, check-is-closed-method), and ensuring only 1 opened instance of the storage
 * exists at each moment. I.e. if {@link #reopen()} is called, current storage instance must be either null (not opened
 * yet), or must be already closed (as reported by {@link #isClosedPredicate}) -- only when new instance is opened.
 * <p>
 * What happens if current instance is still opened, is controlled by {@link #failIfNotClosed} param:
 * <ul>
 * <li>if true -- {@link IllegalStateException} is thrown</li>
 * <li>if false -- warning is logged, and storage is re-opened regardless of previous one
 * TODO in second case previous instance must be closed before new instance is opened, but I postponed that</li>
 * </ul>
 */
@ApiStatus.Internal
public class StorageRef<C extends Closeable, E extends Exception> {
  private static final Logger LOG = Logger.getInstance(StorageRef.class);

  //TODO RC: if we don't throw exception -- we should close storage here, otherwise this is not logically-consistent
  //         behavior. This is temporary false: my idea is to first see how many WARNs we get, and then enable closing,
  //         which will lead to 'already closed' exceptions in a random places across the codebase
  private static final boolean CLOSE_BEFORE_REOPEN = false;

  private @Nullable C storage = null;

  private final String storageName;
  private final @NotNull ThrowableComputable<? extends C, ? extends E> storageOpener;
  private final Predicate<C> isClosedPredicate;
  private final boolean failIfNotClosed;

  public StorageRef(@NotNull String storageName,
                    @NotNull ThrowableComputable<? extends C, ? extends E> storageOpener,
                    @NotNull Predicate<@NotNull C> isClosedPredicate,
                    boolean failIfNotClosed) {
    this.storageName = storageName;
    this.storageOpener = storageOpener;
    this.isClosedPredicate = isClosedPredicate;
    this.failIfNotClosed = failIfNotClosed;
  }

  public synchronized C reopen() throws E, IOException {
    if (storage == null || isClosedPredicate.test(storage)) {
      storage = storageOpener.compute();
      return storage;
    }

    String message = storageName + " is already created and !closed yet -- close the existing storage before re-opening it again";
    if (failIfNotClosed) {
      throw new IllegalStateException(message);
    }

    LOG.warn(message);

    //TODO RC: if we don't throw exception -- we should close storage here, otherwise this is not logically-consistent
    //         behavior
    if (CLOSE_BEFORE_REOPEN) {
      storage.close();
    }

    storage = storageOpener.compute();
    return storage;
  }

  public synchronized void ensureClosed() throws IOException {
    if (storage == null || isClosedPredicate.test(storage)) {
      return;
    }

    String message = "Storage " + storageName + " is not closed yet";
    if (failIfNotClosed) {
      throw new IllegalStateException(message);
    }

    LOG.warn(message);

    //TODO RC: if we don't throw exception -- we should close storage here, otherwise this is not logically-consistent
    //         behavior
    if (CLOSE_BEFORE_REOPEN) {
      storage.close();
    }
  }
}
