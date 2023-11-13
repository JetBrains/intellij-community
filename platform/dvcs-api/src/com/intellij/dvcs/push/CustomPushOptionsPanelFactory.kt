// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
interface CustomPushOptionsPanelFactory {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<CustomPushOptionsPanelFactory> = ExtensionPointName("com.intellij.customPushOptionsPanelFactory")
  }

  val id: String
  fun createOptionsPanel(parentDisposable: Disposable, repos: Collection<Repository>): VcsPushOptionsPanel?
}