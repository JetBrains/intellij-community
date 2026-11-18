// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * A file chooser dialogue that works with [Path] instead of [com.intellij.openapi.vfs.VirtualFile].
 *
 * This interface mirrors [FileChooserDialog].
 * Use it when the chooser shows a file system that has no VFS representation, for example a remote environment.
 * The caller then gets the chosen paths directly and does not need to convert them from VFS files.
 * Unlike [FileChooserDialog], the dialogue gets its project when it is created, not when it is shown.
 */
@ApiStatus.Experimental
interface NioFileChooserDialog {
  /**
   * Shows the dialogue and lets the user choose one or more files.
   *
   * @param toSelect the paths to select when the dialogue opens.
   * @param preferLastOverToSelect when `true`, the dialogue selects the last chosen path instead of [toSelect].
   * @return the paths that the user chose, or an empty list if the user cancelled the dialogue.
   */
  fun choose(vararg toSelect: Path, preferLastOverToSelect: Boolean = false): List<Path>
}
