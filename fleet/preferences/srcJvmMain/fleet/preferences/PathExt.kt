// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import java.nio.file.Path

@Deprecated("java Paths are not multiplatform. Please consider using kotlnx.io Path if possible")
fun kotlinx.io.files.Path.toNioPath(): Path = Path.of(this.toString())

fun kotlinx.io.files.Path.resolve(childPath: String): kotlinx.io.files.Path = kotlinx.io.files.Path(this, childPath)