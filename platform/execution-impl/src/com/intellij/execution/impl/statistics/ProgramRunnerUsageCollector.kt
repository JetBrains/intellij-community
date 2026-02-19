// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl.statistics

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.ProgramRunner
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object ProgramRunnerUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("program.runner", 1)

  private val RUNNER: EventField<Class<*>?> = EventFields.Class("runner")

  private val RUN_PROFILE: EventField<Class<*>?> = EventFields.Class("run_profile")

  private val IS_ASYNC: EventField<Boolean> = EventFields.Boolean("async")

  private val EXECUTE_ACTIVITY = GROUP.registerIdeActivity(
    "execute",
    startEventAdditionalFields = arrayOf(RUNNER, RUN_PROFILE),
    finishEventAdditionalFields = arrayOf(RUNNER, RUN_PROFILE, IS_ASYNC)
  )

  /**
   * Records the starting of [ProgramRunner.execute].
   */
  fun startExecute(project: Project, runner: ProgramRunner<*>, profile: RunProfile): StructuredIdeActivity {
    return EXECUTE_ACTIVITY.started(project) {
      listOf(RUNNER.with(runner::class.java), RUN_PROFILE.with(profile::class.java))
    }
  }

  /**
   * Records when [ProgramRunner.execute] finishes and started the program.
   */
  fun finishExecute(activity: StructuredIdeActivity, runner: ProgramRunner<*>, profile: RunProfile, isAsync: Boolean) {
    activity.finished {
      listOf(RUNNER.with(runner::class.java), RUN_PROFILE.with(profile::class.java), IS_ASYNC.with(isAsync))
    }
  }
}