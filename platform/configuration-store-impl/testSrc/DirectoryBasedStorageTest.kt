// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

class DirectoryBasedStorageTest {
  companion object {
    @ClassRule @JvmField val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  @Rule @JvmField val ruleChain = RuleChain(tempDirManager)

  @Test
  fun readEmptyFile() {
    val dir = tempDirManager.newPath(refreshVfs = true)
    dir.createDirectories().resolve("empty.xml").writeBytes(ByteArray(0))
    DirectoryBasedStorage(dir, TestStateSplitter()).loadData()
  }

  @Test
  fun save() = runBlocking<Unit> {
    val dir = tempDirManager.newPath(refreshVfs = true)
    val storage = DirectoryBasedStorage(dir, TestStateSplitter())
    val componentName = "test"

    setStateAndSave(storage, componentName,"""<component name="$componentName"><sub name="foo" /><sub name="bar" /></component>""")
    assertThat(dir).hasChildren("foo.xml", "bar.xml", "main.xml")
    assertThat(dir.resolve("foo.xml")).hasContent(generateData("foo"))
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))

    setStateAndSave(storage, componentName, """<component name="$componentName"><sub name="bar" /></component>""")
    assertThat(dir).hasChildren("main.xml", "bar.xml")
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))

    setStateAndSave(storage, componentName, null)
    assertThat(dir).doesNotExist()
  }

  private fun generateData(name: String): String {
    return """
      <component name="test">
        <${if (name == "test") "component" else "sub"} name="$name" />
      </component>
    """.trimIndent()
  }

  private suspend fun setStateAndSave(storage: StateStorageBase<*>, componentName: String, state: String?) {
    val saveSessionProducer = storage.createSaveSessionProducer()!!
    val state = if (state == null) Element("state") else JDOMUtil.load(state)
    saveSessionProducer.setState(component = null, componentName, PluginManagerCore.CORE_ID, state)
    val saveSession = saveSessionProducer.createSaveSession()!!
    if (useBackgroundSave) {
      saveSession.save(events = null)
    }
    else {
      writeAction {
        saveSession.saveBlocking()
      }
    }
  }

  private class TestStateSplitter : MainConfigurationStateSplitter() {
    override fun getComponentStateFileName() = "main"

    override fun getSubStateTagName() = "sub"

    override fun getSubStateFileName(element: Element) = element.getAttributeValue("name")!!
  }
}
