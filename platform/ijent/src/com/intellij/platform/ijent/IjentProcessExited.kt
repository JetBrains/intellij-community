// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.platform.eel.EelUnavailableException
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * The IJent process exited with a non-zero exit code, or a signal killed it.
 *
 * It is the root cause of the end of the session. A killed container or a destroyed WSL distribution gives it,
 * so it is a failure of the environment, not a bug.
 */
@Internal
@Suppress("HardCodedStringLiteral") // Internal diagnostic message, not user-facing UI text.
@VisibleToClasses(
  "com.intellij.platform.frontend.split.baseline.light.LightIjentRedeploymentTest",
  "com.intellij.platform.ijent.IjentSessionExitReasonTest",
  "com.intellij.platform.ijent.functional.IjentRestartTest",
  "com.intellij.platform.ijent.functional.IjentWslShutdownTest",
  "com.intellij.platform.ijent.functional.SharedIjents",
  "com.intellij.platform.ijent.functional.TcpLocalBootstrapTest",
  "com.intellij.platform.ijent.impl.GrpcIjentUtilTest",
  "com.intellij.platform.ijent.impl.base.GrpcIjentHealthCheckInterceptorTest",
  "com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategyUnitTest",
)
class IjentProcessExited(
  message: String,
  cause: Throwable?,
  private vararg val attachments: Attachment,
) : EelUnavailableException.Conclusive(message, cause), ExceptionWithAttachments {
  override fun copyForCaller(): EelUnavailableException = IjentProcessExited(message, this)

  override fun getAttachments(): Array<out Attachment> = attachments
}
