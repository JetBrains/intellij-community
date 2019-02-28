// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public interface AbstractSnapshotIndex<Key, Value> {
  @Nullable
  Map<Key, Value> readSnapshot(int hashId) throws IOException;

  void close() throws IOException;

  void flush() throws IOException;

  void clear() throws IOException;
}
