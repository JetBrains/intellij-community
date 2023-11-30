// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KType

/**
 * Used for synchronizing custom data related to a [VirtualFile] instance
 * from Host to Thin Client in Remote Development.
 *
 * @param TData must be serializable (see [kotlinx.serialization.Serializable])
 * @property dataType must return the class of [TData]
 */
@ApiStatus.Experimental
interface VirtualFileCustomDataSynchronizer<TData : Any> {
  val id: String get() = javaClass.name

  val dataType: KType
}