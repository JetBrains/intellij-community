// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IdeSoundSignals {
  private val OWNER = IdeSoundSignals::class.java

  @JvmField
  val EVENTS_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.events"))

  @JvmField
  val CODE_HIGHLIGHTING_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.code.highlighting"))

  @JvmField
  val EDITOR_GUTTER_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.editor.gutter"))

  @JvmField
  val FOLDING_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.folding"))

  @JvmField
  val BUILD_FINISHED: SoundSignal = SoundSignal(
    "build.finished", IdeBundle.messagePointer("sound.signal.build.finished"),
    IdeSounds.NOTIFICATION_SOUNDS, OWNER, 30, EVENTS_GROUP,
  )

  @JvmField
  val TEST_RESULTS: SoundSignal = SoundSignal(
    "test.results", IdeBundle.messagePointer("sound.signal.test.results"),
    IdeSounds.NOTIFICATION_SOUNDS, OWNER, 40, EVENTS_GROUP,
  )

  @JvmField
  val PROGRESS: SoundSignal = SoundSignal(
    "progress", IdeBundle.messagePointer("sound.signal.progress"),
    listOf(
      IdeSounds.PROGRESS_INDETERMINATE, IdeSounds.PROGRESS_DETERMINATE_STAGE_1, IdeSounds.PROGRESS_DETERMINATE_STAGE_2,
      IdeSounds.PROGRESS_DETERMINATE_STAGE_3, IdeSounds.PROGRESS_DETERMINATE_STAGE_4,
    ),
    OWNER, 70, preview = listOf(IdeSounds.PROGRESS_INDETERMINATE),
  )

  @JvmField
  val ERROR_LINE: SoundSignal = SoundSignal(
    "error.line", IdeBundle.messagePointer("sound.signal.error.line"),
    IdeSounds.ERROR, OWNER, 100, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val ERROR_CARET: SoundSignal = SoundSignal(
    "error.caret", IdeBundle.messagePointer("sound.signal.error.caret"),
    IdeSounds.ERROR, OWNER, 110, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val WARNING_LINE: SoundSignal = SoundSignal(
    "warning.line", IdeBundle.messagePointer("sound.signal.warning.line"),
    IdeSounds.WARNING, OWNER, 120, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val WARNING_CARET: SoundSignal = SoundSignal(
    "warning.caret", IdeBundle.messagePointer("sound.signal.warning.caret"),
    IdeSounds.WARNING, OWNER, 130, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val FOLDED_LINE: SoundSignal = SoundSignal(
    "folded.line", IdeBundle.messagePointer("sound.signal.folded.line"),
    IdeSounds.CODE_FOLDING, OWNER, 400, FOLDING_GROUP,
  )

  @JvmField
  val FOLDED_CARET: SoundSignal = SoundSignal(
    "folded.caret", IdeBundle.messagePointer("sound.signal.folded.caret"),
    IdeSounds.CODE_FOLDING, OWNER, 410, FOLDING_GROUP,
  )
}

@ApiStatus.Internal
class IdeSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = with(IdeSoundSignals) {
    listOf(BUILD_FINISHED, TEST_RESULTS, PROGRESS, ERROR_LINE, ERROR_CARET, WARNING_LINE, WARNING_CARET, FOLDED_LINE, FOLDED_CARET)
  }
}
