// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.runInEdtAndWait
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

private fun StateStorage.SaveSessionProducer.save() {
  runInEdtAndWait {
    createSaveSession()!!.save()
  }
}

private fun StateStorageBase<*>.setStateAndSave(componentName: String, state: String?) {
  val externalizationSession = createSaveSessionProducer()!!
  externalizationSession.setState(null, componentName, if (state == null) Element("state") else JDOMUtil.load(state))
  externalizationSession.save()
}

internal class TestStateSplitter : MainConfigurationStateSplitter() {
  override fun getComponentStateFileName() = "main"

  override fun getSubStateTagName() = "sub"

  override fun getSubStateFileName(element: Element) = element.getAttributeValue("name")
}

internal class DirectoryBasedStorageTest {
  companion object {
    @ClassRule
    @JvmField
    val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  @Rule
  @JvmField
  val ruleChain = RuleChain(tempDirManager)

  @Test
  fun save() {
    val dir = tempDirManager.newPath(refreshVfs = true)
    val storage = DirectoryBasedStorage(dir, TestStateSplitter())

    val componentName = "test"

    storage.setStateAndSave(componentName,"""<component name="$componentName"><sub name="foo" /><sub name="bar" /></component>""")

    assertThat(dir).hasChildren("foo.xml", "bar.xml", "main.xml")

    assertThat(dir.resolve("foo.xml")).hasContent(generateData("foo"))
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))

    storage.setStateAndSave(componentName, """<component name="$componentName"><sub name="bar" /></component>""")

    assertThat(dir).hasChildren("main.xml", "bar.xml")
    assertThat(dir.resolve("bar.xml")).hasContent(generateData("bar"))
    assertThat(dir.resolve("main.xml")).hasContent(generateData("test"))

    storage.setStateAndSave(componentName, null)
    assertThat(dir).doesNotExist()
  }

  private fun generateData(name: String): String {
    return """<component name="test">
  <${if (name == "test") "component" else "sub"} name="$name" />
</component>"""
  }
}