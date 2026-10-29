// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/// A storage for checkpoints that let [KeyHashLog] resume updates to the cache of project key hashes.
/// It stores cache state without retaining a project
@ApiStatus.Internal
public interface KeyHashLogCheckpointStore {
  @NotNull Checkpoint load(int checkpointId) throws IOException;

  /// `checkpointId` is an arbitrary id of a checkpoint, to later retrieve it in [#load]
  void save(int checkpointId,
            @NotNull Checkpoint checkpoint) throws IOException;

  /// A snapshot of the cache of project key hashes, with the journal position and counters needed to resume updates
  final class Checkpoint {

    /// up to that position (exclusive) all data is accounted in this checkpoint
    private final @NotNull KeyHashLogStorage.Position scannedUpTo;
    private final long modificationCount;
    private final int removesUnaccountedFor;

    private final @NotNull IntSet keyHashes;

    public Checkpoint(@NotNull KeyHashLogStorage.Position scannedUpTo,
                      long modificationCount,
                      int removesUnaccountedFor,
                      @NotNull IntSet keyHashes) {
      this.scannedUpTo = scannedUpTo;
      this.modificationCount = modificationCount;
      this.removesUnaccountedFor = removesUnaccountedFor;
      this.keyHashes = keyHashes;
    }

    public @NotNull KeyHashLogStorage.Position scannedUpTo() {
      return scannedUpTo;
    }

    public long modificationCount() {
      return modificationCount;
    }

    public int removesUnaccountedFor() {
      return removesUnaccountedFor;
    }

    public @NotNull IntSet keyHashes() {
      return keyHashes;
    }
  }
}
