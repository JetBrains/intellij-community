// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

private const val ENABLE_NEW_LOCK_PROPERTY = "idea.enable.new.lock"

@get:ApiStatus.Internal
val isNewLockEnabled: Boolean
  get() = System.getProperty(ENABLE_NEW_LOCK_PROPERTY, "true").toBoolean()
