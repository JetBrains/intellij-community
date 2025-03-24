// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.remoting

import com.intellij.openapi.client.currentSession
import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val owner: Permission = object : Permission {
  override val id: String
    get() = "owner"

  override fun isGranted(): Boolean {
    return application.currentSession.isOwner
  }
}