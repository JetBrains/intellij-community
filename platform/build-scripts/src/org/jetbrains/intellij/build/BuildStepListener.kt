// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildOptions.Companion.BUILD_STEPS_TO_SKIP_PROPERTY
import org.jetbrains.intellij.build.dependencies.TeamCityHelper

/**
 * > Not to be confused with [TeamCity build steps](https://www.jetbrains.com/help/teamcity/configuring-build-steps.html)
 *
 * Listens to lifecycle events of each build step launched with [org.jetbrains.intellij.build.executeStep].
 */
@ApiStatus.Internal
open class BuildStepListener {
  open suspend fun onStart(stepId: String, messages: BuildMessages) {}
  open suspend fun onSkipping(stepId: String, messages: BuildMessages) {}
  open suspend fun onFailure(stepId: String, failure: Throwable, messages: BuildMessages) {
    val description = buildString {
      append("'$stepId' build step failed")
      if (TeamCityHelper.isUnderTeamCity) {
        append(" (Please don't mute this problem!") // mute scope may be too broad muting similar failures in other build configuration
        append(" If you really need to ignore it, you may either mark this build as green or add '$stepId' to 'system.${BUILD_STEPS_TO_SKIP_PROPERTY}')")
      }
      append(": ")
      append(failure.stackTraceToString())
    }
    messages.reportBuildProblem(description, identity = stepId)
  }

  open suspend fun onCompletion(stepId: String, messages: BuildMessages) {}
}