// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

@Actual("getName")
internal fun getNameWasm(): String = ""

@Actual("getVersion")
internal fun getVersionWasm(): String = ""

@Actual("getArch")
internal fun getArchWasm(): String = ""
