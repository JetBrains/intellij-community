// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.configurations

import com.intellij.util.ArrayUtil
import com.intellij.util.text.nullize
import javax.swing.Icon

private val EMPTY_FACTORIES = arrayOf<ConfigurationFactory>()

abstract class ConfigurationTypeBase protected constructor(private val id: String, private val displayName: String, description: String? = null, private val icon: Lazy<Icon>?) : ConfigurationType {
  companion object {
    @JvmStatic
    fun lazyIcon(producer: () -> Icon): Lazy<Icon> {
      return lazy(LazyThreadSafetyMode.NONE, producer)
    }
  }

  constructor(id: String, displayName: String, description: String?, icon: Icon?) : this(id, displayName, description, icon?.let { lazyOf(it) })

  private var factories = EMPTY_FACTORIES

  private var description = description.nullize() ?: displayName

  protected fun addFactory(factory: ConfigurationFactory) {
    factories = ArrayUtil.append(factories, factory)
  }

  override fun getDisplayName() = displayName

  override final fun getConfigurationTypeDescription() = description

  // open due to backward compatibility
  override fun getIcon() = icon?.value

  override final fun getId() = id

  override fun getConfigurationFactories() = factories
}
