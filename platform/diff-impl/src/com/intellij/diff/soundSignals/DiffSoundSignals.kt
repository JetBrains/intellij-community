// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.soundSignals

import com.intellij.ide.soundSignals.SoundSignal
import com.intellij.ide.soundSignals.SoundSignalGroup
import com.intellij.ide.soundSignals.SoundSignalProvider
import com.intellij.openapi.diff.DiffBundle
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DiffSoundSignals {
  private val OWNER = DiffSoundSignals::class.java

  @JvmField
  val GROUP: SoundSignalGroup = SoundSignalGroup(DiffBundle.messagePointer("sound.signal.group.diff"))

  @JvmField
  val LINE_INSERTED: SoundSignal = SoundSignal(
    "diff.line.inserted", DiffBundle.messagePointer("sound.signal.diff.line.inserted"),
    "sounds/diff_line_inserted.wav", OWNER, 200, GROUP,
  )

  @JvmField
  val LINE_DELETED: SoundSignal = SoundSignal(
    "diff.line.deleted", DiffBundle.messagePointer("sound.signal.diff.line.deleted"),
    "sounds/diff_line_deleted.wav", OWNER, 210, GROUP,
  )

  @JvmField
  val LINE_MODIFIED: SoundSignal = SoundSignal(
    "diff.line.modified", DiffBundle.messagePointer("sound.signal.diff.line.modified"),
    "sounds/diff_line_modified.wav", OWNER, 220, GROUP,
  )

  @JvmField
  val LINE_CONFLICT: SoundSignal = SoundSignal(
    "diff.line.conflict", DiffBundle.messagePointer("sound.signal.diff.line.conflict"),
    "sounds/diff_line_conflict.wav", OWNER, 230, GROUP,
  )
}

@ApiStatus.Internal
class DiffSoundSignalProvider : SoundSignalProvider {
  override val soundSignals: List<SoundSignal> = with(DiffSoundSignals) {
    listOf(LINE_INSERTED, LINE_DELETED, LINE_MODIFIED, LINE_CONFLICT)
  }
}
