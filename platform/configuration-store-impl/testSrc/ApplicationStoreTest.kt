// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.refreshVfs
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.lastModified
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.writeChild
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import gnu.trove.THashMap
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.MapEntry
import org.intellij.lang.annotations.Language
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.picocontainer.MutablePicoContainer
import org.picocontainer.defaults.InstanceComponentAdapter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
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

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  @Before
  fun setUp() {
    testAppConfig = tempDirManager.newPath()
    componentStore = MyComponentStore(testAppConfig.systemIndependentPath)
  }

  @Test
  fun `stream provider save if several storages configured`() = runBlocking<Unit> {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.storageManager.removeStreamProvider(MyStreamProvider::class.java)
    componentStore.storageManager.addStreamProvider(streamProvider)

    componentStore.initComponent(component, false)
    component.foo = "newValue"
    saveStore()

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

  @Test
  fun `remove deprecated storage on write`() = runBlocking {
    doRemoveDeprecatedStorageOnWrite(SeveralStoragesConfigured())
  }

  @Test
  fun `remove deprecated storage on write 2`() = runBlocking {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private suspend fun doRemoveDeprecatedStorageOnWrite(component: Foo) {
    val oldFile = writeConfig("old.xml", "<application>${createComponentData("old")}</application>")

    // test BOM
    val out = ByteArrayOutputStream()
    out.write(0xef)
    out.write(0xbb)
    out.write(0xbf)
    out.write("<application>${createComponentData("new")}</application>".toByteArray())
    testAppConfig.writeChild("new.xml", out.toByteArray())

    testAppConfig.refreshVfs()

    componentStore.initComponent(component, false)
    assertThat(component.foo).isEqualTo("new")

    component.foo = "new2"
    saveStore()

    assertThat(oldFile).doesNotExist()
  }

  @Test fun `export settings`() {
    testAppConfig.refreshVfs()

    val storageManager = ApplicationManager.getApplication().stateStore.storageManager
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

  @Test
  fun `import settings`() = runBlocking<Unit> {
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

    val relativePaths = getPaths(exportedData.toInputStream())
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

  @Test
  fun `remove data from deprecated storage if another component data exists`() = runBlocking<Unit> {
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

  @State(name = "A", storages = [(Storage("a.xml"))], additionalExportFile = "foo")
  private open class A : PersistentStateComponent<TestState> {
    var options = TestState()

    var isThrowErrorOnLoadState = false

    override fun getState() = options

    override fun loadState(state: TestState) {
      if (isThrowErrorOnLoadState) {
        throw ProcessCanceledException()
      }
      this.options = state
    }
  }

  @Test
  fun `don't save if only format is changed`() = runBlocking<Unit> {
    val oldContent = """<application><component name="A" foo="old" deprecated="old"/></application>"""
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

    assertThat(file).hasContent("""
    <application>
      <component name="A" foo="1" bar="2" />
    </application>""")
  }

  @Test
  fun `loadState failed with exception it won't be called next time`() {
    writeConfig("a.xml", """<application><component name="A" foo="old" deprecated="old"/></application>""")
    testAppConfig.refreshVfs()

    val component = A()
    component.isThrowErrorOnLoadState = true
    assertThatThrownBy {
      componentStore.initComponent(component, false)
    }.isInstanceOf(ProcessCanceledException::class.java)
    assertThat(component.options).isEqualTo(TestState())

    component.isThrowErrorOnLoadState = false
    componentStore.initComponent(component, false)
    assertThat(component.options).isEqualTo(TestState("old"))
  }

  @Test
  fun `do not check if only format changed for non-roamable storage`() = runBlocking<Unit> {
    @State(name = "A", storages = [(Storage(value = "b.xml", roamingType = RoamingType.DISABLED))])
    class AWorkspace : A()

    val oldContent = """<application><component name="A" foo="old" deprecated="old"/></application>"""
    val file = writeConfig("b.xml", oldContent)
    testAppConfig.refreshVfs()

    val component = AWorkspace()
    componentStore.initComponent(component, false)
    assertThat(component.options).isEqualTo(TestState("old"))

    try {
      setRoamableComponentSaveThreshold(-100)
      saveStore()
    }
    finally {
      restoreDefaultNotRoamableComponentSaveThreshold()
    }

    assertThat(file).hasContent("""
    <application>
      <component name="A" foo="old" />
    </application>""")
  }

  @Test
  fun `other xml file as not-roamable without explicit roaming`() = runBlocking<Unit> {
    @State(name = "A", storages = [(Storage(value = Storage.NOT_ROAMABLE_FILE))])
    class AOther : A()

    val component = AOther()
    componentStore.initComponent(component, false)
    component.options.foo = "old"

    saveStore()

    assertThat(testAppConfig.resolve(Storage.NOT_ROAMABLE_FILE)).doesNotExist()
  }

  private suspend fun saveStore() {
    componentStore.save()
  }

  private fun writeConfig(fileName: String, @Language("XML") data: String) = testAppConfig.writeChild(fileName, data)

  private class MyStreamProvider : StreamProvider {
    override val isExclusive = true

    override fun processChildren(path: String, roamingType: RoamingType, filter: (String) -> Boolean, processor: (String, InputStream, Boolean) -> Boolean) = true

    val data: MutableMap<RoamingType, MutableMap<String, String>> = THashMap()

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      getMap(roamingType).put(fileSpec, String(content, 0, size, Charsets.UTF_8))
    }

    private fun getMap(roamingType: RoamingType): MutableMap<String, String> {
      return data.getOrPut(roamingType) { THashMap<String, String>() }
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

  private class MyComponentStore(testAppConfigPath: String) : ChildlessComponentStore() {
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

  @State(name = "A", storages = [(Storage("new.xml")), (Storage(value = "old.xml", deprecated = true))])
  class SeveralStoragesConfigured : Foo(), PersistentStateComponent<SeveralStoragesConfigured> {
    override fun getState(): SeveralStoragesConfigured? {
      return this
    }

    override fun loadState(state: SeveralStoragesConfigured) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }

  @State(name = "A", storages = [(Storage(value = "old.xml", deprecated = true)), (Storage("new.xml"))])
  class ActualStorageLast : Foo(), PersistentStateComponent<ActualStorageLast> {
    override fun getState() = this

    override fun loadState(state: ActualStorageLast) {
      XmlSerializerUtil.copyBean(state, this)
    }
  }
}

internal data class TestState @JvmOverloads constructor(@Attribute var foo: String = "", @Attribute var bar: String = "")