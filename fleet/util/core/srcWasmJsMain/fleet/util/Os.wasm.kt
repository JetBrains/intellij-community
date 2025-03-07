// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

// todo: define client's os
@Actual("getName")
internal fun getNameWasmJs(): String = "mac"

@Actual("getVersion")
internal fun getVersionWasmJs(): String = ""

@Actual("getArch")
internal fun getArchWasmJs(): String = ""
