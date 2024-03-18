// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.LocalFileSystem
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
    val dir = tempDirManager.newPath(refreshVfs = false)
    dir.createDirectories().resolve("empty.xml").writeBytes(ByteArray(0))
    DirectoryBasedStorage(dir, TestStateSplitter()).loadData()
  }

  @Test
  fun saveVFS() = save(useVfs = true)

  @Test
  fun saveNIO() = save(useVfs = false)

  private fun save(useVfs: Boolean) = runBlocking<Unit> {
    val dir = tempDirManager.newPath(refreshVfs = useVfs)
    val storage = DirectoryBasedStorage(dir, TestStateSplitter())
    val componentName = "test"

    setStateAndSave(storage, componentName,"""<component name="${componentName}"><sub name="foo"/><sub name="bar"/></component>""", useVfs)
    assertThat(dir).hasChildren("foo.xml", "bar.xml", "main.xml")
    assertThat(dir.resolve("foo.xml")).hasContent(generateData("foo"))
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))
    val vDir = LocalFileSystem.getInstance().findFileByNioFile(dir)!!
    assertThat(vDir.findChild("foo.xml")!!.contentsToByteArray()).asString(Charsets.UTF_8).isEqualTo(generateData("foo"))

    setStateAndSave(storage, componentName, """<component name="${componentName}"><sub name="bar"/></component>""", useVfs)
    assertThat(dir).hasChildren("main.xml", "bar.xml")
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))
    assertThat(vDir.findChild("foo.xml")).isNull()
    assertThat(vDir.findChild("bar.xml")!!.contentsToByteArray()).asString(Charsets.UTF_8).isEqualTo(generateData("bar"))

    setStateAndSave(storage, componentName, """<component name="${componentName}"><sub name="bar" extra="."/></component>""", useVfs)
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar", " extra=\".\""))
    assertThat(vDir.findChild("bar.xml")!!.contentsToByteArray()).asString(Charsets.UTF_8).isEqualTo(generateData("bar", " extra=\".\""))

    setStateAndSave(storage, componentName, state = null, useVfs)
    assertThat(dir).doesNotExist()
    assertThat(vDir.isValid).isFalse()
  }

  private suspend fun setStateAndSave(storage: StateStorageBase<*>, componentName: String, state: String?, useVfs: Boolean) {
    val sessionManager = SaveSessionProducerManager(useVfs, collectVfsEvents = true)
    val sessionProducer = sessionManager.getProducer(storage)!!
    val state = if (state == null) Element("state") else JDOMUtil.load(state)
    sessionProducer.setState(component = null, componentName, PluginManagerCore.CORE_ID, state)
    sessionManager.save(SaveResult())
  }

  private fun generateData(name: String, extra: String = ""): String = """
    <component name="test">
      <${if (name == "test") "component" else "sub"} name="${name}"${extra} />
    </component>""".trimIndent()

  private class TestStateSplitter : MainConfigurationStateSplitter() {
    override fun getComponentStateFileName() = "main"

    override fun getSubStateTagName() = "sub"

    override fun getSubStateFileName(element: Element) = element.getAttributeValue("name")!!
  }
}
