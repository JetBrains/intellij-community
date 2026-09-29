// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IdeSoundSignals {
  private val OWNER = IdeSoundSignals::class.java

  @JvmField
  val CODE_HIGHLIGHTING_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.code.highlighting"))

  @JvmField
  val EDITOR_GUTTER_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.editor.gutter"))

  @JvmField
  val FOLDING_GROUP: SoundSignalGroup = SoundSignalGroup(IdeBundle.messagePointer("sound.signal.group.folding"))

  @JvmField
  val ERROR_LINE: SoundSignal = SoundSignal(
    "error.line", IdeBundle.messagePointer("sound.signal.error.line"),
    "sounds/error.wav", OWNER, 100, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val ERROR_CARET: SoundSignal = SoundSignal(
    "error.caret", IdeBundle.messagePointer("sound.signal.error.caret"),
    "sounds/error.wav", OWNER, 110, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val WARNING_LINE: SoundSignal = SoundSignal(
    "warning.line", IdeBundle.messagePointer("sound.signal.warning.line"),
    "sounds/warning.wav", OWNER, 120, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val WARNING_CARET: SoundSignal = SoundSignal(
    "warning.caret", IdeBundle.messagePointer("sound.signal.warning.caret"),
    "sounds/warning.wav", OWNER, 130, CODE_HIGHLIGHTING_GROUP,
  )

  @JvmField
  val FOLDED_LINE: SoundSignal = SoundSignal(
    "folded.line", IdeBundle.messagePointer("sound.signal.folded.line"),
    "sounds/code_folding.wav", OWNER, 400, FOLDING_GROUP,
  )

  @JvmField
  val FOLDED_CARET: SoundSignal = SoundSignal(
    "folded.caret", IdeBundle.messagePointer("sound.signal.folded.caret"),
    "sounds/code_folding.wav", OWNER, 410, FOLDING_GROUP,
  )
}

@ApiStatus.Internal
class IdeSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = with(IdeSoundSignals) {
    listOf(ERROR_LINE, ERROR_CARET, WARNING_LINE, WARNING_CARET, FOLDED_LINE, FOLDED_CARET)
  }
}
