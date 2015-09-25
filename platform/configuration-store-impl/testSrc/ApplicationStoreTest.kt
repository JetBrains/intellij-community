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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.*
import com.intellij.util.SmartList
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.serialize
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

internal class ApplicationStoreTest {
  companion object {
    @ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()
  @Rule fun getTemporaryFolder() = tempDirManager

  private val edtRule = EdtRule()
  @Rule fun _edtRule() = edtRule

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  @Before fun setUp() {
    testAppConfig = tempDirManager.newPath(refreshVfs = false)
    componentStore = MyComponentStore(testAppConfig.systemIndependentPath)
  }

  @Test fun `stream provider save if several storages configured`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.storageManager.streamProvider = streamProvider

    componentStore.initComponent(component, false)
    component.foo = "newValue"
    componentStore.save(SmartList())

    assertThat(streamProvider.data.get(RoamingType.DEFAULT)!!.get("new.xml")).isEqualTo("<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
  }

  @Test fun `load from stream provider`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = THashMap<String, String>()
    val fileSpec = "new.xml"
    map.put(fileSpec, "<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
    streamProvider.data.put(RoamingType.DEFAULT, map)

    componentStore.storageManager.streamProvider = streamProvider
    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("newValue")

    assertThat(Paths.get(componentStore.storageManager.expandMacros(fileSpec))).isRegularFile()
  }

  @Test fun `remove deprecated storage on write`() {
    doRemoveDeprecatedStorageOnWrite(SeveralStoragesConfigured())
  }

  @Test fun `remove deprecated storage on write 2`() {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private fun doRemoveDeprecatedStorageOnWrite(component: Foo) {
    val oldFile = writeConfig("old.xml", "<application>${createComponentData("old")}</application>")
    writeConfig("new.xml", "<application>${createComponentData("new")}</application>")

    testAppConfig.refreshVfs()

    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("new")

    component.foo = "new2"
    saveStore()

    assertThat(oldFile).doesNotExist()
  }

  private fun createComponentData(foo: String) = """<component name="A" foo="$foo" />"""

  @Test fun `remove data from deprecated storage if another component data exists`() {
    val data = createComponentData("new")
    val oldFile = writeConfig("old.xml", """<application>
    <component name="OtherComponent" foo="old" />
    ${createComponentData("old")}
    </application>""")
   writeConfig("new.xml", "<application>$data</application>")

    testAppConfig.refreshVfs()

    val component = SeveralStoragesConfigured()
    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("new")

    saveStore()

    assertThat(oldFile).hasContent("""<application>
  <component name="OtherComponent" foo="old" />
</application>""")
  }

  @State(name = "A", storages = arrayOf(Storage(file = "a.xml")))
  private open class A : PersistentStateComponent<Element> {
    data class State(@Attribute var foo: String = "", @Attribute var bar: String = "")

    var state = State()

    override fun getState() = state.serialize()

    override fun loadState(state: Element) {
      this.state = XmlSerializer.deserialize(state, State::class.java)!!
    }
  }

  @Test fun `don't save if only format is changed`() {
    val oldContent = "<application><component name=\"A\" foo=\"old\" deprecated=\"old\"/></application>"
    val file = writeConfig("a.xml", oldContent)
    val oldModificationTime = file.getLastModifiedTime()
    testAppConfig.refreshVfs()

    val component = A()
    componentStore.initComponent(component, false)
    assertThat(component.state).isEqualTo(A.State("old"))

    saveStore()

    assertThat(file).hasContent(oldContent)
    assertThat(oldModificationTime).isEqualTo(file.getLastModifiedTime())

    component.state.bar = "2"
    component.state.foo = "1"
    saveStore()

    assertThat(file).hasContent("<application>\n  <component name=\"A\" foo=\"1\" bar=\"2\" />\n</application>")
  }

  @Test fun `do not check if only format changed for non-roamable storage`() {
    @State(name = "A", storages = arrayOf(Storage(file = "b.xml", roamingType = RoamingType.DISABLED)))
    class AWorkspace : A()

    val oldContent = "<application><component name=\"A\" foo=\"old\" deprecated=\"old\"/></application>"
    val file = writeConfig("b.xml", oldContent)
    testAppConfig.refreshVfs()

    val component = AWorkspace()
    componentStore.initComponent(component, false)
    assertThat(component.state).isEqualTo(A.State("old"))

    saveStore()

    assertThat(file).hasContent("<application>\n  <component name=\"A\" foo=\"old\" />\n</application>")
  }

  private fun saveStore() {
    runInEdtAndWait { componentStore.save(SmartList()) }
  }

  private fun writeConfig(fileName: String, @Language("XML") data: String) = testAppConfig.writeChild(fileName, data)

  private class MyStreamProvider : StreamProvider {
    override fun processChildren(path: String, roamingType: RoamingType, filter: (String) -> Boolean, processor: (String, InputStream, Boolean) -> Boolean) {
    }

    public val data: MutableMap<RoamingType, MutableMap<String, String>> = THashMap()

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      getMap(roamingType).put(fileSpec, String(content, 0, size, CharsetToolkit.UTF8_CHARSET))
    }

    private fun getMap(roamingType: RoamingType): MutableMap<String, String> {
      var map = data.get(roamingType)
      if (map == null) {
        map = THashMap<String, String>()
        data.put(roamingType, map)
      }
      return map
    }

    override fun read(fileSpec: String, roamingType: RoamingType): InputStream? {
      val data = getMap(roamingType).get(fileSpec) ?: return null
      return ByteArrayInputStream(data.toByteArray())
    }

    override fun delete(fileSpec: String, roamingType: RoamingType) {
      data.get(roamingType)?.remove(fileSpec)
    }
  }

  class MyComponentStore(testAppConfigPath: String) : ComponentStoreImpl() {
    override val storageManager = ApplicationStorageManager(ApplicationManager.getApplication())

    init {
      setPath(testAppConfigPath)
    }

    override fun setPath(path: String) {
      storageManager.addMacro(StoragePathMacros.APP_CONFIG, path)
    }
  }

  abstract class Foo {
    @Attribute
    var foo = "defaultValue"
  }

  @State(name = "A", storages = arrayOf(Storage(file = "new.xml"), Storage(file = StoragePathMacros.APP_CONFIG + "/old.xml", deprecated = true)))
  class SeveralStoragesConfigured : Foo(), PersistentStateComponent<SeveralStoragesConfigured> {
    override fun getState(): SeveralStoragesConfigured? {
      return this
    }

    override fun loadState(state: SeveralStoragesConfigured) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }

  @State(name = "A", storages = arrayOf(Storage(file = "old.xml", deprecated = true), Storage(file = "${StoragePathMacros.APP_CONFIG}/new.xml")))
  class ActualStorageLast : Foo(), PersistentStateComponent<ActualStorageLast> {
    override fun getState() = this

    override fun loadState(state: ActualStorageLast) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}