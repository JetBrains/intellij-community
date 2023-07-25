// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.backend.workspace

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a new state of the storage, it should be obtained from [BuilderSnapshot.getStorageReplacement] and passed to 
 * [WorkspaceModel.replaceProjectModel].
 */
class StorageReplacement internal constructor(
  val version: Long,
  val builder: MutableEntityStorage,
  val changes: Map<Class<*>, List<EntityChange<*>>>
)

/**
 * Represents a modified state of the storage. 
 * Its instance can be obtained from [WorkspaceModel.getBuilderSnapshot].
 * This class can be used without global read or write lock, but it isn't a thread safe.
 */
class BuilderSnapshot @ApiStatus.Internal constructor(val version: Long, private val storage: EntityStorageSnapshot) {
  /**
   * Provides access to [MutableEntityStorage] which can be used to prepare the new state.
   */
  val builder: MutableEntityStorage = MutableEntityStorage.from(storage)

  /**
   * Returns `true` if entities in this instance differ from the original storage.
   */
  fun areEntitiesChanged(): Boolean = !builder.hasSameEntities(storage)

  /**
   * Prepares a replacement for the storage. 
   * Execution of this function may take considerable time, so it's better to invoke it from a background thread, and only pass its result
   * to [WorkspaceModel.replaceProjectModel] under write-lock.
   */
  fun getStorageReplacement(): StorageReplacement {
    val changes = builder.collectChanges(storage)
    return StorageReplacement(version, builder, changes)
  }
}