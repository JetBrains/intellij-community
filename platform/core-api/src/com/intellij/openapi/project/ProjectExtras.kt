// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.extensions.ExtensionPointName
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KType

interface ProjectExtras<TData : Any> {
  companion object {
    val EP_NAME: ExtensionPointName<ProjectExtras<*>> = ExtensionPointName.create("com.intellij.project.extras")
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