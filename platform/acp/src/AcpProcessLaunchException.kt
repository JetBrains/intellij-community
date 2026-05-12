// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.acp.api

/**
 * Thrown by [AcpProcessLauncher.startProcess] (and its dependencies) when an ACP agent process
 * cannot be launched: managed-runtime download failure, invalid configuration, or the underlying
 * EEL spawn failing.
 *
 * The [message] is intended to be user-facing — callers typically forward it to a chat error
 * bubble or a balloon notification.
 */
class AcpProcessLaunchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
