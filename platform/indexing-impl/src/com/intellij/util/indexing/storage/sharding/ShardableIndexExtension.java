// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.storage.sharding;

import org.jetbrains.annotations.ApiStatus;

/**
 * Interface to implement by {@link com.intellij.util.indexing.IndexExtension} to declare that the index _may_ be sharded.
 * The declaration is not a requirement: it is up to {@link com.intellij.util.indexing.storage.FileBasedIndexLayoutProvider}s
 * to provide or not an actual implementation of sharding for the index that declares sharding.
 * If {@link #shardsCount()} == 1 it means the sharding is disabled for the index.
 */
@ApiStatus.Internal
public interface ShardableIndexExtension {
  /**
   * A number of shards to use for the index, must be >=1
   * 1 shard means index is effectively not sharded
   */
  int shardsCount();

  /** Mapping must be deterministic (pure function) */
  default int shardNo(int inputId) {
    return inputId % shardsCount();
  }

  /**
   * @param enforcedShardsCount if it is >0 than it is the return value of the method, otherwise optimal # of shards is determined
   *                            from #CPUs available
   * @return number of shards to use for indexes: enforcedShardsCount if >0, or auto-detect optimal number of shards from #CPU
   * available on the machine
   */
  static int determineShardsCount(int enforcedShardsCount) {
    if (enforcedShardsCount > 0) {
      return enforcedShardsCount;
    }

    int cpus = Runtime.getRuntime().availableProcessors();
    //following mapping is based on benchmarks:
    // - 16-cores show little-to-no benefits from sharding
    // - 32-cores show some benefit from 2 shards
    // - 64..128-cores show better benefits from 3 shards.
    if (cpus <= 16) {
      return 1;
    }
    else if (cpus <= 32) {
      return 2;
    }
    else {
      return 3;
    }
  }
}
