// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface EditorSoundSignalDetector {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorSoundSignalDetector> = ExtensionPointName("com.intellij.editorSoundSignalDetector")
  }

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorSoundSignal>
}

@ApiStatus.Internal
data class EditorSoundSignal(
  val signal: SoundSignal,
  /**
   * The line signal that this signal refines.
   * When set, the editor replays this signal inside the line and mutes it when the line signal plays.
   */
  val lineCounterpart: SoundSignal? = null,
)
