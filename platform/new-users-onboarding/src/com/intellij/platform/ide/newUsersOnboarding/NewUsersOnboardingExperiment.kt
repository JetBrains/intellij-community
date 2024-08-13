// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUsersOnboarding

import com.intellij.ide.ApplicationActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.experiment.ab.impl.experiment.getABExperimentInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

  fun isEnabled(): Boolean {
    return Registry.`is`("ide.newUsersOnboarding", false) && isExperimentEnabled
  }

  /**
   * To determine the experiment state, we need to calculate the machine ID.
   * It requires reading the file or running an external process: should be executed in the background.
   * Since some clients require knowing the state in EDT quite early (especially on the Welcome Screen),
   * better to calculate it safely and eagerly before instead of EDT.
   */
  internal class Initializer : ApplicationActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute() {
      withContext(Dispatchers.IO) {
        serviceAsync<NewUsersOnboardingExperiment>().isExperimentEnabled
      }
    }
  }
}