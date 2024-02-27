// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InspectionApplicationFactory {
  fun id(): String

  fun getApplication(args: List<String>): InspectionApplicationStart

  companion object {
    @JvmStatic
    fun getApplication(id: String, args: List<String>): InspectionApplicationStart =
      EP_NAME.extensionList
        .firstOrNull { it.id() == id }
        ?.getApplication(args)
      ?: throw InspectionApplicationException("There is no loaded inspect engine with id= '$id'. Please check loaded plugin list.")

    val EP_NAME: ExtensionPointName<InspectionApplicationFactory> = create("com.intellij.inspectionApplicationFactory")
  }
}

sealed interface InspectionApplicationStart {
  interface Synchronous : InspectionApplicationStart {
    fun startup()
  }

  interface Asynchronous : InspectionApplicationStart {
    suspend fun startup()
  }
}
