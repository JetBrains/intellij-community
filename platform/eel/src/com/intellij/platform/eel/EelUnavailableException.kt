// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.IOException

/**
 * Thrown when an EEL cannot be accessed or initialized.
 *
 * This exception indicates that the target execution environment (such as a remote machine,
 * Docker container, or WSL instance) is temporarily or permanently unavailable.
 *
 * Common scenarios include:
 * - Docker daemon connection failures
 * - Container not found or stopped
 * - Remote SSH connection issues
 * - Environment-specific setup errors
 *
 * This exception is typically thrown during:
 * - Eel initialization
 * - Project opening when the remote environment is unavailable
 *
 * The exception should contain a localized user-facing message explaining the specific
 * reason for unavailability, and optionally wrap the underlying cause.
 *
 * @param message Localized user-facing error message explaining why the EEL is unavailable
 * @param cause Optional underlying exception that caused the unavailability
 *
 */
@ApiStatus.Experimental
class EelUnavailableException @ApiStatus.Internal constructor(override val message: @Nls String, cause: Throwable? = null) : IOException(message, cause)
