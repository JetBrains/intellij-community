// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration

/**
 * Additional class and not part of existing RunConfigurationProducer to check code correctness at compile time.
 *
 * Approach to pass configuration factory as [RunConfigurationProducer] constructor parameter is better in terms of design,
 * but problem is that iteration of producer list leads to loading of not required configuration factories (in turn, it leads to loading more and more not required classes)
 */
abstract class LazyRunConfigurationProducer<T : RunConfiguration> : RunConfigurationProducer<T>(null) {
  abstract override fun getConfigurationFactory(): ConfigurationFactory
}