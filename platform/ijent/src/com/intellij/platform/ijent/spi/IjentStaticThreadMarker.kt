// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@Internal
internal const val IJENT_STATIC_THREAD_MARKER: String = "This thread belongs to Ijent and lives till the end of the app"

@Internal
@TestOnly
fun isIjentStaticThread(threadName: String): Boolean = IJENT_STATIC_THREAD_MARKER in threadName