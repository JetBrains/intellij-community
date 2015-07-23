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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.stores.StateStorageManager
import com.intellij.openapi.components.impl.stores.StorageData
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.components.impl.stores.StreamProvider
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.xmlb.XmlSerializerUtil
import gnu.trove.THashMap
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.intellij.lang.annotations.Language
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

public class ApplicationStoreTest : LightPlatformTestCase() {
  private var testAppConfig: File? = null
  private var componentStore: MyComponentStore? = null

  override fun setUp() {
    super.setUp()

    val testAppConfigPath = System.getProperty("test.app.config.path")
    if (testAppConfigPath == null) {
      testAppConfig = FileUtil.createTempDirectory("testAppSettings", null)
    }
    else {
      testAppConfig = File(FileUtil.expandUserHome(testAppConfigPath))
    }
    FileUtil.delete(testAppConfig!!)

    componentStore = MyComponentStore(testAppConfig!!.getAbsolutePath())
  }

  override fun tearDown() {
    try {
      Disposer.dispose(componentStore!!)
      componentStore = null
    }
    finally {
      try {
        super.tearDown()
      }
      finally {
        FileUtil.delete(testAppConfig!!)
      }
    }
  }

  public fun testStreamProviderSaveIfSeveralStoragesConfigured() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore!!.getStateStorageManager().setStreamProvider(streamProvider)

    componentStore!!.initComponent(component, false)
    component.foo = "newValue"
    StoreUtil.save(componentStore!!, null)

    assertThat<String>(streamProvider.data.get(RoamingType.PER_USER)!!.get(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml"), equalTo("<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"newValue\" />\n" + "  </component>\n" + "</application>"))
  }

  public fun testLoadFromStreamProvider() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = THashMap<String, String>()
    map.put(StoragePathMacros.APP_CONFIG + "/proxy.settings.xml", "<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"newValue\" />\n" + "  </component>\n" + "</application>")
    streamProvider.data.put(RoamingType.PER_USER, map)

    componentStore!!.getStateStorageManager().setStreamProvider(streamProvider)
    componentStore!!.initComponent(component, false)
    assertThat(component.foo, equalTo("newValue"))
  }

  public fun testRemoveDeprecatedStorageOnWrite() {
  }

  public fun testRemoveDeprecatedStorageOnWrite2() {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private fun doRemoveDeprecatedStorageOnWrite(component: Foo) {
    val oldFile = saveConfig("other.xml", "<application>" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"old\" />\n" + "  </component>\n" + "</application>")

    saveConfig("proxy.settings.xml", "<application>\n" + "  <component name=\"HttpConfigurable\">\n" + "    <option name=\"foo\" value=\"new\" />\n" + "  </component>\n" + "</application>")

    componentStore!!.initComponent(component, false)
    assertThat(component.foo, equalTo("new"))

    component.foo = "new2"
    StoreUtil.save(componentStore!!, null)

    assertThat(oldFile.exists(), equalTo(false))
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

    throws(IOException::class)
    override fun loadContent(fileSpec: String, roamingType: RoamingType): InputStream? {
      val data = getMap(roamingType).get(fileSpec)
      return if (data == null) null else ByteArrayInputStream(data.toByteArray(CharsetToolkit.UTF8_CHARSET))
    }

    override fun delete(fileSpec: String, roamingType: RoamingType) {
      val map = data.get(roamingType)
      map?.remove(fileSpec)
    }
  }

  class MyComponentStore(testAppConfigPath: String) : ComponentStoreImpl(), Disposable {
    private val stateStorageManager: StateStorageManager

    init {
      val macroSubstitutor = ApplicationPathMacroManager().createTrackingSubstitutor()
      stateStorageManager = object : StateStorageManagerImpl(macroSubstitutor, "application", this, ApplicationManager.getApplication().getPicoContainer()) {
        override fun createStorageData(fileSpec: String, filePath: String) = StorageData("application")

        override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation) = null

        override fun getMacroSubstitutor(fileSpec: String): TrackingPathMacroSubstitutor? {
          if (fileSpec == "${StoragePathMacros.APP_CONFIG}/${PathMacrosImpl.EXT_FILE_NAME}.xml") {
            return null
          }
          return super.getMacroSubstitutor(fileSpec)
        }
      }

      stateStorageManager.addMacro(StoragePathMacros.APP_CONFIG, testAppConfigPath)
    }

    override fun getStateStorageManager() = stateStorageManager

    override fun dispose() {
    }

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
