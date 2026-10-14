// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.notification.NotificationType

internal object IdeSounds {
  const val ERROR: String = "sounds/error.wav"
  const val WARNING: String = "sounds/warning.wav"
  const val CODE_FOLDING: String = "sounds/code_folding.wav"
  const val NOTIFICATION_INFO: String = "sounds/notification_info.wav"
  const val NOTIFICATION_WARNING: String = "sounds/notification_warning.wav"
  const val NOTIFICATION_ERROR: String = "sounds/notification_error.wav"
  const val PROGRESS_INDETERMINATE: String = "sounds/progress_indeterminate.wav"
  const val PROGRESS_DETERMINATE_STAGE_1: String = "sounds/progress_determinate_stage_1.wav"
  const val PROGRESS_DETERMINATE_STAGE_2: String = "sounds/progress_determinate_stage_2.wav"
  const val PROGRESS_DETERMINATE_STAGE_3: String = "sounds/progress_determinate_stage_3.wav"
  const val PROGRESS_DETERMINATE_STAGE_4: String = "sounds/progress_determinate_stage_4.wav"

  val NOTIFICATION_SOUNDS: List<String> = listOf(NOTIFICATION_INFO, NOTIFICATION_WARNING, NOTIFICATION_ERROR)
}

internal fun notificationSound(type: NotificationType): String = when (type) {
  NotificationType.INFORMATION, NotificationType.IDE_UPDATE -> IdeSounds.NOTIFICATION_INFO
  NotificationType.WARNING -> IdeSounds.NOTIFICATION_WARNING
  NotificationType.ERROR -> IdeSounds.NOTIFICATION_ERROR
}
