// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.util.indexing.impl.storage.KeyHashLog;
import com.intellij.util.indexing.impl.storage.durablemap.database.StorageAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// A handle that manages a [KeyHashLog] session and its stored data;
/// Extends [StorageAccessor] that provides only open + clear lifecycle ops, with rebuild
/// and close with/without cleaning;
///
/// The index storage uses it to open the journal and check whether it needs rebuilding
@ApiStatus.Internal
public interface KeyHashLogAccessor<Key> extends StorageAccessor<KeyHashLog<Key>> {

  @Override
  @NotNull KeyHashLog<Key> open() throws IOException;

  boolean requiresRebuild();

  /// Marks the session clean only after the index map closes successfully
  void close(boolean clean) throws IOException;

  @Override
  void cleanData() throws IOException;
}
