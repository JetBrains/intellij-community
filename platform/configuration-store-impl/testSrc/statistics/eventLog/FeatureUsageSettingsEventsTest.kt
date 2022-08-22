// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.statistics.eventLog

import com.intellij.configurationStore.getStateSpec
import com.intellij.configurationStore.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ReportValue
import com.intellij.openapi.components.SkipReportingStatistics
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.NonNls
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Test

class FeatureUsageSettingsEventsTest {
  companion object {
    @JvmField @ClassRule val projectRule = ProjectRule()
  }

  @Test
  fun `record all default application component with enabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, null)
    assertDefaultState(printer, withProject = false, defaultProject = false)
  }

  @Test
  fun `record all default application component with disabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)
    assertDefaultWithoutDefaultRecording(printer, withProject = false, defaultProject = false)
  }

  @Test
  fun `record default application component with enabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, null)
    assertDefaultState(printer, withProject = false, defaultProject = false)
  }

  @Test
  fun `record default application component with disabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, null)
    assertDefaultWithoutDefaultRecording(printer, withProject = false, defaultProject = false)
  }

  @Test
  fun `record all default component with enabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertDefaultState(printer, withProject = true, defaultProject = false)
  }

  @Test
  fun `record all default component with disabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertDefaultWithoutDefaultRecording(printer, withProject = true, defaultProject = false)
  }

  @Test
  fun `record default component with enabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, projectRule.project)
    assertDefaultState(printer, withProject = true, defaultProject = false)
  }

  @Test
  fun `record default component with disabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logDefaultConfigurationState(spec.name, ComponentState::class.java, projectRule.project)
    assertDefaultWithoutDefaultRecording(printer, withProject = true, defaultProject = false)
  }

  @Test
  fun `record default multi component with enabled default recording`() {
    val component = TestComponent()
    component.loadState(MultiComponentState())
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logDefaultConfigurationState(spec.name, MultiComponentState::class.java, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertDefaultState(printer.getOptionByName("boolOption"), "boolOption", false, SettingsFields.Companion.Types.BOOl, withProject, defaultProject)
    assertDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", true, SettingsFields.Companion.Types.BOOl, withProject, defaultProject)
  }

  @Test
  fun `record default multi component with disabled default recording`() {
    val component = TestComponent()
    component.loadState(MultiComponentState())
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logDefaultConfigurationState(spec.name, MultiComponentState::class.java, projectRule.project)

    assertThat(printer.result).hasSize(1)
    assertDefaultWithoutDefaultRecording(printer, withProject = true, defaultProject = false)
  }

  @Test
  fun `record component for default project with enabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, ProjectManager.getInstance().defaultProject)
    assertDefaultState(printer, withProject = false, defaultProject = true)
  }

  @Test
  fun `record component for default project with disabled default recording`() {
    val component = TestComponent()
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, ProjectManager.getInstance().defaultProject)
    assertDefaultWithoutDefaultRecording(printer, withProject = false, defaultProject = true)
  }

  @Test
  fun `record not default application component with enabled default recording`() {
    val withRecordDefault = true
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, null)
    assertNotDefaultState(printer, withRecordDefault, withProject = false, defaultProject = false)
  }

  @Test
  fun `record not default application component with disabled default recording`() {
    val withRecordDefault = false
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  @Test
  fun `record not default component with enabled default recording`() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)
    assertNotDefaultState(printer, withRecordDefault = true, withProject = true, defaultProject = false)
  }

  @Test
  fun `record not default component with disabled default recording`() {
    val withRecordDefault = false
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  @Test
  fun `record partially not default multi component with enabled default recording`() {
    val withRecordDefault = true
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
    assertDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", true,
                       SettingsFields.Companion.Types.BOOl, withProject, defaultProject)
  }

  @Test
  fun `record partially not default multi component with disabled default recording`() {
    val withRecordDefault = false
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = true))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  @Suppress("SameParameterValue")
  @Test
  fun `record not default multi component with enabled default recording`() {
    val withRecordDefault = true
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = false))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(true)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(2)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", false,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  @Test
  fun `record not default multi component with disabled default recording`() {
    val withRecordDefault = false
    val component = TestComponent()
    component.loadState(MultiComponentState(bool = true, secondBool = false))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, projectRule.project)

    val withProject = true
    val defaultProject = false
    assertThat(printer.result).hasSize(3)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("boolOption"), "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("secondBoolOption"), "secondBoolOption", false,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  @Test
  fun `record default numerical fields in application component`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithNumerical())
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(1, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
  }

  @Test
  fun `record not default numerical fields in application component`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithNumerical(intOpt = 10, longOpt = 15, floatOpt = 5.5F, doubleOpt = 3.4))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(5, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("integerOption"), "integerOption", null,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("longOption"), "longOption", null,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("floatOption"), "floatOption", null,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("doubleOption"), "doubleOption", null,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
  }

  @Test
  fun `record not default numerical fields with absolute value in application component`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithNumerical(absIntOpt = 10, absLongOpt = 15, absFloatOpt = 5.5F, absDoubleOpt = 3.4))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(5, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absIntegerOption"), "absIntegerOption", 10,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absLongOption"), "absLongOption", 15L,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absFloatOption"), "absFloatOption", 5.5f,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absDoubleOption"), "absDoubleOption", 3.4,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
  }

  @Test
  fun `record all not default numerical fields with absolute value in application component`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithNumerical(absIntOpt = 10, absLongOpt = 15, absFloatOpt = 5.5F, absDoubleOpt = 3.4))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(5, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absIntegerOption"), "absIntegerOption", 10,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absLongOption"), "absLongOption", 15L,
                          SettingsFields.Companion.Types.INT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absFloatOption"), "absFloatOption", 5.5f,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absDoubleOption"), "absDoubleOption", 3.4,
                          SettingsFields.Companion.Types.FLOAT, false, withProject, defaultProject)
  }

  @Test
  fun `record enum fields`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithEnum(ComponentStateWithEnum.EnumOption.BAR, ComponentStateWithEnum.EnumOption.BAR))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(3, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("enumOption"), "enumOption", null,
                          SettingsFields.Companion.Types.ENUM, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absEnumOption"), "absEnumOption", ComponentStateWithEnum.EnumOption.BAR.name,
                          SettingsFields.Companion.Types.ENUM, false, withProject, defaultProject)
  }

  @Test
  fun `record string field`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithString("notDefault", "predefined"))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(3, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("stringOption"), "stringOption", null,
                          SettingsFields.Companion.Types.STRING, false, withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absStringOption"), "absStringOption", "predefined",
                          SettingsFields.Companion.Types.STRING, false, withProject, defaultProject)
  }

  @Test
  fun `record only predefined strings`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithString(absStringOpt = "notPredefined"))
    val spec = getStateSpec(component)
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(spec.name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(2, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absStringOption"), "absStringOption", null,
                          SettingsFields.Companion.Types.STRING, false, withProject, defaultProject)
  }

  @Test
  fun `not record string field without possible values`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithString(absStringOptWithoutPossibleValues = "notPredefined"))
    val recordDefault = false
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val withProject = false
    val defaultProject = false
    Assert.assertEquals(2, printer.result.size)
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    assertNotDefaultState(printer.getOptionByName("absStringOptionWithoutPossibleValues"), "absStringOptionWithoutPossibleValues", null,
                          SettingsFields.Companion.Types.STRING, recordDefault, withProject, defaultProject)
  }

  @Test
  fun `same project hash in invoke and state action`() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val printer = TestFeatureUsageSettingsEventsPrinter(false)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)
    assertThat(printer.getInvokedEvent().project).isEqualTo(printer.getOptionByName("boolOption").project)
  }

  @Test
  fun `log changed to default setting`() {
    val component = TestComponent()
    component.loadState(MultiComponentState())
    val printer = TestFeatureUsageSettingsChangedPrinter(false)
    val state = getStateSpec(component)
    printer.logConfigurationStateChanged(state.name, component.state, projectRule.project)

    assertThat(printer.result).hasSize(1)
    validateChangedComponent(state, printer.result.first())
  }

  private fun validateChangedComponent(state: State, event: Pair<(Project?, List<EventPair<*>>) -> Unit, MutableList<EventPair<*>>>) {
    val (eventFunction, usageData) = event
    assertThat(eventFunction.toString()).isEqualTo(SettingsChangesCollector::logComponentChanged.toString())

    val componentField = usageData.stream().filter { it.field == SettingsFields.COMPONENT_FIELD }.findFirst()
    assertThat(componentField.isPresent).isEqualTo(true)
    assertThat(componentField.get().data).isEqualTo(state.name)

    assertThat(usageData.stream().filter {
      it.field != SettingsFields.COMPONENT_FIELD &&
      it.field != SettingsFields.PLUGIN_INFO_FIELD &&
      it.field.name != "project"
    }.count()).isEqualTo(0)
  }

  @Test
  fun `log changed to not default settings`() {
    val component = TestComponent()
    component.loadState(MultiComponentState(secondBool = false))
    val printer = TestFeatureUsageSettingsChangedPrinter(false)
    val state = getStateSpec(component)
    printer.logConfigurationStateChanged(state.name, component.state, projectRule.project)

    assertThat(printer.result).hasSize(2)
    val (eventFunction, usageData) = printer.result[0]

    assertThat(eventFunction.toString()).isEqualTo(SettingsChangesCollector::logComponentChangedOption.toString())

    val componentField = usageData.stream().filter { it.field == SettingsFields.COMPONENT_FIELD }.findFirst()
    assertThat(componentField.isPresent).isEqualTo(true)
    assertThat(componentField.get().data).isEqualTo(state.name)

    val typeField = usageData.stream().filter { it.field == SettingsFields.TYPE_FIELD }.findFirst()
    assertThat(typeField.isPresent).isEqualTo(true)
    assertThat(typeField.get().data).isEqualTo(SettingsFields.Companion.Types.BOOl)

    val nameField = usageData.stream().filter { it.field == SettingsFields.NAME_FIELD }.findFirst()
    assertThat(nameField.isPresent).isEqualTo(true)
    assertThat(nameField.get().data).isEqualTo("secondBoolOption")

    assertThat(usageData.stream().filter {
      it.field != SettingsFields.COMPONENT_FIELD &&
      it.field != SettingsFields.PLUGIN_INFO_FIELD &&
      it.field != SettingsFields.VALUE_FIELD &&
      it.field != SettingsFields.TYPE_FIELD &&
      it.field != SettingsFields.NAME_FIELD &&
      it.field.name != "project"
    }.count()).isEqualTo(0)

    validateChangedComponent(state, printer.result[1])
  }

  @Test
  fun `not report disabled fields`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithDisabledField(true))
    val recordDefault = false
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val withProject = false
    val defaultProject = false
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    Assert.assertEquals(1, printer.result.size)
  }

  @Test
  fun `not report collections`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithCollections(listOf("foo"), hashMapOf("foo" to "bar"), setOf("bar")))
    val recordDefault = false
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val withProject = false
    val defaultProject = false
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    Assert.assertEquals(1, printer.result.size)
    assertThat(isComponentOptionNameWhitelisted("setOption")).isFalse()
    assertThat(isComponentOptionNameWhitelisted("mapOption")).isFalse()
    assertThat(isComponentOptionNameWhitelisted("listOption")).isFalse()
  }

  @Test
  fun `not report transient field`() {
    val component = TestComponent()
    component.loadState(ComponentStateWithTransientField("bar"))
    val recordDefault = false
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val withProject = false
    val defaultProject = false
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    Assert.assertEquals(1, printer.result.size)
    assertThat(isComponentOptionNameWhitelisted("stringOption")).isFalse()
  }

  @Test
  fun `ignore state presentableName`() {
    val component = TestComponentWithPresentableName()
    component.loadState(ComponentState())
    val recordDefault = false
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val withProject = false
    val defaultProject = false
    assertInvokedRecorded(printer.getInvokedEvent(), withProject, defaultProject)
    Assert.assertEquals(1, printer.result.size)
  }

  @Test
  fun `report invoked and options events with the same id`() {
    val component = TestComponent()
    component.loadState(ComponentState(bool = true))
    val printer = TestFeatureUsageSettingsEventsPrinter(recordDefault = false)
    printer.logConfigurationState(getStateSpec(component).name, component.state, null)

    val invokedEvent = printer.getInvokedEvent()
    val optionEvent = printer.getOptionByName("boolOption")
    Assert.assertEquals(invokedEvent.id, optionEvent.id)
  }

  private fun assertDefaultWithoutDefaultRecording(printer: TestFeatureUsageSettingsEventsPrinter,
                                                   withProject: Boolean,
                                                   defaultProject: Boolean) {
    assertThat(printer.result).hasSize(1)
    assertInvokedRecorded(printer.result[0], withProject, defaultProject)
  }

  @Suppress("SameParameterValue")
  private fun assertNotDefaultState(printer: TestFeatureUsageSettingsEventsPrinter,
                                    withRecordDefault: Boolean,
                                    withProject: Boolean,
                                    defaultProject: Boolean) {
    assertThat(printer.result).hasSize(1)
    assertNotDefaultState(printer.result[0], "boolOption", true,
                          SettingsFields.Companion.Types.BOOl, withRecordDefault, withProject, defaultProject)
  }

  private fun assertNotDefaultState(event: LoggedComponentStateEvents,
                                    name: String,
                                    value: Any?,
                                    type: SettingsFields.Companion.Types,
                                    withDefaultRecorded: Boolean,
                                    withProject: Boolean,
                                    defaultProject: Boolean) {

    val eventFunction = if (withDefaultRecorded) SettingsCollector::logOption
    else SettingsCollector::logNotDefault

    checkEventData(event, eventFunction, name, value, type, withDefaultRecorded, withProject, defaultProject, 3)
  }

  private fun assertDefaultState(printer: TestFeatureUsageSettingsEventsPrinter, withProject: Boolean, defaultProject: Boolean) {
    assertThat(printer.result).hasSize(1)
    assertDefaultState(printer.result[0], "boolOption", false,
                       SettingsFields.Companion.Types.BOOl, withProject, defaultProject)
  }

  private fun assertDefaultState(event: LoggedComponentStateEvents,
                                 name: String,
                                 value: Any,
                                 type: SettingsFields.Companion.Types,
                                    withProject: Boolean,
                                    defaultProject: Boolean) {
    checkEventData(event, SettingsCollector::logOption, name, value, type, null, withProject, defaultProject, 5)

    val defaultField = event.data.stream().filter { it.field == SettingsFields.DEFAULT_FIELD }.findFirst()
    assertThat(defaultField.isPresent).isEqualTo(true)
    assertThat(defaultField.get().data).isEqualTo(true)
  }

  private fun assertInvokedRecorded(event: LoggedComponentStateEvents,
                                    withProject: Boolean,
                                    defaultProject: Boolean) {
    checkEventData(event, SettingsCollector::logInvoked, null, null,
                   null, null, withProject, defaultProject, 1)
  }

  private fun checkEventData(event: LoggedComponentStateEvents,
                             @NonNls eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                             name: String?,
                             value: Any?,
                             type: SettingsFields.Companion.Types?,
                             withDefaultRecorded: Boolean?,
                             withProject: Boolean,
                             defaultProject: Boolean,
                             startSize: Int) {
    assertThat(event.eventFunction.toString()).isEqualTo(eventFunction.toString())
    assertThat(event.id).isNotNull()

    var size = startSize
    if (defaultProject) size++
    if (withDefaultRecorded != null && withDefaultRecorded) size++
    if (withDefaultRecorded != null && value != null) size++
    if (event.data.stream().filter { it.field == SettingsFields.PLUGIN_INFO_FIELD }.count() > 0) size++
    if (event.data.stream().filter { it.field.name == "plugin_version" }.count() > 0) size++
    if (event.data.stream().filter { it.field.name == "plugin" }.count() > 0) size++
    assertThat(event.data).hasSize(size)

    if (withProject) {
      assertThat(event.project).isNotNull
    }

    if (defaultProject) {
      val defaultProjectField = event.data.stream().filter { it.field == SettingsFields.DEFAULT_PROJECT_FIELD }
        .findFirst()
      assertThat(defaultProjectField.isPresent).isEqualTo(true)
      assertThat(defaultProjectField.get().data).isEqualTo(true)
    }

    val componentField = event.data.stream().filter { it.field == SettingsFields.COMPONENT_FIELD }.findFirst()
    assertThat(componentField.isPresent).isEqualTo(true)
    assertThat(componentField.get().data).isEqualTo("MyTestComponent")

    val pluginInfoField = event.data.stream().filter { it.field == SettingsFields.PLUGIN_INFO_FIELD }.findFirst()
    assertThat(pluginInfoField.isPresent).isEqualTo(true)
    assertThat(pluginInfoField.get().data).isNotNull

    if (name != null) {
    val nameField = event.data.stream().filter { it.field == SettingsFields.NAME_FIELD }.findFirst()
    assertThat(nameField.isPresent).isEqualTo(true)
    assertThat(nameField.get().data).isEqualTo(name)
    }

    if (value != null) {
      val valueField = event.data.stream().filter { it.field == SettingsFields.VALUE_FIELD }.findFirst()
      assertThat(valueField.isPresent).isEqualTo(true)
      assertThat(valueField.get().data.toString()).isEqualTo(value.toString())
    }

    if (type != null) {
      val typeField = event.data.stream().filter { it.field == SettingsFields.TYPE_FIELD }.findFirst()
      assertThat(typeField.isPresent).isEqualTo(true)
      assertThat(typeField.get().data).isEqualTo(type)
    }

    if ((withDefaultRecorded != null) && withDefaultRecorded) {
      val defaultField = event.data.stream().filter { it.field == SettingsFields.DEFAULT_FIELD }.findFirst()
      assertThat(defaultField.isPresent).isEqualTo(true)
      assertThat(defaultField.get().data).isEqualTo(false)
    }
  }

  private class TestFeatureUsageSettingsEventsPrinter(recordDefault: Boolean) : FeatureUsageSettingsEventPrinter(recordDefault) {
    val result: MutableList<LoggedComponentStateEvents> = ArrayList()

    override fun logConfig(@NonNls eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                           project: Project?,
                           data: MutableList<EventPair<*>>,
                           id: Int) {
      result.add(LoggedComponentStateEvents(eventFunction, project, data, id))
    }

    fun getOptionByName(name: String): LoggedComponentStateEvents {
      for (event in result) {
        val nameField = event.data.stream().filter { it.field == SettingsFields.NAME_FIELD }.findFirst()
        if (nameField.isPresent && nameField.get().data == name) {
          return event
        }
      }
      throw RuntimeException("Failed to find event")
    }

    fun getInvokedEvent(): LoggedComponentStateEvents {
      for (event in result) {
        if (event.eventFunction == SettingsCollector::logInvoked) {
          return event
        }
      }
      throw RuntimeException("Failed to find event")
    }
  }

  private class TestFeatureUsageSettingsChangedPrinter(recordDefault: Boolean) : FeatureUsageSettingsEventPrinter(recordDefault) {
    val result: MutableList<Pair<(Project?, List<EventPair<*>>) -> Unit, MutableList<EventPair<*>>>> = ArrayList()
    override fun logSettingsChanged(@NonNls eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                                    project: Project?,
                                    data: MutableList<EventPair<*>>,
                                    id: Int) {
      result.add(Pair(eventFunction, data))
    }
  }

  private class LoggedComponentStateEvents(val eventFunction: (Project?, List<EventPair<*>>) -> Unit,
                                           val project: Project?,
                                           val data: MutableList<EventPair<*>>,
                                           val id: Int)

  @State(name = "MyTestComponent", reportStatistic = true)
  private class TestComponent : PersistentStateComponent<ComponentState> {
    private var state = ComponentState()

    override fun loadState(s: ComponentState) {
      state = s
    }

    override fun getState() = state
  }

  @Suppress("unused")
  private open class ComponentState(bool: Boolean = false, list: List<Int> = ArrayList()) {
    @Attribute("bool-value")
    val boolOption: Boolean = bool

    @Attribute("int-values")
    val intOption: List<Int> = list
  }

  @Suppress("unused")
  private class MultiComponentState(bool: Boolean = false,
                                    secondBool: Boolean = true,
                                    list: List<Int> = ArrayList()) : ComponentState(bool, list) {
    @Attribute("second-bool-value")
    val secondBoolOption: Boolean = secondBool
  }

  @Suppress("unused")
  private class ComponentStateWithNumerical(intOpt: Int = 0,
                                            longOpt: Long = 0,
                                            floatOpt: Float = 0.0F,
                                            doubleOpt: Double = 0.0,
                                            absIntOpt: Int = 0,
                                            absLongOpt: Long = 0,
                                            absFloatOpt: Float = 0.0F,
                                            absDoubleOpt: Double = 0.0,
                                            bool: Boolean = false,
                                            list: List<Int> = ArrayList()) : ComponentState(bool, list) {
    @Attribute("int-option")
    val integerOption: Int = intOpt

    @Attribute("long-option")
    val longOption: Long = longOpt

    @Attribute("float-option")
    val floatOption: Float = floatOpt

    @Attribute("double-option")
    val doubleOption: Double = doubleOpt

    @Attribute("abs-int-option")
    @field:ReportValue
    val absIntegerOption: Int = absIntOpt

    @Attribute("abs-long-option")
    @field:ReportValue
    val absLongOption: Long = absLongOpt

    @Attribute("abs-float-option")
    @field:ReportValue
    val absFloatOption: Float = absFloatOpt

    @Attribute("abs-double-option")
    @field:ReportValue
    val absDoubleOption: Double = absDoubleOpt
  }

  @Suppress("unused")
  private class ComponentStateWithEnum(enumOpt: EnumOption = EnumOption.FOO,
                                       absEnumOpt: EnumOption = EnumOption.FOO) : ComponentState() {
    @Attribute("enum-option")
    val enumOption: EnumOption = enumOpt

    @Attribute("abs-enum-option")
    @field:ReportValue
    val absEnumOption: EnumOption = absEnumOpt

    enum class EnumOption {
      FOO, BAR
    }
  }

  @Suppress("unused")
  private class ComponentStateWithString(stringOpt: String = "test",
                                         absStringOpt: String = "test",
                                         absStringOptWithoutPossibleValues: String = "test") : ComponentState() {
    @Attribute("string-option")
    val stringOption: String = stringOpt

    @Attribute("abs-string-option")
    @field:ReportValue(possibleValues = ["predefined"])
    val absStringOption: String = absStringOpt

    @Attribute("abs-string-option-without-possible-values")
    @field:ReportValue
    val absStringOptionWithoutPossibleValues: String = absStringOptWithoutPossibleValues
  }

  @Suppress("unused")
  private class ComponentStateWithDisabledField(bool: Boolean = false) : ComponentState() {
    @SkipReportingStatistics
    @Attribute("disabled-option")
    val disabledField: Boolean = bool
  }

  @Suppress("unused")
  private class ComponentStateWithCollections(list: List<String> = listOf(),
                                              map: Map<String, String> = hashMapOf(),
                                              set: Set<String> = hashSetOf()) : ComponentState() {
    @Attribute("list-option")
    val listOption: List<String> = list

    @Attribute("map-option")
    val mapOption: Map<String, String> = map

    @Attribute("set-option")
    val setOption: Set<String> = set
  }

  @Suppress("unused")
  private class ComponentStateWithTransientField(stringOpt: String = "foo") : ComponentState() {
    @Transient
    val stringOption: String = stringOpt
  }

  @State(name = "MyTestComponent", presentableName = TestComponentWithPresentableName.PresentableNameGetter::class)
  private class TestComponentWithPresentableName : PersistentStateComponent<ComponentState> {
    private var state = ComponentState()

    override fun loadState(s: ComponentState) {
      state = s
    }

    override fun getState() = state

    class PresentableNameGetter : State.NameGetter() {
      override fun get(): String {
        return "PresentableName"
      }
    }
  }
}
