// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.experiment.ab.impl.experiment.getABExperimentInstance
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class NewUsersOnboardingExperiment {
  /**
   * It determines the state by using machine ID, that is calculated each time now.
   * Since it is not changed during IDE session, better to calculate it once.
   */
  private val isExperimentEnabled: Boolean by lazy {
    getABExperimentInstance().isExperimentOptionEnabled(NewUsersOnboardingExperimentOption::class.java)
  }

  /**
   * Should not be executed on EDT or with read lock because it can run the external process to calculate the machine ID.
   * Machine ID is needed to determine the experiment group.
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun isEnabled(): Boolean {
    return isExperimentEnabled
  }

  companion object {
    fun getInstance(): NewUsersOnboardingExperiment = service()
  }
}