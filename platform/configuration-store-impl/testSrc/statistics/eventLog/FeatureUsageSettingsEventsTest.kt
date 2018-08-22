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
    assertDefaultState(printer, false, false)
  }

  @Test
  fun recordDefaultApplicationComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, null)
    assertDefaultState(printer, false, false)
  }

  @Test
  fun recordAllDefaultComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertDefaultState(printer, true, false)
  }

  @Test
  fun recordDefaultComponent() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, projectRule.project)
    assertDefaultState(printer, true, false)
  }

  @Test
  fun recordDefaultMultiComponent() {
    val component = TestComponent()
    component.loadState(MultiComponentState())
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logDefaultConfigurationState(spec.name, MultiComponentState::class.java, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertEquals(2, printer.result.size)
    assertDefaultState(printer.getOptionByName("boolOption"), "boolOption", false, withProject, defaultProject)
    assertDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", true, withProject, defaultProject)
  }

  @Test
  fun recordComponentForDefaultProject() {
    val component = TestComponent()
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, ProjectManager.getInstance().defaultProject)
    assertDefaultState(printer, false, true)
  }

  @Test
  fun recordNotDefaultApplicationComponent() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, null)
    assertNotDefaultState(printer, false, false)
  }

  @Test
  fun recordNotDefaultComponent() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertNotDefaultState(printer, true, false)
  }

  @Test
  fun recordPartiallyNotDefaultMultiComponent() {
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = true))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertEquals(2, printer.result.size)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true, withProject, defaultProject)
    assertDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", true, withProject, defaultProject)
  }

  @Test
  fun recordNotDefaultMultiComponent() {
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = false))
    val spec = StoreUtil.getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter()
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertEquals(2, printer.result.size)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", false, withProject, defaultProject)

  }

  private fun assertNotDefaultState(printer: TestFeatureUsageSettingsEventsPrinter, withProject: Boolean, defaultProject: Boolean) {
    assertEquals(1, printer.result.size)
    assertNotDefaultState(printer.result[0], "boolOption", true, withProject, defaultProject)
  }

  private fun assertNotDefaultState(event: LoggedComponentStateEvents,
                                    name: String,
                                    value: Any,
                                    withProject: Boolean,
                                    defaultProject: Boolean) {
    assertEquals("settings", event.group)
    assertEquals("MyTestComponent", event.id)

    var size = 3
    if (withProject) size++
    if (defaultProject) size++

    assertEquals(size, event.data.size)
    assertTrue { event.data["name"] == name }
    assertTrue { event.data["value"] == value }
    assertTrue { event.data["default"] == false }
    if (withProject) {
      assertTrue { event.data.containsKey("project") }
    }
    if (defaultProject) {
      assertTrue { event.data["default_project"] == true }
    }
  }

  private fun assertDefaultState(printer: TestFeatureUsageSettingsEventsPrinter, withProject: Boolean, defaultProject: Boolean) {
    assertEquals(1, printer.result.size)
    assertDefaultState(printer.result[0], "boolOption", false, withProject, defaultProject)
  }

  private fun assertDefaultState(event: LoggedComponentStateEvents,
                                 name: String,
                                 value: Any,
                                 withProject: Boolean,
                                 defaultProject: Boolean) {
    assertEquals("settings", event.group)
    assertEquals("MyTestComponent", event.id)

    var size = 2
    if (withProject) size++
    if (defaultProject) size++

    assertEquals(size, event.data.size)
    assertTrue { event.data["name"] == name }
    assertTrue { event.data["value"] == value }
    if (withProject) {
      assertTrue { event.data.containsKey("project") }
    }
    if (defaultProject) {
      assertTrue { event.data["default_project"] == true }
    }
  }

  private class TestFeatureUsageSettingsEventsPrinter : FeatureUsageSettingsEventPrinter() {
    val result: MutableList<LoggedComponentStateEvents> = ArrayList()

    override fun logConfig(groupId: String, eventId: String, data: Map<String, Any>) {
      result.add(LoggedComponentStateEvents(groupId, eventId, data))
    }

    fun getOptionByName(name: String): LoggedComponentStateEvents {
      for (event in result) {
        if (event.data.containsKey("name") && event.data["name"] == name) {
          return event
        }
      }
      throw RuntimeException("Failed to find event")
    }
  }

  private class LoggedComponentStateEvents(val group: String, val id: String, val data: Map<String, Any>)

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
  private open class ComponentState(bool: Boolean = false, str: String = "string-option", list: List<Int> = ArrayList()) {
    @Attribute("bool-value")
    val boolOption: Boolean = bool

    @Attribute("str-value")
    val strOption: String = str

    @Attribute("int-values")
    val intOption: List<Int> = list
  }

  @Suppress("unused")
  private class MultiComponentState(bool: Boolean = false,
                                    secondBool: Boolean = true,
                                    str: String = "string-option",
                                    list: List<Int> = ArrayList()) : ComponentState(bool, str, list) {
    @Attribute("second-bool-value")
    val secondBoolOption: Boolean = secondBool
  }
}