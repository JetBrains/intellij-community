// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

val Dispatchers.multiplatformIO by lazy { DispatchersIO() }

internal fun DispatchersIO(): CoroutineDispatcher = linkToActual()