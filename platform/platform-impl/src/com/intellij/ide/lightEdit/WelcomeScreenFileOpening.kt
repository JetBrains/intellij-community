// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit

import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import java.nio.file.Path

internal fun isClaimedByWelcomeScreenProject(file: Path): Boolean {
  return WelcomeScreenProjectProvider.canOpenFilesFromSystemFileManager(file)
}
