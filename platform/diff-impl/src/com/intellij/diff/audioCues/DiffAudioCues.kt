// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.audioCues

import com.intellij.ide.audioCues.AudioCue
import com.intellij.ide.audioCues.AudioCueProvider
import com.intellij.openapi.diff.DiffBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DiffAudioCues {
  private val OWNER = DiffAudioCues::class.java

  @JvmField
  val LINE_INSERTED: AudioCue =
    AudioCue("diff.line.inserted", DiffBundle.messagePointer("audio.cue.diff.line.inserted"), "sounds/diff_line_inserted.wav", OWNER, 60)

  @JvmField
  val LINE_DELETED: AudioCue =
    AudioCue("diff.line.deleted", DiffBundle.messagePointer("audio.cue.diff.line.deleted"), "sounds/diff_line_deleted.wav", OWNER, 70)

  @JvmField
  val LINE_MODIFIED: AudioCue =
    AudioCue("diff.line.modified", DiffBundle.messagePointer("audio.cue.diff.line.modified"), "sounds/diff_line_modified.wav", OWNER, 80)

  @JvmField
  val LINE_CONFLICT: AudioCue =
    AudioCue("diff.line.conflict", DiffBundle.messagePointer("audio.cue.diff.line.conflict"), "sounds/diff_line_conflict.wav", OWNER, 90)
}

@ApiStatus.Internal
class DiffAudioCueProvider : AudioCueProvider {
  override val audioCues: List<AudioCue> = with(DiffAudioCues) {
    listOf(LINE_INSERTED, LINE_DELETED, LINE_MODIFIED, LINE_CONFLICT)
  }
}
