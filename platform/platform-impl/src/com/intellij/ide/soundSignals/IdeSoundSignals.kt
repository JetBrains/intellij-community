// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.ide.IdeBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object IdeSoundSignals {
  private val OWNER = IdeSoundSignals::class.java

  @JvmField
  val ERROR_LINE: SoundSignal =
    SoundSignal("error.line", IdeBundle.messagePointer("sound.signal.error.line"), "sounds/error.wav", OWNER, 20)

  @JvmField
  val ERROR_CARET: SoundSignal =
    SoundSignal("error.caret", IdeBundle.messagePointer("sound.signal.error.caret"), "sounds/error.wav", OWNER, 30)

  @JvmField
  val WARNING_LINE: SoundSignal =
    SoundSignal("warning.line", IdeBundle.messagePointer("sound.signal.warning.line"), "sounds/warning.wav", OWNER, 40)

  @JvmField
  val WARNING_CARET: SoundSignal =
    SoundSignal("warning.caret", IdeBundle.messagePointer("sound.signal.warning.caret"), "sounds/warning.wav", OWNER, 50)

  @JvmField
  val FOLDED_LINE: SoundSignal =
    SoundSignal("folded.line", IdeBundle.messagePointer("sound.signal.folded.line"), "sounds/code_folding.wav", OWNER, 130)

  @JvmField
  val FOLDED_CARET: SoundSignal =
    SoundSignal("folded.caret", IdeBundle.messagePointer("sound.signal.folded.caret"), "sounds/code_folding.wav", OWNER, 140)
}

@ApiStatus.Internal
class IdeSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = with(IdeSoundSignals) {
    listOf(ERROR_LINE, ERROR_CARET, WARNING_LINE, WARNING_CARET, FOLDED_LINE, FOLDED_CARET)
  }
}
