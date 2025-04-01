// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import fleet.multiplatform.shims.multiplatformIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

/*
Dispatcher intended for use in coroutines that blocks (not suspends) for indefinite amount of time
Dispatchers.IO is limited to 64 threads. See documentation for Dispatchers.IO for details

especially

 `Dispatchers.IO` has a unique property of elasticity: its views
 obtained with [CoroutineDispatcher.limitedParallelism] are
 not restricted by the `Dispatchers.IO` parallelism.
 */
@OptIn(ExperimentalCoroutinesApi::class)
val UnlimitedDispatcher: CoroutineDispatcher = Dispatchers.multiplatformIO.limitedParallelism(8192)
