// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("Will be deleted alongisde `com.intellij.psi.ExternalChangeAction` eventually")
interface WriteActionPresenceService {
  fun addWriteActionClass(clazz: Class<*>): Boolean

  fun removeWriteActionClass(clazz: Class<*>)

  fun hasWriteAction(clazz: Class<*>): Boolean
}