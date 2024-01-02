// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.backend.workspace

import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a new state of the storage, it should be obtained from [BuilderSnapshot.getStorageReplacement] and passed to 
 * [WorkspaceModel.replaceProjectModel].
 */
public class StorageReplacement internal constructor(
  public val version: Long,
  public val builder: MutableEntityStorage,
  public val changes: Map<Class<*>, List<EntityChange<*>>>
)

/**
 * Represents a modified state of the storage. 
 * Its instance can be obtained from [WorkspaceModel.getBuilderSnapshot].
 * This class can be used without global read or write lock, but it isn't a thread safe.
 */
public class BuilderSnapshot @ApiStatus.Internal constructor(private val version: Long, private val storage: ImmutableEntityStorage) {
  /**
   * Provides access to [MutableEntityStorage] which can be used to prepare the new state.
   */
  public val builder: MutableEntityStorage = MutableEntityStorage.from(storage)

  /**
   * Returns `true` if entities in this instance differ from the original storage.
   */
  @OptIn(EntityStorageInstrumentationApi::class)
  public fun areEntitiesChanged(): Boolean = !(builder as MutableEntityStorageInstrumentation).hasSameEntities()

  /**
   * Prepares a replacement for the storage. 
   * Execution of this function may take considerable time, so it's better to invoke it from a background thread, and only pass its result
   * to [WorkspaceModel.replaceProjectModel] under write-lock.
   */
  @OptIn(EntityStorageInstrumentationApi::class)
  public fun getStorageReplacement(): StorageReplacement {
    val changes = (builder as MutableEntityStorageInstrumentation).collectChanges()
    return StorageReplacement(version, builder, changes)
  }
}