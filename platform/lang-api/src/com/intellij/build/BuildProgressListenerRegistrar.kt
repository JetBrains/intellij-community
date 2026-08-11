// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface BuildProgressListenerRegistrar {
  fun register(project: Project, buildProgressObservable: BuildProgressObservable)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<BuildProgressListenerRegistrar> = ExtensionPointName("com.intellij.buildProgressListenerRegistrar")

    @JvmStatic
    fun registerBuildProgressListeners(project: Project, buildProgressObservable: BuildProgressObservable) {
      for (registrar in EP_NAME.extensionList) {
        registrar.register(project, buildProgressObservable)
      }
    }
  }
}
