// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.active

import com.intellij.feedback.show.showInactiveTimeNotificationIfPossible
import com.intellij.openapi.project.Project
import java.time.LocalDateTime

/**
 * Temporary storage for information about the last user activity.
 *
 * @see track.active.EditorMouseEventTracker
 * @see track.active.EditorTypingEventTracker
 */

object LastActive {
  var lastActive: LocalDateTime = LocalDateTime.now()

  internal fun trackActive(project: Project) {
    showInactiveTimeNotificationIfPossible(project)
    lastActive = LocalDateTime.now()
  }
}