// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@fleet.util.multiplatform.Actual("currentThreadId")
fun currentThreadIdWasmJs(): Long = 0

@fleet.util.multiplatform.Actual("currentThreadName")
fun currentThreadNameWasmJs(): String = "main"
