// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.configurations

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ArrayUtil
import com.intellij.util.LazyUtil
import com.intellij.util.text.nullize
import javax.swing.Icon

private val EMPTY_FACTORIES = arrayOf<ConfigurationFactory>()

abstract class ConfigurationTypeBase protected constructor(private val id: String, private val displayName: String, description: String? = null, private val icon: NotNullLazyValue<Icon>?) : ConfigurationType {
  companion object {
    @JvmStatic
    @Deprecated("Use LazyUtil.create", ReplaceWith("LazyUtil.create(producer)", "com.intellij.util.LazyUtil"))
    fun lazyIcon(producer: () -> Icon): NotNullLazyValue<Icon> = LazyUtil.create(producer)
  }

  @Deprecated("")
  constructor(id: String, displayName: String, description: String?, icon: Lazy<Icon>) : this(id, displayName, description, LazyUtil.create { icon.value })

  constructor(id: String, displayName: String, description: String?, icon: Icon?) : this(id, displayName, description, icon?.let { NotNullLazyValue.createConstantValue(it) })

  private var factories = EMPTY_FACTORIES

  private var description = description.nullize() ?: displayName

  protected fun addFactory(factory: ConfigurationFactory) {
    factories = ArrayUtil.append(factories, factory)
  }

  override fun getDisplayName(): String = displayName

  override final fun getConfigurationTypeDescription(): String = description

  // open due to backward compatibility
  override fun getIcon(): Icon? = icon?.value

  override final fun getId(): String = id

  override fun getConfigurationFactories(): Array<ConfigurationFactory> = factories
}
