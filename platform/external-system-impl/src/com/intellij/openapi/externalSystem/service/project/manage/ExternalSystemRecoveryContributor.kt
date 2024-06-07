// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.actions.cache.RecoveryScope
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ExternalSystemRecoveryContributor {

  suspend fun beforeClose(recoveryScope: RecoveryScope) = Unit

  suspend fun afterClose() = Unit

  suspend fun afterOpen(recoveryScope: RecoveryScope) = Unit

  interface Factory {
    fun createContributor(): ExternalSystemRecoveryContributor;
  }

  companion object {
    val EP_NAME: ExtensionPointName<Factory> = ExtensionPointName("com.intellij.externalSystemRecoveryContributor")
  }
}