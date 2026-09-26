// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * Thrown by [EelExecApi.spawnProcess] when the environment refuses to start the process — for example, the executable is missing or
 * not executable.
 *
 * [errno] is the underlying OS error code; [message] is its human-readable description.
 */
@ApiStatus.Experimental
class ExecuteProcessException(val errno: Int, override val message: String) : EelError, IOException()