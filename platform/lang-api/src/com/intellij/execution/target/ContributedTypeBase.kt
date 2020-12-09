// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.components.PersistentStateComponent
import org.jetbrains.annotations.Nls
import javax.swing.Icon

/**
 * Base class for all contributed types, responsible for management and persistence of the
 * heterogeneous set of [configurations][ContributedConfigurationBase].
 *
 * Abstract subclasses of this class corresponds to extension points, and define some specific protocol
 * and common configuration options for all instances and common serialization format.
 * Concrete subclasses of this class corresponds to extensions of this extension point, which conform to protocol but extends the set
 * of the configuration options.
 *
 * E.g, an abstract [TargetEnvironmentType] corresponds to "com.intellij.executionTargetType" extension point, and defines the protocol by
 * [TargetEnvironmentType.createEnvironmentFactory] and the common configuration options by [TargetEnvironmentConfiguration].
 * Concrete subclass of [TargetEnvironmentType] would represent a plugin extension, e.g, for Docker-based targets.
 */
abstract class ContributedTypeBase<C : ContributedConfigurationBase>(val id: String) {

  abstract val displayName: String @Nls get

  abstract val icon: Icon

  abstract fun createSerializer(config: C): PersistentStateComponent<*>

  abstract fun createDefaultConfig(): C

  open fun initializeNewlyCreated(config: C) {}

  abstract fun duplicateConfig(config: C): C

  open val helpTopic: String? = null

  @Suppress("UNCHECKED_CAST")
  internal fun castConfiguration(config: ContributedConfigurationBase) = config as C

  companion object {
    @JvmStatic
    fun <Type, Config, State> duplicatePersistentComponent(type: Type, template: Config): Config
      where Config : PersistentStateComponent<State>,
            Config : ContributedConfigurationBase,
            Type : ContributedTypeBase<Config> {
      return type.createDefaultConfig().also {
        val state = template.state
        if (state != null) {
          it.loadState(state)
        }
        it.displayName = template.displayName
      }
    }
  }
}