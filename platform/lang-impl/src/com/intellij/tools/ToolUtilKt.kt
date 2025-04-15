// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.asEelPath
import java.nio.file.Path

internal object ToolUtilKt {

  @JvmStatic
  fun toEelPath(path: Path): EelPath = path.asEelPath()

}