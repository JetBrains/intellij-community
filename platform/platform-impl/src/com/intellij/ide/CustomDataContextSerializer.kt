// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.serialization.KSerializer
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to pass the specified data context item from frontend to backend for the purposes of action updates and execution.
 * Instances should be registered in .split modules, so that they are available both on the frontend and on the backend side.
 */
@ApiStatus.Experimental
interface CustomDataContextSerializer<T : Any> {
  @ApiStatus.Internal
  companion object {
    val EP_NAME: ExtensionPointName<CustomDataContextSerializer<*>>
      = ExtensionPointName("com.intellij.remote.customDataContextSerializer")
  }

  val key: DataKey<T>
  val serializer: KSerializer<T>
}