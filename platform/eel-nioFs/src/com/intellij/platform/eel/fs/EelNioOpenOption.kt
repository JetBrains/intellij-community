// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.channels.EelDelicateApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.OpenOption

/** Additional open options that only an Eel-backed file system understands. */
@ApiStatus.Experimental
sealed interface EelNioOpenOption : OpenOption {
  /**
   * Controls the auto close after EOF optimization.
   *
   * When the option is on and a read reaches the end of the file, the implementation closes the file at once.
   * A separate close call does not need a round trip to the remote machine.
   * If the caller uses the file again, the implementation reopens the file and restores the cursor position.
   *
   * Only an open for reading honors this option. An open for writing ignores it.
   *
   * When the caller does not pass this option, the registry key `ijent.auto.close.after.eof.read` supplies the default.
   */
  @EelDelicateApi
  data class AutoClose(val autoClose: Boolean) : EelNioOpenOption
}
