// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations

import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.ReflectionUtil
import com.intellij.util.xmlb.annotations.Transient
import org.jdom.Element
import javax.swing.Icon

// RunConfiguration interface in java, so, we have to use base class (short kotlin property declaration doesn't support java)
abstract class RunConfigurationMinimalBase<T : BaseState>(name: String?, 
                                                          private val factory: ConfigurationFactory, 
                                                          private val project: Project) : RunConfiguration, PersistentStateComponent<T> {
  private var name: String = name ?: ""

  protected var options: T = createOptions()
    private set
  
  @Transient
  override fun getName(): String {
    // a lot of clients not ready that name can be null and in most cases it is not convenient - just add more work to handle null value
    // in any case for run configuration empty name it is the same as null, we don't need to bother clients and use null
    return StringUtilRt.notNullize(name)
  }

  override fun setName(value: String) {
    name = value
  }

  override fun getFactory(): ConfigurationFactory = factory

  override fun getIcon(): Icon = factory.icon

  override fun getProject(): Project = project
  
  final override fun readExternal(element: Element) {
    throw UnsupportedOperationException("readExternal must be not called")
  }
  
  final override fun writeExternal(element: Element) {
    // serializeObjectInto(options, element)
    throw UnsupportedOperationException("writeExternal must be not called")
  }

  override fun loadState(state: T) {
    options = state
  }

  private fun createOptions(): T {
    return ReflectionUtil.newInstance(getOptionsClass())
  }

  private fun getOptionsClass(): Class<T> {
    val result = factory.optionsClass
    @Suppress("UNCHECKED_CAST")
    return when {
      result != null -> result as Class<T>
      else -> {
        val instance = this as PersistentStateComponent<*>
        ComponentSerializationUtil.getStateClass(instance.javaClass)
      }
    }
  }
}