// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.indexing.FileContent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

public interface UpdatableSnapshotIndex<Key, Value> extends AbstractSnapshotIndex<Key, Value> {
  Map<Key, Value> readOrPutSnapshot(int hashId, @NotNull FileContent content) throws IOException;
}
