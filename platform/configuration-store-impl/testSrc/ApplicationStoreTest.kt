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
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.ExceptionUtil
import com.intellij.util.SmartList
import com.intellij.util.io.lastModified
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.writeChild
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import gnu.trove.THashMap
import org.assertj.core.data.MapEntry
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.picocontainer.MutablePicoContainer
import org.picocontainer.defaults.InstanceComponentAdapter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.Delegates

internal class ApplicationStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  @JvmField
  @Rule
  val edtRule = EdtRule()

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  @Before fun setUp() {
    testAppConfig = tempDirManager.newPath()
    componentStore = MyComponentStore(testAppConfig.systemIndependentPath)
  }

  @Test fun `stream provider save if several storages configured`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.storageManager.removeStreamProvider(MyStreamProvider::class.java)
    componentStore.storageManager.addStreamProvider(streamProvider)

    componentStore.initComponent(component, false)
    component.foo = "newValue"
    componentStore.save(SmartList())

    assertThat(streamProvider.data[RoamingType.DEFAULT]!!["new.xml"]).isEqualTo("<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
  }

  @Test fun `load from stream provider`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = THashMap<String, String>()
    val fileSpec = "new.xml"
    map.put(fileSpec, "<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
    streamProvider.data.put(RoamingType.DEFAULT, map)

    val storageManager = componentStore.storageManager
    storageManager.removeStreamProvider(MyStreamProvider::class.java)
    storageManager.addStreamProvider(streamProvider)
    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("newValue")

    assertThat(Paths.get(storageManager.expandMacros(fileSpec))).doesNotExist()
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

  @Test fun `export settings`() {
    testAppConfig.refreshVfs()

    val storageManager = ApplicationManager.getApplication().stateStore.stateStorageManager
    val optionsPath = storageManager.expandMacros(APP_CONFIG)
    val rootConfigPath = storageManager.expandMacros(ROOT_CONFIG)
    val map = getExportableComponentsMap(false, true, storageManager)
    assertThat(map).isNotEmpty

    fun test(item: ExportableItem) {
      val file = item.file
      assertThat(map.get(file)).containsExactly(item)
      assertThat(file).doesNotExist()
    }

    test(ExportableItem(Paths.get(optionsPath, "filetypes.xml"), "File types", RoamingType.DEFAULT))
    test(ExportableItem(Paths.get(rootConfigPath, "filetypes"), "File types (schemes)", RoamingType.DEFAULT))
    test(ExportableItem(Paths.get(optionsPath, "customization.xml"), "Menus and toolbars customization", RoamingType.DEFAULT))
    test(ExportableItem(Paths.get(optionsPath, "templates.xml"), "Live templates", RoamingType.DEFAULT))
    test(ExportableItem(Paths.get(rootConfigPath, "templates"), "Live templates (schemes)", RoamingType.DEFAULT))
  }

  @Test fun `import settings`() {
    testAppConfig.refreshVfs()

    val component = A()
    componentStore.initComponent(component, false)

    component.options.foo = "new"

    saveStore()

    val storageManager = componentStore.storageManager

    val configPath = storageManager.expandMacros(ROOT_CONFIG)
    val configDir = Paths.get(configPath)

    val componentPath = configDir.resolve("a.xml")
    assertThat(componentPath).isRegularFile

    // additional export path
    val additionalPath = configDir.resolve("foo")
    additionalPath.writeChild("bar.icls", "")
    val exportedData = BufferExposingByteArrayOutputStream()
    exportSettings(setOf(componentPath, additionalPath), exportedData, configPath)

    val relativePaths = getPaths(ByteArrayInputStream(exportedData.internalBuffer, 0, exportedData.size()))
    assertThat(relativePaths).containsOnly("a.xml", "foo/", "foo/bar.icls", "IntelliJ IDEA Global Settings")

    fun <B> Path.to(that: B) = MapEntry.entry(this, that)

    val picoContainer = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
    val componentKey = A::class.java.name
    picoContainer.registerComponent(InstanceComponentAdapter(componentKey, component))
    try {
      assertThat(getExportableComponentsMap(false, false, storageManager, relativePaths)).containsOnly(
        componentPath.to(listOf(ExportableItem(componentPath, ""))), additionalPath.to(listOf(ExportableItem(additionalPath, " (schemes)"))))
    }
    finally {
      picoContainer.unregisterComponent(componentKey)
    }
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

  @State(name = "A", storages = arrayOf(Storage("a.xml")), additionalExportFile = "foo")
  private open class A : PersistentStateComponent<TestState> {
    var options = TestState()

    override fun getState() = options

    override fun loadState(state: TestState) {
      this.options = state
    }
  }

  @Test fun `don't save if only format is changed`() {
    val oldContent = "<application><component name=\"A\" foo=\"old\" deprecated=\"old\"/></application>"
    val file = writeConfig("a.xml", oldContent)
    val oldModificationTime = file.lastModified()
    testAppConfig.refreshVfs()

    val component = A()
    componentStore.initComponent(component, false)
    assertThat(component.options).isEqualTo(TestState("old"))

    saveStore()

    assertThat(file).hasContent(oldContent)
    assertThat(oldModificationTime).isEqualTo(file.lastModified())

    component.options.bar = "2"
    component.options.foo = "1"
    saveStore()

    assertThat(file).hasContent("<application>\n  <component name=\"A\" foo=\"1\" bar=\"2\" />\n</application>")
  }

  @Test
  @RunsInEdt
  fun `modification tracker`() {
    testAppConfig.refreshVfs()

    @State(name = "modificationTrackerA", storages = arrayOf(Storage("a.xml")))
    open class A : PersistentStateComponent<TestState>, SimpleModificationTracker() {
      var options = TestState()

      val stateCalledCount = AtomicLong(0)
      var lastGetStateStackTrace: String? = null

      override fun getState(): TestState {
        lastGetStateStackTrace = ExceptionUtil.currentStackTrace()
        stateCalledCount.incrementAndGet()
        return options
      }

      override fun loadState(state: TestState) {
        this.options = state
      }
    }

    val component = A()
    componentStore.initComponent(component, false)

    assertThat(component.modificationCount).isEqualTo(0)
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // test that store correctly set last modification count to component modification count on init
    component.lastGetStateStackTrace = null
    saveStore()
    @Suppress("USELESS_CAST")
    assertThat(component.lastGetStateStackTrace as String?).isNull()
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // change modification count - store will be forced to check changes using serialization and A.getState will be called
    component.incModificationCount()
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    // test that store correctly save last modification time and doesn't call our state on next save
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    val componentFile = testAppConfig.resolve("a.xml")
    assertThat(componentFile).doesNotExist()

    // update data but "forget" to update modification count
    component.options.foo = "new"

    saveStore()
    assertThat(componentFile).doesNotExist()

    component.incModificationCount()
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(2)

    assertThat(componentFile).hasContent("""
    <application>
      <component name="modificationTrackerA" foo="new" />
    </application>""".trimIndent())
  }

  @Test
  @RunsInEdt
  fun persistentStateComponentWithModificationTracker() {
    testAppConfig.refreshVfs()

    @State(name = "TestPersistentStateComponentWithModificationTracker", storages = arrayOf(Storage("b.xml")))
    open class A : PersistentStateComponentWithModificationTracker<TestState> {
      var modificationCount = AtomicLong(0)

      override fun getStateModificationCount() = modificationCount.get()

      var options = TestState()

      var stateCalledCount = AtomicLong(0)

      override fun getState(): TestState {
        stateCalledCount.incrementAndGet()
        return options
      }

      override fun loadState(state: TestState) {
        this.options = state
      }

      fun incModificationCount() {
        modificationCount.incrementAndGet()
      }
    }

    val component = A()
    componentStore.initComponent(component, false)

    assertThat(component.modificationCount.get()).isEqualTo(0)
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // test that store correctly set last modification count to component modification count on init
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // change modification count - store will be forced to check changes using serialization and A.getState will be called
    component.incModificationCount()
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    // test that store correctly save last modification time and doesn't call our state on next save
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    val componentFile = testAppConfig.resolve("b.xml")
    assertThat(componentFile).doesNotExist()

    // update data but "forget" to update modification count
    component.options.foo = "new"

    saveStore()
    assertThat(componentFile).doesNotExist()

    component.incModificationCount()
    saveStore()
    assertThat(component.stateCalledCount.get()).isEqualTo(2)

    assertThat(componentFile).hasContent("""
    <application>
      <component name="TestPersistentStateComponentWithModificationTracker" foo="new" />
    </application>""".trimIndent())
  }

  @Test fun `do not check if only format changed for non-roamable storage`() {
    @State(name = "A", storages = arrayOf(Storage(value = "b.xml", roamingType = RoamingType.DISABLED)))
    class AWorkspace : A()

    val oldContent = "<application><component name=\"A\" foo=\"old\" deprecated=\"old\"/></application>"
    val file = writeConfig("b.xml", oldContent)
    testAppConfig.refreshVfs()

    val component = AWorkspace()
    componentStore.initComponent(component, false)
    assertThat(component.options).isEqualTo(TestState("old"))

    saveStore()

    assertThat(file).hasContent("<application>\n  <component name=\"A\" foo=\"old\" />\n</application>")
  }

  private fun saveStore() {
    runInEdtAndWait { componentStore.save(SmartList()) }
  }

  private fun writeConfig(fileName: String, @Language("XML") data: String) = testAppConfig.writeChild(fileName, data)

  private class MyStreamProvider : StreamProvider {
    override fun processChildren(path: String, roamingType: RoamingType, filter: (String) -> Boolean, processor: (String, InputStream, Boolean) -> Boolean) = true

    val data: MutableMap<RoamingType, MutableMap<String, String>> = THashMap()

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      getMap(roamingType).put(fileSpec, String(content, 0, size, CharsetToolkit.UTF8_CHARSET))
    }

    private fun getMap(roamingType: RoamingType): MutableMap<String, String> {
      var map = data[roamingType]
      if (map == null) {
        map = THashMap<String, String>()
        data.put(roamingType, map)
      }
      return map
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      val data = getMap(roamingType).get(fileSpec)
      data?.let { ByteArrayInputStream(it.toByteArray()) }.let(consumer)
      return true
    }

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      data.get(roamingType)?.remove(fileSpec)
      return true
    }
  }

  class MyComponentStore(testAppConfigPath: String) : ComponentStoreImpl() {
    override val storageManager = ApplicationStorageManager(ApplicationManager.getApplication())

    init {
      setPath(testAppConfigPath)
    }

    override fun setPath(path: String) {
      storageManager.addMacro(APP_CONFIG, path)
      // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
      storageManager.addMacro(ROOT_CONFIG, path)
    }
  }

  abstract class Foo {
    @Attribute
    var foo = "defaultValue"
  }

  @State(name = "A", storages = arrayOf(Storage("new.xml"), Storage(value = "old.xml", deprecated = true)))
  class SeveralStoragesConfigured : Foo(), PersistentStateComponent<SeveralStoragesConfigured> {
    override fun getState(): SeveralStoragesConfigured? {
      return this
    }

    override fun loadState(state: SeveralStoragesConfigured) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }

  @State(name = "A", storages = arrayOf(Storage(value = "old.xml", deprecated = true), Storage("new.xml")))
  class ActualStorageLast : Foo(), PersistentStateComponent<ActualStorageLast> {
    override fun getState() = this

    override fun loadState(state: ActualStorageLast) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}

private data class TestState @JvmOverloads constructor(@Attribute var foo: String = "", @Attribute var bar: String = "")