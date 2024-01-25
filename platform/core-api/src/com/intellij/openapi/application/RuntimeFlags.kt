// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus
import java.lang.IllegalStateException

private const val ENABLE_NEW_LOCK_PROPERTY = "idea.enable.new.lock"
private const val ENABLE_BACKGROUND_WRITE_PROPERTY = "idea.enable.background.write"

@get:ApiStatus.Internal
val isNewLockEnabled: Boolean
  get() =  java.lang.Boolean.getBoolean(ENABLE_NEW_LOCK_PROPERTY)
