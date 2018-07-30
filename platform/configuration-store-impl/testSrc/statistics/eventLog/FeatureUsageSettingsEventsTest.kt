// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore.statistics.eventLog

import com.intellij.configurationStore.statistic.eventLog.FeatureUsageSettingsEventPrinter
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ProjectRule
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureUsageSettingsEventsTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test
  fun projectNameToHash() {
    val name = "project-name"
    val printer = TestFeatureUsageSettingsEventsPrinter()
    assertNotNull(printer.toHash(name))
  }

  @Test
  fun noProjectNameToHash() {
    val printer = TestFeatureUsageSettingsEventsPrinter()
    assertNull(printer.toHash(null))
  }

  @Test
  fun recordAllDefaultApplicationComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, null)
    assertDefaultState(printer.result, false, false)
  }

  @Test
  fun recordDefaultApplicationComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, null)
    assertDefaultState(printer.result, false, false)

  }

  @Test
  fun recordAllDefaultComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertDefaultState(printer.result, true, false)
  }

  @Test
  fun recordDefaultComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, projectRule.project)
    assertDefaultState(printer.result, true, false)
  }

  @Test
  fun recordComponentForDefaultProject() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, ProjectManager.getInstance().defaultProject)
    assertDefaultState(printer.result, false, true)
  }

  @Test
  fun recordNotDefaultApplicationComponent() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, null)
    assertNotDefaultState(printer.result, false, false)
  }

  @Test
  fun recordNotDefaultComponent() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertNotDefaultState(printer.result, true, false)
  }

  private fun assertNotDefaultState(options: List<LoggedComponentInfo>, withProject: Boolean, defaultProject: Boolean) {
    assertEquals(1, options.size)
    val option = options[0]
    assertEquals("settings.MyTestComponent", option.group)
    assertEquals("boolOption", option.option)

    var size = 2
    if (withProject) size++
    if (defaultProject) size++

    assertEquals(size, option.data.size)
    assertTrue { option.data["value"] == true }
    assertTrue { option.data["default"] == false }
    if (withProject) {
      assertTrue { option.data.containsKey("project")}
    }
    if (defaultProject) {
      assertTrue { option.data["default_project"] == true }
    }
  }

  private fun assertDefaultState(options: List<LoggedComponentInfo>, withProject: Boolean, defaultProject: Boolean) {
    assertEquals(1, options.size)
    val option = options[0]
    assertEquals("settings.MyTestComponent", option.group)
    assertEquals("boolOption", option.option)

    var size = 1
    if (withProject) size++
    if (defaultProject) size++

    assertEquals(size, option.data.size)
    assertTrue { option.data["value"] == false }
    if (withProject) {
      assertTrue { option.data.containsKey("project")}
    }
    if (defaultProject) {
      assertTrue { option.data["default_project"] == true }
    }
  }

  private class TestFeatureUsageSettingsEventsPrinter : FeatureUsageSettingsEventPrinter() {
    val result : MutableList<LoggedComponentInfo> = ArrayList()

    override fun logConfig(groupId: String, eventId: String, data: Map<String, Any>) {
      result.add(LoggedComponentInfo(groupId, eventId, data))
    }
  }

  private class LoggedComponentInfo(val group: String, val option: String, val data: Map<String, Any>)

  @State(name = "MyTestComponent", reportStatistic = true)
  private class TestComponent : PersistentStateComponent<ComponentState> {
    private var state = ComponentState()

    override fun loadState(s: ComponentState) {
      state = s
    }

    override fun getState(): ComponentState? {
      return state
    }
  }

  @Suppress("unused")
  private class ComponentState(bool: Boolean = false, str: String = "string-option", list: List<Int> = ArrayList()) {
    @Attribute("bool-value")
    val boolOption : Boolean = bool

    @Attribute("str-value")
    val strOption : String = str

    @Attribute("int-values")
    val intOption : List<Int> = list
  }
}