// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import java.nio.file.Path

interface EelFsApi {
  fun getOriginalPath(path: Path): EelPath.Absolute
}

sealed interface EelPath {
  data class Relative(val path: String) : EelPath
  data class Absolute(val path: String) : EelPath
}