// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.provider.getEelDescriptor
import java.nio.file.Path

internal fun getJavaPath(binPath: Path): Path {
  val os = binPath.getEelDescriptor().osFamily
  return binPath.resolve(if (os.isPosix) "java" else "java.exe")
}
