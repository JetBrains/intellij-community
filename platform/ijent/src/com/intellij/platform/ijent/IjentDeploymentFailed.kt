// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * The deployment of IJent failed, and the deployer ends the session because of it.
 *
 * It is the root cause of the end of the session. The deployer kills the shell after the failure, and IJent can then exit with the code 0.
 * That exit must not hide the failure.
 */
@Internal
@Suppress("HardCodedStringLiteral") // Internal diagnostic message, not user-facing UI text.
class IjentDeploymentFailed(message: String, cause: Throwable?) : EelUnavailableException.Conclusive(message, cause) {
  override fun copyForCaller(): EelUnavailableException = IjentDeploymentFailed(message, this)
}
