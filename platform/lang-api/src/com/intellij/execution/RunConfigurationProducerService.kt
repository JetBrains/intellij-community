// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Project component that keeps track of [RunConfigurationProducer] implementations that should be ignored for a given project. All
 * subclasses of classes specified here will be ignored when looking for configuration producers.
 */
@Service(Service.Level.PROJECT)
@State(name = "RunConfigurationProducerService", storages = [Storage("runConfigurations.xml")])
@ApiStatus.Internal
class RunConfigurationProducerService : PersistentStateComponent<RunConfigurationProducerService.State> {
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

  fun addIgnoredProducer(ignoredProducer: Class<out RunConfigurationProducer<*>?>) {
    myState.ignoredProducers.add(ignoredProducer.getName())
  }

  fun isIgnored(producer: RunConfigurationProducer<*>): Boolean = myState.ignoredProducers.contains(producer.javaClass.getName())
}
