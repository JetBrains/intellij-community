// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.execution

import com.intellij.openapi.externalSystem.serialization.service.execution.ExternalSystemTaskExecutionSettingsState
import com.intellij.openapi.externalSystem.service.isSameSettings
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertNotEquals

class ExternalSystemTaskExecutionSettingsTest : UsefulTestCase() {
  fun `test backward compatibility`() {
    val settings1 = ExternalSystemTaskExecutionSettings().apply {
      val taskNames = taskNames
      taskNames.add(":module:cleanTest")
      taskNames.add(":module:test")
      scriptParameters = "--tests \"TestCase\" --continue"
    }
    val settings2 = ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
      addUnorderedArgument("--continue")
    }
    assertEquals(settings1.taskNames, settings2.taskNames)
    assertEquals(settings1, settings2)
    assertEquals(settings1.hashCode(), settings2.hashCode())
    assertTrue(settings1.isSameSettings(settings2))
    val settings3 = ExternalSystemTaskExecutionSettings()
    settings3.setFrom(settings2)
    settings3.addTaskSettings(TaskSettingsImpl(":module:build", listOf("-x test")))
    assertEquals(settings1, settings2)
    assertEquals(settings1.hashCode(), settings2.hashCode())
    assertTrue(settings1.isSameSettings(settings2))
    assertNotEquals(settings3, settings2)
    assertFalse(settings3.isSameSettings(settings2))
  }

  fun `test command line representation`() {
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
    }.run {
      assertEquals(":module:cleanTest :module:test --tests \"TestCase\"", toString())
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":module:build", listOf("-x test")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
    }.run {
      assertEquals(":module:build -x test :module:cleanTest :module:test --tests \"TestCase\"", toString())
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test\"")))
      addUnorderedArgument("--continue")
    }.run {
      assertEquals(":cleanTest :test --tests \"Test\" :module:cleanTest :module:test --tests \"Test\" --continue", toString())
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test1\"", "--tests \"Test2\"", "--tests \"Test3\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test4\"", "--tests \"Test5\"")))
      addUnorderedArgument("--continue")
    }.run {
      assertEquals(":cleanTest :test --tests \"Test1\" --tests \"Test2\" --tests \"Test3\" " +
                   ":module:cleanTest :module:test --tests \"Test4\" --tests \"Test5\" " +
                   "--continue", toString())
    }
  }

  fun `test ui representation backward compatibility`() {
    ExternalSystemTaskExecutionSettings().apply {
      val taskNames = taskNames
      taskNames.add(":module:cleanTest")
      taskNames.add(":module:test")
      scriptParameters = "--tests \"TestCase\""
    }.run {
      assertEquals(listOf(":module:cleanTest", ":module:test"), taskNames)
      assertEquals("--tests \"TestCase\"", scriptParameters)
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
    }.run {
      assertEquals(listOf(":module:cleanTest", ":module:test"), taskNames)
      assertEquals("--tests \"TestCase\"", scriptParameters)
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":module:build", listOf("-x test")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
    }.run {
      assertEquals(emptyList<String>(), taskNames)
      assertEquals(":module:build -x test :module:cleanTest :module:test --tests \"TestCase\"", scriptParameters)
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test\"")))
      addUnorderedArgument("--continue")
    }.run {
      assertEquals(emptyList<String>(), taskNames)
      assertEquals(":cleanTest :test --tests \"Test\" :module:cleanTest :module:test --tests \"Test\" --continue", scriptParameters)
    }
    ExternalSystemTaskExecutionSettings().apply {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test1\"", "--tests \"Test2\"", "--tests \"Test3\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test4\"", "--tests \"Test5\"")))
      addUnorderedArgument("--continue")
    }.run {
      assertEquals(emptyList<String>(), taskNames)
      assertEquals(":cleanTest :test --tests \"Test1\" --tests \"Test2\" --tests \"Test3\" " +
                   ":module:cleanTest :module:test --tests \"Test4\" --tests \"Test5\" " +
                   "--continue", scriptParameters)
    }
  }

  fun `test settings serialization to xml`() {
    assertExternalSystemTaskExecutionSettings("""
      <ExternalSystemSettings>
        <option name="executionName" />
        <option name="externalProjectPath" />
        <option name="externalSystemIdString" />
        <option name="scriptParameters" />
        <option name="taskDescriptions">
          <list />
        </option>
        <option name="taskNames">
          <list />
        </option>
        <option name="tasksSettings">
          <list>
            <TaskSettings>
              <option name="arguments">
                <list />
              </option>
              <option name="description" />
              <option name="name" value=":cleanTest" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list>
                  <option value="--tests &quot;Test&quot;" />
                </list>
              </option>
              <option name="description" />
              <option name="name" value=":test" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list />
              </option>
              <option name="description" />
              <option name="name" value=":module:cleanTest" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list>
                  <option value="--tests &quot;Test&quot;" />
                </list>
              </option>
              <option name="description" />
              <option name="name" value=":module:test" />
            </TaskSettings>
          </list>
        </option>
        <option name="unorderedArguments">
          <set>
            <option value="--continue" />
          </set>
        </option>
        <option name="vmOptions" />
      </ExternalSystemSettings>
    """.trimIndent()) {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test\"")))
      addUnorderedArgument("--continue")
    }
    assertExternalSystemTaskExecutionSettings("""
      <ExternalSystemSettings>
        <option name="executionName" />
        <option name="externalProjectPath" />
        <option name="externalSystemIdString" />
        <option name="scriptParameters" />
        <option name="taskDescriptions">
          <list />
        </option>
        <option name="taskNames">
          <list />
        </option>
        <option name="tasksSettings">
          <list>
            <TaskSettings>
              <option name="arguments">
                <list />
              </option>
              <option name="description" />
              <option name="name" value=":cleanTest" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list>
                  <option value="--tests &quot;Test1&quot;" />
                  <option value="--tests &quot;Test2&quot;" />
                  <option value="--tests &quot;Test3&quot;" />
                </list>
              </option>
              <option name="description" />
              <option name="name" value=":test" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list />
              </option>
              <option name="description" />
              <option name="name" value=":module:cleanTest" />
            </TaskSettings>
            <TaskSettings>
              <option name="arguments">
                <list>
                  <option value="--tests &quot;Test4&quot;" />
                  <option value="--tests &quot;Test5&quot;" />
                </list>
              </option>
              <option name="description" />
              <option name="name" value=":module:test" />
            </TaskSettings>
          </list>
        </option>
        <option name="unorderedArguments">
          <set>
            <option value="--continue" />
          </set>
        </option>
        <option name="vmOptions" />
      </ExternalSystemSettings>
    """.trimIndent()) {
      addTaskSettings(TaskSettingsImpl(":cleanTest"))
      addTaskSettings(TaskSettingsImpl(":test", listOf("--tests \"Test1\"", "--tests \"Test2\"", "--tests \"Test3\"")))
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"Test4\"", "--tests \"Test5\"")))
      addUnorderedArgument("--continue")
    }
    assertExternalSystemTaskExecutionSettings("""
      <ExternalSystemSettings>
        <option name="executionName" />
        <option name="externalProjectPath" />
        <option name="externalSystemIdString" />
        <option name="scriptParameters" value="--tests &quot;TestCase&quot; --continue" />
        <option name="taskDescriptions">
          <list />
        </option>
        <option name="taskNames">
          <list>
            <option value=":module:cleanTest" />
            <option value=":module:test" />
          </list>
        </option>
        <option name="tasksSettings">
          <list />
        </option>
        <option name="unorderedArguments">
          <set />
        </option>
        <option name="vmOptions" />
      </ExternalSystemSettings>
    """.trimIndent()) {
      val taskNames = taskNames
      taskNames.add(":module:cleanTest")
      taskNames.add(":module:test")
      scriptParameters = "--tests \"TestCase\" --continue"
    }
    assertExternalSystemTaskExecutionSettings("""
      <ExternalSystemSettings>
        <option name="executionName" />
        <option name="externalProjectPath" />
        <option name="externalSystemIdString" />
        <option name="scriptParameters" />
        <option name="taskDescriptions">
          <list />
        </option>
        <option name="taskNames">
          <list>
            <option value=":module:cleanTest" />
          </list>
        </option>
        <option name="tasksSettings">
          <list>
            <TaskSettings>
              <option name="arguments">
                <list>
                  <option value="--tests &quot;TestCase&quot;" />
                </list>
              </option>
              <option name="description" />
              <option name="name" value=":module:test" />
            </TaskSettings>
          </list>
        </option>
        <option name="unorderedArguments">
          <set>
            <option value="--continue" />
          </set>
        </option>
        <option name="vmOptions" />
      </ExternalSystemSettings>
    """.trimIndent()) {
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test", listOf("--tests \"TestCase\"")))
      addUnorderedArgument("--continue")
    }
    assertExternalSystemTaskExecutionSettings("""
      <ExternalSystemSettings>
        <option name="executionName" />
        <option name="externalProjectPath" />
        <option name="externalSystemIdString" />
        <option name="scriptParameters" value="--tests &quot;TestCase&quot; --continue" />
        <option name="taskDescriptions">
          <list />
        </option>
        <option name="taskNames">
          <list>
            <option value=":module:cleanTest" />
            <option value=":module:test" />
          </list>
        </option>
        <option name="tasksSettings">
          <list />
        </option>
        <option name="unorderedArguments">
          <set />
        </option>
        <option name="vmOptions" />
      </ExternalSystemSettings>
    """.trimIndent()) {
      addTaskSettings(TaskSettingsImpl(":module:cleanTest"))
      addTaskSettings(TaskSettingsImpl(":module:test"))
      scriptParameters = "--tests \"TestCase\" --continue"
    }
  }

  private fun assertExternalSystemTaskExecutionSettings(xml: String, configuration: ExternalSystemTaskExecutionSettings.() -> Unit) {
    val settings = ExternalSystemTaskExecutionSettings().apply(configuration)
    val settingsState = ExternalSystemTaskExecutionSettingsState.valueOf(settings)
    val element = XmlSerializer.serialize(settingsState) { accessor, _ ->
      when (accessor.name) {
        "passParentEnvs" -> !settings.isPassParentEnvs
        "env" -> !settings.env.isEmpty()
        else -> true
      }
    }
    assertThat(element).isEqualTo(xml)
    val restoredSettingsState = XmlSerializer.deserialize(element, ExternalSystemTaskExecutionSettingsState::class.java)
    val restoredSettings = restoredSettingsState.toTaskExecutionSettings()
    assertEquals(settings.toString(), restoredSettings.toString())
    assertEquals(settings, restoredSettings)
    assertTrue(settings.isSameSettings(restoredSettings))
  }
}
