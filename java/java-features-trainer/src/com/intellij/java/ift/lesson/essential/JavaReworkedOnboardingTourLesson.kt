// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ift.lesson.essential

import com.intellij.execution.RunManager
import training.dsl.LessonContext

class JavaReworkedOnboardingTourLesson : CommonLogicForOnboardingTours("idea.onboarding.reworked", "Reworked onboarding tour") {
  override val sample = javaOnboardingTourSample

  override val completionStepExpectedCompletion: String = "length"

  override fun LessonContext.contextActions() = contextActionsForJavaOnboarding(sample)

  override val lessonContent: LessonContext.() -> Unit = {
    prepareRuntimeTask {
      rememberJdkAtStart()
      // So the Current run configuration will be set
      RunManager.getInstance(project).selectedConfiguration = null
    }

    prepareSample(sample)

    commonTasks()
  }
}