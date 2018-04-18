// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.configurations

import com.intellij.util.ArrayUtil
import com.intellij.util.text.nullize
import javax.swing.Icon

private val EMPTY_FACTORIES = arrayOf<ConfigurationFactory>()

abstract class ConfigurationTypeBase protected constructor(private val id: String, private val displayName: String, description: String?, private val icon: Icon?) : ConfigurationType {
  private var factories = EMPTY_FACTORIES

  private var description = description.nullize() ?: displayName

  protected fun addFactory(factory: ConfigurationFactory) {
    factories = ArrayUtil.append(factories, factory)
  }

  override fun getDisplayName() = displayName

  override final fun getConfigurationTypeDescription() = description

  // open due to backward compatibility
  override fun getIcon() = icon

  override final fun getId() = id

  override fun getConfigurationFactories() = factories
}
