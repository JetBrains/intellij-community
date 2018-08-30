// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ArrayUtil
import com.intellij.util.LazyUtil
import com.intellij.util.text.nullize
import javax.swing.Icon

private val EMPTY_FACTORIES = arrayOf<ConfigurationFactory>()

enum class RunConfigurationSingletonPolicy {
  SINGLE_INSTANCE,
  MULTIPLE_INSTANCE,
  SINGLE_INSTANCE_ONLY,
  MULTIPLE_INSTANCE_ONLY;

  val isPolicyConfigurable: Boolean
    get() = this != SINGLE_INSTANCE_ONLY && this != MULTIPLE_INSTANCE_ONLY

  val isSingleton: Boolean
    get() = this == SINGLE_INSTANCE || this == SINGLE_INSTANCE_ONLY
}

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

  override fun getDisplayName() = displayName

  override final fun getConfigurationTypeDescription() = description

  // open due to backward compatibility
  /**
   * DO NOT override
   */
  override fun getIcon() = icon?.value

  override final fun getId() = id

  /**
   * DO NOT override
   */
  override fun getConfigurationFactories() = factories
}

abstract class SimpleConfigurationType protected constructor(private val id: String,
                                                             private val name: String,
                                                             description: String? = null,
                                                             private val icon: NotNullLazyValue<Icon>) : ConfigurationType {
  private var description = description.nullize() ?: name
  @Suppress("LeakingThis")
  val factory: ConfigurationFactory = object : ConfigurationFactory(this) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
      return this@SimpleConfigurationType.createTemplateConfiguration(project)
    }
  }

  private var factories = arrayOf(factory)

  protected abstract fun createTemplateConfiguration(project: Project): RunConfiguration

  protected open fun getOptionsClass(): Class<out BaseState>? = null

  override final fun getId() = id

  override final fun getDisplayName() = name

  override final fun getConfigurationTypeDescription() = description

  override final fun getIcon() = icon.value

  override final fun getConfigurationFactories(): Array<ConfigurationFactory> = factories
}
