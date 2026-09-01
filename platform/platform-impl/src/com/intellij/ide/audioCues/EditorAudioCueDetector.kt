// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface EditorAudioCueDetector {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorAudioCueDetector> = ExtensionPointName("com.intellij.editorAudioCueDetector")
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  fun detect(editor: Editor, line: Int, caretOffset: Int): Set<EditorAudioCue>
}

@ApiStatus.Internal
data class EditorAudioCue(
  val cue: AudioCue,
  /** The line cue that this cue refines. When set, the editor replays this cue inside the line and mutes it when the line cue plays. */
  val lineCounterpart: AudioCue? = null,
)
