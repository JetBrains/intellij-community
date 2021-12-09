// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback.state.projectCreation

data class ProjectCreationInfoState(
  var numberNotificationShowed: Int = 0,
  var feedbackSent: Boolean = false,
  var lastCreatedProjectBuilderId: String? = null
)
