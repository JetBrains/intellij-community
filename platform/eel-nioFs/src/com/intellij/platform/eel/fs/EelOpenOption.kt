// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus
import java.nio.file.OpenOption

/** Additional options for [EelFiles.write]. */
@ApiStatus.Experimental
enum class EelOpenOption : OpenOption {
  /**
   * Creates missing parent directories before writing the file.
   * Other open options keep their usual behavior. If this is the only option, the default write options apply.
   */
  CREATE_PARENTS,
}
