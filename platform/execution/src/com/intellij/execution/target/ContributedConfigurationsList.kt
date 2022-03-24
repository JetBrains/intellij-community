// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.execution.target.ContributedConfigurationBase.Companion.getTypeImpl
import com.intellij.execution.target.ContributedConfigurationsList.ContributedStateBase.Companion.deserializeState
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element

/**
 * Persistent storage of all [configurations][ContributedConfigurationBase] conforming to given [type][ContributedTypeBase].
 * Provides access to  the list of currently available configurations, while preserving the serialized state of configurations
 * provided by the currently unavailable plugins.
 */
open class ContributedConfigurationsList<C, T>(private val extPoint: ExtensionPointName<T>)
  : PersistentStateComponent<ContributedConfigurationsList.ListState>
  where C : ContributedConfigurationBase, T : ContributedTypeBase<out C> {

  private val resolvedInstances = mutableListOf<C>()
  private val unresolvedInstances = mutableListOf<ContributedStateBase>()

  fun clear() {
    resolvedInstances.clear()
    unresolvedInstances.clear()
  }

  fun findByName(name: String): C? {
    for (resolvedInstance in resolvedInstances) {
      if (resolvedInstance.displayName == name) {
        return resolvedInstance
      }
    }
    return null
  }

  inline fun <reified T> findByType(): T? where T : C {
    return resolvedConfigs().filterIsInstance<T>().firstOrNull()
  }

  fun <T> findByType(type: Class<T>): T? where T : C {
    return resolvedInstances.filterIsInstance(type).firstOrNull()
  }

  fun resolvedConfigs(): List<C> = resolvedInstances.toList()

  fun addConfig(config: C) {
    if (resolvedInstances.contains(config)) {
      Logger.getInstance(ContributedConfigurationsList::class.java).error("Cannot add duplicate: $config")
      return
    }
    resolvedInstances.add(config)
  }

  fun replaceAllWith(newList: List<C>) {
    with(resolvedInstances) {
      clear()
      addAll(newList)
    }
  }

  fun removeConfig(config: C) = resolvedInstances.remove(config)

  fun unresolvedNames(): List<String> = unresolvedInstances.mapNotNull { it.name }.toList()

  override fun getState(): ListState = ListState().also { it ->
    it.configs.addAll(resolvedInstances.map { toBaseState(it) })
    it.configs.addAll(unresolvedInstances)
  }

  override fun loadState(state: ListState) {
    loadState(state.configs)
  }

  fun loadState(configs: List<ContributedStateBase>) {
    clear()
    configs.forEach {
      val nextConfig = fromOneState(it)
      if (nextConfig == null) {
        unresolvedInstances.add(it)
      }
      else {
        resolvedInstances.add(nextConfig)
      }
    }
  }

  protected open fun toBaseState(config: C): ContributedStateBase = ContributedStateBase().also {
    it.loadFromConfiguration(config)
  }

  protected open fun fromOneState(state: ContributedStateBase): C? {
    return extPoint.deserializeState(state)
  }

  companion object {
    fun ContributedConfigurationBase.getSerializer() = getTypeImpl().createSerializer(this)
  }

  /**
   * Complete state of the [ContributedConfigurationsList] represented by the list of individual [configuration states][ContributedStateBase]
   */
  open class ListState : BaseState() {
    @get: XCollection(style = XCollection.Style.v2)
    var configs by list<ContributedStateBase>()
  }

  /**
   * State of the individual contributed configuration. Packs extension-specific serialized state of the configuration together with
   * information of its [type][ContributedTypeBase] and individual [name][]
   */
  open class ContributedStateBase : BaseState() {
    @get:Attribute("type")
    var typeId by string()

    @get:Attribute("name")
    var name by string()

    @get:Tag("config")
    var innerState: Element? by property<Element?>(null) { it === null }

    open fun loadFromConfiguration(config: ContributedConfigurationBase) {
      typeId = config.typeId
      name = config.displayName
      innerState = config.getSerializer().state?.let { XmlSerializer.serialize(it) }
    }

    companion object {
      private fun ContributedConfigurationBase.getSerializer() = getTypeImpl().createSerializer(this)

      fun <T, C> ExtensionPointName<T>.deserializeState(state: ContributedStateBase): C?
        where C : ContributedConfigurationBase, T : ContributedTypeBase<out C> {

        val type = extensionList.firstOrNull { it.id == state.typeId }
        val defaultConfig = type?.createDefaultConfig()
        return defaultConfig?.also {
          it.displayName = state.name ?: ""
          ComponentSerializationUtil.loadComponentState(it.getSerializer(), state.innerState)
        }
      }
    }
  }
}