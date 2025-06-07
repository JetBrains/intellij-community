// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual

@Actual("getName")
internal fun getNameJvm(): String = System.getProperty("os.name")

@Actual("getVersion")
internal fun getVersionJvm(): String = System.getProperty("os.version").lowercase()

@Actual("getArch")
internal fun getArchJvm(): String = System.getProperty("os.arch")
