// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("TargetProcessHandlers")

package com.intellij.execution.target

import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

private val TARGET_ENVIRONMENT_KEY = Key<TargetEnvironment>("TargetEnvironment")

var ProcessHandler.targetEnvironment: TargetEnvironment?
  @ApiStatus.Internal
  get() = getUserData(TARGET_ENVIRONMENT_KEY)
  @ApiStatus.Internal
  set(value) {
    putUserData(TARGET_ENVIRONMENT_KEY, value)
  }
