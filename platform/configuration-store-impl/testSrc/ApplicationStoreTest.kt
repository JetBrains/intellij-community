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

import com.intellij.application.options.PathMacrosImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.FixtureRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.exists
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlin.properties.Delegates

class ApplicationStoreTest {
  private val fixtureManager = FixtureRule()
  public Rule fun getFixtureManager(): FixtureRule = fixtureManager

  private val tempDirManager = TemporaryDirectory()
  public Rule fun getTemporaryFolder(): TemporaryDirectory = tempDirManager

  private var testAppConfig: File by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  public Before fun setUp() {
    testAppConfig = tempDirManager.newDirectory()
    componentStore = MyComponentStore(FileUtilRt.toSystemIndependentName(testAppConfig.systemIndependentPath))
  }

  public Test fun `stream provider save if several storages configured`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.getStateStorageManager().setStreamProvider(streamProvider)

    componentStore.initComponent(component, false)
    component.foo = "newValue"
    StoreUtil.save(componentStore, null)

    assertThat<String>(streamProvider.data.get(RoamingType.PER_USER)!!.get(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml"), equalTo("<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"newValue\" />\n" + "  </component>\n" + "</application>"))
  }

  public Test fun testLoadFromStreamProvider() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = THashMap<String, String>()
    map.put(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml", "<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"newValue\" />\n" + "  </component>\n" + "</application>")
    streamProvider.data.put(RoamingType.PER_USER, map)

    componentStore.getStateStorageManager().setStreamProvider(streamProvider)
    componentStore.initComponent(component, false)
    assertThat(component.foo, equalTo("newValue"))
  }

  public Test fun `remove deprecated storage on write`() {
  }

  public Test fun `remove deprecated storage on write 2`() {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private fun doRemoveDeprecatedStorageOnWrite(component: Foo) {
    val oldFile = saveConfig("other.xml", "<application>" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"old\" />\n" + "  </component>\n" + "</application>")

    saveConfig("proxy.settings.xml", "<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"new\" />\n" + "  </component>\n" + "</application>")

    componentStore.initComponent(component, false)
    assertThat(component.foo, equalTo("new"))

    component.foo = "new2"
    invokeAndWaitIfNeed { StoreUtil.save(componentStore, null) }

    assertThat(oldFile, not(exists()))
  }

  private fun saveConfig(fileName: String, Language("XML") data: String): File {
    val file = File(testAppConfig, fileName)
    FileUtil.writeToFile(file, data)
    return file
  }

  private class MyStreamProvider : StreamProvider {
    public val data: MutableMap<RoamingType, MutableMap<String, String>> = THashMap()

    override fun saveContent(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
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

    override fun loadContent(fileSpec: String, roamingType: RoamingType): InputStream? {
      val data = getMap(roamingType).get(fileSpec) ?: return null
      return ByteArrayInputStream(data.toByteArray())
    }

    override fun delete(fileSpec: String, roamingType: RoamingType) {
      data.get(roamingType)?.remove(fileSpec)
    }
  }

  class MyComponentStore(testAppConfigPath: String) : ComponentStoreImpl() {
    private val storageManager = object : StateStorageManagerImpl(ApplicationPathMacroManager().createTrackingSubstitutor(), "application") {
      override fun getMacroSubstitutor(fileSpec: String): TrackingPathMacroSubstitutor? {
        if (fileSpec == "${StoragePathMacros.APP_CONFIG}/${PathMacrosImpl.EXT_FILE_NAME}.xml") {
          return null
        }
        return super.getMacroSubstitutor(fileSpec)
      }
    }

    init {
      setPath(testAppConfigPath)
    }

    override fun setPath(path: String) {
      storageManager.addMacro(StoragePathMacros.APP_CONFIG, path)
    }

    override fun getStateStorageManager() = storageManager

    override fun getMessageBus() = ApplicationManager.getApplication().getMessageBus()
  }

  abstract class Foo {
    public var foo: String = "defaultValue"
  }

  State(name = "HttpConfigurable", storages = arrayOf(Storage(file = StoragePathMacros.APP_CONFIG + "/proxy.settings.xml"), Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true)))
  class SeveralStoragesConfigured : Foo(), PersistentStateComponent<SeveralStoragesConfigured> {
    override fun getState(): SeveralStoragesConfigured? {
      return this
    }

    override fun loadState(state: SeveralStoragesConfigured) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }

  State(name = "HttpConfigurable", storages = arrayOf(Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml", deprecated = true), Storage(file = StoragePathMacros.APP_CONFIG + "/proxy.settings.xml")))
  class ActualStorageLast : Foo(), PersistentStateComponent<ActualStorageLast> {
    override fun getState() = this

    override fun loadState(state: ActualStorageLast) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}
