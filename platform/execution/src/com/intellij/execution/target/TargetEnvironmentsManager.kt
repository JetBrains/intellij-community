// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.target.TargetEnvironmentsManager.OneTargetState.Companion.toOneTargetState
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.text.UniqueNameGenerator
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "RemoteTargetsManager", storages = [Storage("remote-targets.xml")])
class TargetEnvironmentsManager : PersistentStateComponent<TargetEnvironmentsManager.TargetsListState> {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): TargetEnvironmentsManager =
      project.getService(TargetEnvironmentsManager::class.java)
  }

  /**
   * UUID of the target which is set as the project default or `null` if the project default target is the local machine.
   */
  private var projectDefaultTargetUuid: String? = null

  /**
   * The project default target or `null` if the project default target is the local machine.
   */
  var defaultTarget: TargetEnvironmentConfiguration?
    get() = projectDefaultTargetUuid?.let { uuid -> targets.resolvedConfigs().firstOrNull { it.uuid == uuid } }
    set(value) {
      projectDefaultTargetUuid = value?.uuid
    }

  val targets: ContributedConfigurationsList<TargetEnvironmentConfiguration, TargetEnvironmentType<*>> = TargetsList()

  override fun getState(): TargetsListState {
    val result = TargetsListState()
    result.projectDefaultTargetUuid = projectDefaultTargetUuid
    for (next in this.targets.state.configs) {
      result.targets.add(next as OneTargetState)
    }
    return result
  }

  override fun loadState(state: TargetsListState) {
    projectDefaultTargetUuid = state.projectDefaultTargetUuid
    targets.loadState(state.targets)
  }

  fun addTarget(target: TargetEnvironmentConfiguration) {
    if (!targets.resolvedConfigs().contains(target)) {
      ensureUniqueName(target)
      targets.addConfig(target)
    }
  }

  fun removeTarget(target: TargetEnvironmentConfiguration) {
    targets.removeConfig(target)
  }

  fun ensureUniqueName(target: TargetEnvironmentConfiguration) {
    if (!targets.resolvedConfigs().contains(target)) {
      val existingNames = targets.resolvedConfigs().map { it.displayName }.toSet() + targets.unresolvedNames()
      val uniqueName = UniqueNameGenerator.generateUniqueName(target.displayName, existingNames)
      target.displayName = uniqueName
    }
  }

  class TargetsList : ContributedConfigurationsList<TargetEnvironmentConfiguration, TargetEnvironmentType<*>>(
    TargetEnvironmentType.EXTENSION_NAME
  ) {
    override fun toBaseState(config: TargetEnvironmentConfiguration): OneTargetState = config.toOneTargetState()

    override fun fromOneState(state: ContributedStateBase): TargetEnvironmentConfiguration? = when (state) {
      is OneTargetState -> state.toTargetConfiguration()
      else -> super.fromOneState(state) // unexpected, but I do not want to fail just in case
    }
  }

  class TargetsListState : BaseState() {
    var projectDefaultTargetUuid: String? by string()

    @get: XCollection(style = XCollection.Style.v2)
    var targets: MutableList<OneTargetState> by list()
  }

  @Tag("target")
  class OneTargetState : ContributedConfigurationsList.ContributedStateBase() {
    @get:Attribute("uuid")
    var uuid: String? by string()

    @get: XCollection(style = XCollection.Style.v2)
    @get: Property(surroundWithTag = false)
    var runtimes: MutableList<ContributedConfigurationsList.ContributedStateBase> by list()

    fun toTargetConfiguration(): TargetEnvironmentConfiguration? {
      return TargetEnvironmentType.EXTENSION_NAME.deserializeState(this)?.also { result ->
        result.uuid = uuid ?: UUID.randomUUID().toString()
        result.runtimes.loadState(runtimes)
      }
    }

    companion object {
      fun TargetEnvironmentConfiguration.toOneTargetState(): OneTargetState = OneTargetState().also { state ->
        state.loadFromConfiguration(this)
        state.uuid = uuid
        state.runtimes = runtimes.state.configs
      }
    }
  }
}
