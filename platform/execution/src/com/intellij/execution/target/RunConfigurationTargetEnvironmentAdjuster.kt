// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target

import com.intellij.execution.configuration.AbstractRunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.annotations.ApiStatus

/**
 * Allows to configure the request for creating the target environment based on the settings of the corresponding Run Configuration.
 */
@ApiStatus.Internal
interface RunConfigurationTargetEnvironmentAdjuster {
  /**
   * Adjusts [targetEnvironmentRequest] using the settings from [runConfiguration].
   *
   * Note that [Sdk] assigned to [runConfiguration] is expected to match SDK that was used for finding the current adjuster using
   * [isEnabledFor] or the utility method [findTargetEnvironmentRequestAdjuster].
   */
  fun adjust(targetEnvironmentRequest: TargetEnvironmentRequest, runConfiguration: RunConfigurationBase<*>)

  fun providesAdditionalRunConfigurationUI(): Boolean

  fun createAdditionalRunConfigurationUI(runConfiguration: AbstractRunConfiguration,
                                         sdkGetter: () -> Sdk?): SettingsEditor<AbstractRunConfiguration>?

  fun isEnabledFor(sdk: Sdk): Boolean

  companion object {
    private val LOG = logger<RunConfigurationTargetEnvironmentAdjuster>()

    @JvmStatic
    val EP_NAME = ExtensionPointName<RunConfigurationTargetEnvironmentAdjuster>("com.intellij.runConfigurationTargetEnvironmentAdjuster")

    @JvmStatic
    fun findTargetEnvironmentRequestAdjuster(sdk: Sdk): RunConfigurationTargetEnvironmentAdjuster? {
      val filter = EP_NAME.extensionList.filter { it.isEnabledFor(sdk) }
      if (filter.size > 1) {
        LOG.warn("Several RunConfigurationTargetEnvironmentAdjuster EPs suitable for SDK '$sdk' found." +
                 " Only the first one will be applied.")
      }
      return filter.firstOrNull()
    }
  }
}