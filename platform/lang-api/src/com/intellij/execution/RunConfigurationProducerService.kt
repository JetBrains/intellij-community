// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface RunConfigurationProducerSuppressor {
  companion object {
    internal val EP_NAME = ExtensionPointName<RunConfigurationProducerSuppressor>("com.intellij.runConfigurationProducerSuppressor")
  }

  fun shouldSuppress(producer: RunConfigurationProducer<*>, project: Project): Boolean
}

/**
 * Project component that keeps track of [RunConfigurationProducer] implementations that should be ignored for a given project. All
 * subclasses of classes specified here will be ignored when looking for configuration producers.
 */
@Service(Service.Level.PROJECT)
@State(name = "RunConfigurationProducerService", storages = [Storage("runConfigurations.xml")])
@ApiStatus.Internal
class RunConfigurationProducerService(private val project: Project) : PersistentStateComponent<RunConfigurationProducerService.State> {
  private var myState = State()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): RunConfigurationProducerService = project.service<RunConfigurationProducerService>()
  }

  override fun getState(): State = myState

  override fun loadState(state: State) {
    myState = state
  }

  class State {
    @JvmField
    val ignoredProducers: MutableSet<String?> = HashSet<String?>()
  }

  @Deprecated("Use RunConfigurationProducerSuppressor")
  fun addIgnoredProducer(ignoredProducer: Class<out RunConfigurationProducer<*>?>) {
    myState.ignoredProducers.add(ignoredProducer.getName())
  }

  fun isIgnored(producer: RunConfigurationProducer<*>): Boolean {
    for (suppressor in RunConfigurationProducerSuppressor.EP_NAME.extensionList) {
      if (suppressor.shouldSuppress(producer, project)) {
        return true
      }
    }

    val ignoredProducers = myState.ignoredProducers
    return !ignoredProducers.isEmpty() && ignoredProducers.contains(producer.javaClass.getName())
  }
}
