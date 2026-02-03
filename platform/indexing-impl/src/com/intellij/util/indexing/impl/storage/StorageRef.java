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

import static com.intellij.util.SystemProperties.getBooleanProperty;

/**
 * Wraps a storage (name, opening-method, check-is-closed-method), and ensuring only 1 opened instance of the storage
 * exists at each moment. I.e. if {@link #reopen()} is called, current storage instance must be either null (not opened
 * yet), or must be already closed (as reported by {@link #isClosedPredicate}) -- only when new instance is opened.
 * <p>
 * What happens if current instance is still opened, is controlled by {@link #failIfNotClosed} param:
 * <ul>
 * <li>if true -- {@link IllegalStateException} is thrown</li>
 * <li>if false -- warning is logged, current storage is closed forcibly, and re-opened/cleaned</li>
 * </ul>
 */
@ApiStatus.Internal
public class StorageRef<C extends Closeable, E extends Exception> {
  private static final Logger LOG = Logger.getInstance(StorageRef.class);

  /**
   * If we don't throw exception, we must close the storage -- otherwise the invariant [<=1 storage instance exists at any given moment]
   * is broken -- and keeping this invariant is the only reason this class was created.
   * Hence, this property must be true, and inlined.
   * It is temporary made configurable, because I suspect it could cause too many tests failures, so I want to provide a
   * way out :)
   */
  private static final boolean CLOSE_BEFORE_REOPEN = getBooleanProperty("indexes.force-close-before-reopen", true);

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

  /**
   * Opens/re-opens storage, by using .storageOpener factory supplied into the ctor.
   * <p>
   * Ensures the current storage instance, if exists, is closed before the new instance opened -- i.e. ensures <= 1 instance
   * of a storage is opened at any given moment.
   * <p>
   * If the current instance is not closed, behavior depends on .failIfNotClosed ctor param:
   * <ul>
   * <li>if .failIfNotClosed=true  => throw {@link IllegalStateException}.</li>
   * <li>if .failIfNotClosed=false => log warning, close the current storage forcibly, and reopen it again</li>
   * </ul>
   * </p>
   */
  public synchronized C reopen() throws E, IOException, IllegalStateException {
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

  /**
   * Ensures current storage (if exists) is closed.
   * If the current instance is not closed, behavior depends on .failIfNotClosed ctor param:
   * <ul>
   * <li>if .failIfNotClosed=true  => throw {@link IllegalStateException}.</li>
   * <li>if .failIfNotClosed=false => log warning, and close the current storage forcibly</li>
   * </ul>
   */
  public synchronized void ensureClosed() throws IOException, IllegalStateException {
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
