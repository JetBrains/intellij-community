// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.RunConfigurationConverter
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import org.jdom.Element

private val LOG = logger<RunConfigurationConverter>()

/**
 * Lets a [RunConfigurationConverter] retype a stored element before a [RunnerAndConfigurationSettingsImpl] reads it.
 *
 * Every loader of a stored run configuration must call this. Without it, an element of a removed type resolves to
 * [com.intellij.execution.configurations.UnknownConfigurationType].
 */
internal fun convertRunConfigurationOnDemand(element: Element) {
  // `any` stops at the first converter that applies. One element belongs to one type, so the rest have nothing to do.
  ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.filterIsInstance<RunConfigurationConverter>().any {
    LOG.runAndLogException { it.convertRunConfigurationOnDemand(element) } ?: false
  }
}
