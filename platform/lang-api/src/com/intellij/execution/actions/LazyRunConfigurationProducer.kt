// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration

/**
 * Base class for RunConfigurationProducer that requires to implement [getConfigurationFactory] instead of passing it as a constructor parameter.
 *
 * The old approach to pass configuration factory as [RunConfigurationProducer] constructor parameter doesn't support lazy loading, -
 * operations on producer list lead to loading of not required configuration factories (in turn, it leads to loading more and more not required classes).
 *
 * It's an additional class and not part of existing [RunConfigurationProducer] to check code correctness at compile time.
 */
abstract class LazyRunConfigurationProducer<T : RunConfiguration> : RunConfigurationProducer<T>(true) {
  abstract override fun getConfigurationFactory(): ConfigurationFactory
}