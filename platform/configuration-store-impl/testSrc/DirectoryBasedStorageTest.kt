/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.idea.Bombed
import com.intellij.openapi.components.MainConfigurationStateSplitter
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.impl.stores.StateStorageBase
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.hasChildren
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.*

private fun StateStorage.ExternalizationSession.save() {
  runInEdtAndWait {
    createSaveSession()!!.save()
  }
}

private fun StateStorageBase<*>.setStateAndSave(componentName: String, state: String?) {
  var externalizationSession = startExternalization()!!
  externalizationSession.setState(null, componentName, if (state == null) Element("state") else JDOMUtil.load(state.reader()))
  externalizationSession.save()
}

internal class TestStateSplitter : MainConfigurationStateSplitter() {
  override fun getComponentStateFileName() = "main"

  override fun getSubStateTagName() = "sub"

  override fun getSubStateFileName(element: Element) = element.getAttributeValue("name")
}

@Bombed(user = "vladimir.krivosheev", year = 2016, month = Calendar.DECEMBER, day = 10)
internal class DirectoryBasedStorageTest {
  companion object {
    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  val tempDirManager = TemporaryDirectory()

  private val ruleChain = RuleChain(tempDirManager)
  @Rule fun getChain() = ruleChain

  @Test fun save() {
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