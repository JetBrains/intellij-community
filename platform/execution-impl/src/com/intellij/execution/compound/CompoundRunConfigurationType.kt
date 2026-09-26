// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.compound

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationSingletonPolicy
import com.intellij.execution.configurations.SimpleConfigurationType
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue

class CompoundRunConfigurationType : SimpleConfigurationType("CompoundRunConfigurationType",
                                                             ExecutionBundle.message("compound.run.configuration.type.name"),
                                                             ExecutionBundle.message("compound.run.configuration.type.description"),
                                                             NotNullLazyValue.createValue {
                                                               AllIcons.RunConfigurations.Compound
                                                             }) {
  override fun createTemplateConfiguration(project: Project): RunConfiguration {
    return CompoundRunConfiguration("Compound", project, this)
  }

  override fun getSingletonPolicy() = RunConfigurationSingletonPolicy.SINGLE_INSTANCE_ONLY

  override fun getHelpTopic() = "reference.dialogs.rundebug.CompoundRunConfigurationType"

  override fun getOptionsClass() = CompoundRunConfigurationOptions::class.java
}
