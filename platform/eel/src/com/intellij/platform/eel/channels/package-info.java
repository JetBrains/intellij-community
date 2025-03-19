// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * Channels are used to send and receive bytes as  {@link java.nio.ByteBuffer}.
 * This API is pretty much low-level, but other modules contain useful tools to copy channels, pipes, convert them into JVM API, and so on.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
package com.intellij.platform.eel.channels;

import org.jetbrains.annotations.ApiStatus;