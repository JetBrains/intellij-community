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
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.properties.Delegates

class ApplicationStoreTest {
  companion object {
    ClassRule val projectRule = ProjectRule()
  }

  private val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  private val edtRule = EdtRule()
  public Rule fun _edtRule(): EdtRule = edtRule

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  public Before fun setUp() {
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

    assertThat(streamProvider.data.get(RoamingType.DEFAULT)!!.get("proxy.settings.xml")).isEqualTo("<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"newValue\" />\n" + "  </component>\n" + "</application>")
  }

  @Test fun testLoadFromStreamProvider() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = THashMap<String, String>()
    val fileSpec = "proxy.settings.xml"
    map.put(fileSpec, "<application>\n  <component name=\"HttpConfigurable\">\n    <option name=\"foo\" value=\"newValue\" />\n  </component>\n</application>")
    streamProvider.data.put(RoamingType.DEFAULT, map)

    componentStore.storageManager.streamProvider = streamProvider
    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("newValue")

    assertThat(Paths.get(componentStore.storageManager.fileSpecToPath(fileSpec))).isRegularFile()
  }

  @Test fun `remove deprecated storage on write`() {
    doRemoveDeprecatedStorageOnWrite(SeveralStoragesConfigured())
  }

  @Test fun `remove deprecated storage on write 2`() {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private fun doRemoveDeprecatedStorageOnWrite(component: Foo) {
    val oldFile = writeConfig("other.xml", "<application><component name=\"HttpConfigurable\"><option name=\"foo\" value=\"old\" /></component></application>")
    writeConfig("proxy.settings.xml", "<application><component name=\"HttpConfigurable\"><option name=\"foo\" value=\"new\" /></component></application>")

    testAppConfig.refreshVfs()

    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("new")

    component.foo = "new2"
    runInEdtAndWait { componentStore.save(SmartList()) }

    assertThat(oldFile).doesNotExist()
  }

  private fun writeConfig(fileName: String, Language("XML") data: String) = testAppConfig.writeChild(fileName, data)

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
    public var foo: String = "defaultValue"
  }

  @State(name = "HttpConfigurable", storages = arrayOf(Storage(file = "proxy.settings.xml"), Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)))
  class SeveralStoragesConfigured : Foo(), PersistentStateComponent<SeveralStoragesConfigured> {
    override fun getState(): SeveralStoragesConfigured? {
      return this
    }

    override fun loadState(state: SeveralStoragesConfigured) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }

  @State(name = "HttpConfigurable", storages = arrayOf(Storage(file = "other.xml", deprecated = true), Storage(file = "${StoragePathMacros.APP_CONFIG}/proxy.settings.xml")))
  class ActualStorageLast : Foo(), PersistentStateComponent<ActualStorageLast> {
    override fun getState() = this

    override fun loadState(state: ActualStorageLast) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}