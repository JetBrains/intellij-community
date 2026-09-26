// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IdeAudioCues {
  private val OWNER = IdeAudioCues::class.java

  @JvmField
  val ERROR_LINE: AudioCue =
    AudioCue("error.line", IdeBundle.messagePointer("audio.cue.error.line"), "sounds/error.wav", OWNER, 20)

  @JvmField
  val ERROR_CARET: AudioCue =
    AudioCue("error.caret", IdeBundle.messagePointer("audio.cue.error.caret"), "sounds/error.wav", OWNER, 30)

  @JvmField
  val WARNING_LINE: AudioCue =
    AudioCue("warning.line", IdeBundle.messagePointer("audio.cue.warning.line"), "sounds/warning.wav", OWNER, 40)

  @JvmField
  val WARNING_CARET: AudioCue =
    AudioCue("warning.caret", IdeBundle.messagePointer("audio.cue.warning.caret"), "sounds/warning.wav", OWNER, 50)

  @JvmField
  val FOLDED_LINE: AudioCue =
    AudioCue("folded.line", IdeBundle.messagePointer("audio.cue.folded.line"), "sounds/code_folding.wav", OWNER, 130)

  @JvmField
  val FOLDED_CARET: AudioCue =
    AudioCue("folded.caret", IdeBundle.messagePointer("audio.cue.folded.caret"), "sounds/code_folding.wav", OWNER, 140)
}

@ApiStatus.Internal
class IdeAudioCueProvider : AudioCueProvider {
  override val audioCues: List<AudioCue> = with(IdeAudioCues) {
    listOf(ERROR_LINE, ERROR_CARET, WARNING_LINE, WARNING_CARET, FOLDED_LINE, FOLDED_CARET)
  }
}
