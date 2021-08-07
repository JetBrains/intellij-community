// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.util.ArrayUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

private val EMPTY_FACTORIES = arrayOf<ConfigurationFactory>()

enum class RunConfigurationSingletonPolicy {
  SINGLE_INSTANCE,
  MULTIPLE_INSTANCE,
  SINGLE_INSTANCE_ONLY,
  MULTIPLE_INSTANCE_ONLY;

  val isPolicyConfigurable: Boolean
    get() = this != SINGLE_INSTANCE_ONLY && this != MULTIPLE_INSTANCE_ONLY

  val isAllowRunningInParallel: Boolean
    get() = this == MULTIPLE_INSTANCE || this == MULTIPLE_INSTANCE_ONLY
}

inline fun <reified T : ConfigurationType> runConfigurationType(): T = ConfigurationTypeUtil.findConfigurationType(T::class.java)

abstract class ConfigurationTypeBase protected constructor(@NonNls private val id: String,
                                                           @Nls private val displayName: String,
                                                           @Nls private val description: String? = null,
                                                           private val icon: NotNullLazyValue<Icon>?) : ConfigurationType {
  constructor(id: String, @Nls displayName: String, @Nls description: String?, icon: Icon?)
    : this(id, displayName, description, icon?.let { NotNullLazyValue.createConstantValue(it) })

  private var factories = EMPTY_FACTORIES

  protected fun addFactory(factory: ConfigurationFactory) {
    factories = ArrayUtil.append(factories, factory)
  }

  override fun getDisplayName() = displayName

  final override fun getConfigurationTypeDescription() = description.nullize() ?: displayName

  /** DO NOT override (not sealed because of backward compatibility) */
  override fun getIcon() = icon?.value

  final override fun getId() = id

  /** DO NOT override (not sealed because of backward compatibility) */
  override fun getConfigurationFactories() = factories
}

abstract class SimpleConfigurationType protected constructor(@NonNls private val id: String,
                                                             @Nls private val name: String,
                                                             @Nls private val description: String? = null,
                                                             private val icon: NotNullLazyValue<Icon>) : ConfigurationType, ConfigurationFactory() {
  @Suppress("LeakingThis")
  private val factories: Array<ConfigurationFactory> = arrayOf(this)

  final override fun getId() = id

  final override fun getDisplayName() = name

  final override fun getName() = name

  final override fun getConfigurationTypeDescription() = description.nullize() ?: displayName

  final override fun getIcon() = icon.value

  final override fun getConfigurationFactories() = factories

  final override fun getType() = this

  final override fun getIcon(configuration: RunConfiguration) = getIcon()
}
