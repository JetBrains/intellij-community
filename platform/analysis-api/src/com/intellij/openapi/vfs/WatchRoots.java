// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.SystemIndependent;

import java.nio.file.Path;

/// Registers roots with the file change watcher of the local file system.
///
/// Every root is a system-independent path. A registration lives until its [Token] is closed.
/// A file system without a watcher returns a token that does nothing.
/// It is the client's responsibility to load the files it is interested in into the VFS.
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface WatchRoots {
  static @NotNull WatchRoots getInstance() {
    return ApplicationManager.getApplication().getService(WatchRoots.class);
  }

  /// One active root registration.
  @ApiStatus.NonExtendable
  interface Token extends AutoCloseable {
    /// Stops watching the root. A repeated call does nothing. Safe to call from any thread.
    @Override
    void close();
  }

  /// Starts watching `rootPath`.
  ///
  /// @param rootPath  a system-independent path
  /// @param recursive `true` to watch the whole subtree, `false` to watch only the direct children
  @NotNull Token watch(@NotNull @SystemIndependent String rootPath, boolean recursive);

  /// Starts watching `root`. See [#watch(String, boolean)].
  default @NotNull Token watch(@NotNull Path root, boolean recursive) {
    return watch(FileUtil.toSystemIndependentName(root.toString()), recursive);
  }

  /// Starts watching `rootPath` and stops when `parent` is disposed. See [#watch(String, boolean)].
  @SuppressWarnings("resource")
  default void watch(@NotNull @SystemIndependent String rootPath, boolean recursive, @NotNull Disposable parent) {
    var token = watch(rootPath, recursive);
    Disposer.register(parent, token::close);
  }

  /// Runs `body` as one change of the watched roots: the file watcher learns the new root set once, when `body` ends.
  /// Use it around a series of [#watch] and [Token#close] calls. A nested call joins the outer batch.
  default void batch(@NotNull Runnable body) {
    body.run();
  }
}
