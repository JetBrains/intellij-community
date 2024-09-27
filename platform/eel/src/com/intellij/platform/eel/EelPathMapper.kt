// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath
import java.nio.file.Path

fun EelPath.Absolute.toNioPath(eelApi: EelApi): Path = eelApi.mapper.toNioPath(this)

interface EelPathMapper {
  fun getOriginalPath(path: Path): EelPath.Absolute?
  fun toNioPath(path: EelPath.Absolute): Path
}