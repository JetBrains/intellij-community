// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Experimental
import kotlin.reflect.KType

/**
 * Used for synchronizing custom data related to a [Project] instance
 * from Host to Thin Client in Remote Development.
 *
 * @param TData must be serializable (see [kotlinx.serialization.Serializable])
 * @property dataType must return the class of [TData]
 */
@Experimental
interface ProjectCustomDataSynchronizer<TData : Any> {
  companion object {
    val EP_NAME: ExtensionPointName<ProjectCustomDataSynchronizer<*>> =
      ExtensionPointName.create("com.intellij.projectCustomDataSynchronizer")
  }

  val id: String get() = javaClass.name

  val dataType: KType

  /**
   * Called from the backend side
   */
  fun getValues(project: Project): Flow<TData>

  /**
   * Called from the frontend side
   */
  // TODO [A.Bukhonov] maybe make it suspend?
  fun consumeValue(project: Project, value: TData)

  fun consumeValueAny(project: Project, value: Any) {
    @Suppress("UNCHECKED_CAST")
    consumeValue(project, value as TData)
  }
}