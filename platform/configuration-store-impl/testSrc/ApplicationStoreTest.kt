// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UsePropertyAccessSyntax", "ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.exists
import com.intellij.util.io.lastModified
import com.intellij.util.io.write
import com.intellij.util.io.writeChild
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.MapEntry
import org.intellij.lang.annotations.Language
import org.junit.Assert.*
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.*
import kotlin.properties.Delegates

internal class ApplicationStoreTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  @Before
  fun setUp() {
    testAppConfig = fsRule.fs.getPath("/app-config")
    componentStore = MyComponentStore(testAppConfig)
  }

  @Test
  fun `stream provider save if several storages configured`() = runBlocking<Unit> {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.storageManager.removeStreamProvider(MyStreamProvider::class.java)
    componentStore.storageManager.addStreamProvider(streamProvider)

    componentStore.initComponent(component, null, null)
    component.foo = "newValue"
    componentStore.save()

    assertThat(streamProvider.data.get(RoamingType.DEFAULT)!!.get("new.xml"))
      .isEqualTo("<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
  }

  @Test fun `load from stream provider`() {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = HashMap<String, String>()
    val fileSpec = "new.xml"
    map[fileSpec] = "<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>"
    streamProvider.data[RoamingType.DEFAULT] = map

    val storageManager = componentStore.storageManager
    storageManager.removeStreamProvider(MyStreamProvider::class.java)
    storageManager.addStreamProvider(streamProvider)
    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("newValue")

    assertThat(storageManager.expandMacro(fileSpec)).doesNotExist()
  }

  @Test
  fun `remove deprecated storage on write`() = runBlocking {
    doRemoveDeprecatedStorageOnWrite(SeveralStoragesConfigured())
  }

  @Test
  fun `remove deprecated storage on write 2`() = runBlocking {
    doRemoveDeprecatedStorageOnWrite(ActualStorageLast())
  }

  private suspend fun doRemoveDeprecatedStorageOnWrite(component: FooComponent) {
    val oldFile = writeConfig("old.xml", "<application>${createComponentData("old")}</application>")

    // test BOM
    val out = ByteArrayOutputStream()
    out.write(0xef)
    out.write(0xbb)
    out.write(0xbf)
    out.write("<application>${createComponentData("new")}</application>".toByteArray())
    testAppConfig.writeChild("new.xml", out.toByteArray())

    testAppConfig.refreshVfs()

    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("new")

    component.foo = "new2"
    componentStore.save()

    assertThat(oldFile).doesNotExist()
  }

  @Test fun `export settings`() {
    testAppConfig.refreshVfs()

    val storageManager = ApplicationManager.getApplication().stateStore.storageManager
    val map = getExportableComponentsMap(true, storageManager)
    assertThat(map).isNotEmpty

    fun test(item: ExportableItem) {
      assertNotNull("Map doesn't contain item for ${item.fileSpec}. Whole map: \n${map.entries.joinToString("\n")}", map[item.fileSpec])
    }

    test(ExportableItem(FileSpec("filetypes", true), "File types (schemes)"))
    test(ExportableItem(FileSpec("options/filetypes.xml", false), "File types"))
    test(ExportableItem(FileSpec("options/customization.xml", false), "Menus and toolbars customization"))
    test(ExportableItem(FileSpec("options/templates.xml", false), "Live templates"))
    test(ExportableItem(FileSpec("templates", true), "Live templates (schemes)"))
  }

  @Test
  fun `import settings`() {
    val component = A()
    componentStore.initComponent(component, null, null)

    component.options.foo = "new"

    runBlocking {
      componentStore.save()
    }

    val storageManager = componentStore.storageManager

    val configDir = storageManager.expandMacro(ROOT_CONFIG)

    val componentPath = configDir.resolve("a.xml")
    assertThat(componentPath).isRegularFile()

    // additional export path
    val additionalPath = configDir.resolve("foo")
    additionalPath.writeChild("bar.icls", "")
    val exportedData = BufferExposingByteArrayOutputStream()
    exportSettings(setOf(ExportableItem(FileSpec("a.xml", false), ""), ExportableItem(FileSpec("foo", true), "")), exportedData, mapOf(), storageManager)

    val relativePaths = getPaths(exportedData.toInputStream())
    assertThat(relativePaths).containsOnly("a.xml", "foo", "foo/bar.icls", "IntelliJ IDEA Global Settings")

    fun <B> Path.to(that: B) = MapEntry.entry(this, that)

    ApplicationManager.getApplication().registerServiceInstance(A::class.java, component)
    try {
      assertThat(getExportableItemsFromLocalStorage(getExportableComponentsMap(false, storageManager), storageManager))
        .containsOnly(
          componentPath.to(listOf(LocalExportableItem(componentPath, ""))),
          additionalPath.to(listOf(LocalExportableItem(additionalPath, " (schemes)")))
        )
    }
    finally {
      (ApplicationManager.getApplication() as ComponentManagerImpl).unregisterComponent(A::class.java)
    }
  }

  @Test
  fun `import deprecated settings`() {

    @State(name = "Comp", storages = [
      Storage("old.xml", roamingType = RoamingType.PER_OS, deprecated = true),
      Storage("new.xml", roamingType = RoamingType.PER_OS)])
    class Comp : FooComponent()

    val storageManager = componentStore.storageManager
    val configDir = storageManager.expandMacro(ROOT_CONFIG)
    fun fileSpec(spec: String) = FileSpec(configDir.resolve(spec).toString())

    val component = Comp()
    ApplicationManager.getApplication().registerServiceInstance(Comp::class.java, component)
    val os = getPerOsSettingsStorageFolderName()
    try {
      val allItems = getExportableComponentsMap(isComputePresentableNames = false,
                                                storageManager = storageManager,
                                                withDeprecated = true)
      assertThat(allItems).containsKeys(
        fileSpec("old.xml"),
        fileSpec("$os/old.xml"),
        fileSpec("new.xml"),
        fileSpec("$os/new.xml")
      )

      val nonDeprecatedItems = getExportableComponentsMap(isComputePresentableNames = false,
                                                          storageManager = storageManager,
                                                          withDeprecated = false)
      assertThat(nonDeprecatedItems).containsKeys(fileSpec("$os/new.xml"))
      assertThat(nonDeprecatedItems).doesNotContainKeys(
        fileSpec("old.xml"),
        fileSpec("$os/old.xml"),
        fileSpec("new.xml")
      )
    }
    finally {
      (ApplicationManager.getApplication() as ComponentManagerImpl).unregisterComponent(Comp::class.java)
    }
  }

  private fun createComponentData(fooValue: String, componentName: String = "A") = """<component name="$componentName" foo="$fooValue" />"""

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
    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("new")

    componentStore.save()

    assertThat(oldFile).hasContent("""<application>
  <component name="OtherComponent" foo="old" />
</application>""")
  }

  @Test
  fun `don't save if only format is changed`() = runBlocking<Unit> {
    val oldContent = """<application><component name="A" foo="old" deprecated="old"/></application>"""
    val file = writeConfig("a.xml", oldContent)
    val oldModificationTime = file.lastModified()
    testAppConfig.refreshVfs()

    val component = A()
    componentStore.initComponent(component, null, null)
    assertThat(component.options).isEqualTo(TestState("old"))

    componentStore.save()

    assertThat(file).hasContent(oldContent)
    assertThat(oldModificationTime).isEqualTo(file.lastModified())

    component.options.bar = "2"
    component.options.foo = "1"
    componentStore.save()

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
      componentStore.initComponent(component, null, null)
    }.isInstanceOf(ProcessCanceledException::class.java)
    assertThat(component.options).isEqualTo(TestState())

    component.isThrowErrorOnLoadState = false
    componentStore.initComponent(component, null, null)
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
    componentStore.initComponent(component, null, null)
    assertThat(component.options).isEqualTo(TestState("old"))

    try {
      setRoamableComponentSaveThreshold(-100)
      componentStore.save()
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
    @State(name = "A", storages = [(Storage(value = StoragePathMacros.NON_ROAMABLE_FILE))])
    class AOther : A()

    val component = AOther()
    componentStore.initComponent(component, null, null)
    component.options.foo = "old"

    componentStore.save()

    assertThat(testAppConfig.resolve(StoragePathMacros.NON_ROAMABLE_FILE)).doesNotExist()
  }

  @Test
  fun `remove stalled data`() = runBlocking<Unit> {
    val obsoleteStorageBean = ObsoleteStorageBean()
    obsoleteStorageBean.file = "i_do_not_want_to_be_deleted_but.xml"
    obsoleteStorageBean.components.addAll(listOf("loser1", "loser2", "lucky"))
    ExtensionTestUtil.maskExtensions(OBSOLETE_STORAGE_EP, listOf(obsoleteStorageBean), disposableRule.disposable)

    @State(name = "loser1", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class AOther : A()
    @State(name = "loser2", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class BOther : A()
    @State(name = "lucky", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class COther : A()

    val component = AOther()
    componentStore.initComponent(component, null, null)
    component.options.foo = "old"

    val component2 = BOther()
    componentStore.initComponent(component2, null, null)
    component2.options.foo = "old?"

    val component3 = COther()
    componentStore.initComponent(component3, null, null)
    component3.options.bar = "foo"

    componentStore.save()

    // all must be saved regardless of obsoleteStorageBean because we have such components
    assertThat(testAppConfig.resolve(obsoleteStorageBean.file)).isEqualTo("""
      <application>
        <component name="loser1" foo="old" />
        <component name="loser2" foo="old?" />
        <component name="lucky" bar="foo" />
      </application>
    """.trimIndent())

    component.options.foo = ""

    // first looser is deleted since state equals to default (no committed component data)
    componentStore.save()
    assertThat(testAppConfig.resolve(obsoleteStorageBean.file)).isEqualTo("""
      <application>
        <component name="loser2" foo="old?" />
        <component name="lucky" bar="foo" />
      </application>
    """.trimIndent())

    component2.options.foo = ""

    // second looser is deleted since state equals to default (no committed component data)
    componentStore.save()
    assertThat(testAppConfig.resolve(obsoleteStorageBean.file)).isEqualTo("""
      <application>
        <component name="lucky" bar="foo" />
      </application>
    """.trimIndent())
  }

  @Test
  fun `remove stalled data - keep file if another unknown component`() = runBlocking<Unit> {
    val obsoleteStorageBean = ObsoleteStorageBean()
    obsoleteStorageBean.file = "i_will_be_not_deleted.xml"
    obsoleteStorageBean.components.addAll(listOf("Loser"))
    ExtensionTestUtil.maskExtensions(OBSOLETE_STORAGE_EP, listOf(obsoleteStorageBean), disposableRule.disposable)

    testAppConfig.resolve(obsoleteStorageBean.file).write("""
      <application>
        <component name="Unknown" data="some data" />
        <component name="Loser" foo="old?" />
      </application>
    """.trimIndent())

    componentStore.save()

    assertThat(testAppConfig.resolve(obsoleteStorageBean.file)).isEqualTo("""
      <application>
        <component name="Unknown" data="some data" />
      </application>
    """.trimIndent())
  }

  @Test
  fun `survive on error`() {
    @State(name = "Bad", storages = [Storage(value = "foo.xml")])
    class MyComponent : PersistentStateComponent<Foo> {
      override fun loadState(state: Foo) {
        throw RuntimeException("error")
      }

      override fun getState(): Foo {
        throw RuntimeException("error")
      }

      override fun noStateLoaded() {
        throw RuntimeException("error")
      }
    }

    val component = MyComponent()
    assertThatThrownBy {
      componentStore.initComponent(component, null, null)
    }.hasMessage("Cannot init component state (componentName=Bad, componentClass=MyComponent)")

    assertThat(componentStore.getComponents()).doesNotContainKey("Bad")
  }

  @Test
  fun `test per-os components are stored in subfolder`() = runBlocking {
    val component = PerOsComponent()
    componentStore.initComponent(component, null, null)
    component.foo = "bar"

    componentStore.save()

    val osCode = getPerOsSettingsStorageFolderName()
    val fs = testAppConfig.fileSystem
    assertTrue("${osCode}/per-os.xml doesn't exist", testAppConfig.resolve(fs.getPath(osCode, "per-os.xml")).exists())
    assertFalse("Old per-os.xml without os prefix was not removed", testAppConfig.resolve("per-os.xml").exists())
  }

  @Test
  fun `test per-os component is read from deprecated top-level storage and moved to new location`() = runBlocking {
    writeConfig("per-os.xml", "<application>${createComponentData("new")}</application>")

    testAppConfig.refreshVfs()

    val component = PerOsComponent()
    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("new")

    componentStore.save()

    val osCode = getPerOsSettingsStorageFolderName()
    val fs = testAppConfig.fileSystem
    assertTrue("${osCode}/per-os.xml doesn't exist", testAppConfig.resolve(fs.getPath(osCode, "per-os.xml")).exists())
    assertFalse("Old per-os.xml without os prefix was not removed", testAppConfig.resolve("per-os.xml").exists())
  }

  @Test
  fun `per-os setting is preferred from os subfolder`() {
    val osCode = getPerOsSettingsStorageFolderName()
    writeConfig("per-os.xml", "<application>${createComponentData("old")}</application>")
    writeConfig("${osCode}/per-os.xml", "<application>${createComponentData("new")}</application>")

    testAppConfig.refreshVfs()

    val component = PerOsComponent()
    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("new")
  }

  @Test
  fun `can keep xml file name when deprecating roaming type`() = runBlocking {
    @State(name = "Comp", storages = [
      Storage("old.xml", roamingType = RoamingType.PER_OS, deprecated = true),
      Storage("old.xml", roamingType = RoamingType.DEFAULT)
    ])
    class Comp : FooComponent()

    val os = getPerOsSettingsStorageFolderName()
    writeConfig("$os/old.xml", """<application>${createComponentData("old", "Comp")}</application>""")
    testAppConfig.refreshVfs()

    val component = Comp()
    componentStore.initComponent(component, null, null)
    assertThat(component.foo).isEqualTo("old")

    componentStore.save()

    val fs = testAppConfig.fileSystem
    assertFalse("$os/old.xml was not removed", testAppConfig.resolve(fs.getPath(os, "old.xml")).exists())
    assertTrue("New old.xml without os prefix not found", testAppConfig.resolve("old.xml").exists())
  }

  @State(name = "A", storages = [Storage(value = "per-os.xml", roamingType = RoamingType.PER_OS)])
  private class PerOsComponent : FooComponent()

  private fun writeConfig(fileName: String, @Language("XML") data: String) = testAppConfig.writeChild(fileName, data)

  private class MyStreamProvider : StreamProvider {
    override val isExclusive = true

    override fun processChildren(path: String, roamingType: RoamingType, filter: (String) -> Boolean, processor: (String, InputStream, Boolean) -> Boolean) = true

    val data: MutableMap<RoamingType, MutableMap<String, String>> = EnumMap(RoamingType::class.java)

    override fun write(fileSpec: String, content: ByteArray, size: Int, roamingType: RoamingType) {
      getMap(roamingType).put(fileSpec, String(content, 0, size, Charsets.UTF_8))
    }

    private fun getMap(roamingType: RoamingType): MutableMap<String, String> = data.computeIfAbsent(roamingType) { HashMap() }

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

  private class MyComponentStore(testAppConfigPath: Path) : ComponentStoreWithExtraComponents() {
    override val serviceContainer: ComponentManagerImpl
      get() = ApplicationManager.getApplication() as ComponentManagerImpl

    override val storageManager = ApplicationStorageManager(ApplicationManager.getApplication())

    init {
      setPath(testAppConfigPath)
    }

    override fun setPath(path: Path) {
      // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
      storageManager.setMacros(listOf(Macro(APP_CONFIG, path), Macro(ROOT_CONFIG, path), Macro(StoragePathMacros.CACHE_FILE, path)))
    }

    override suspend fun doSave(result: SaveResult, forceSavingAllSettings: Boolean) {
      childlessSaveImplementation(result, forceSavingAllSettings)
    }
  }

  private abstract class FooComponent : PersistentStateComponent<Foo> {
    private val myState = Foo()

    var foo
      get() = myState.foo
      set(value) {
        myState.foo = value
      }

    override fun getState() = myState

    override fun loadState(state: Foo) {
      XmlSerializerUtil.copyBean(state, myState)
    }
  }

  private class Foo {
    @Attribute
    var foo = "defaultValue"
  }

  @State(name = "A", storages = [(Storage("new.xml")), (Storage(value = "old.xml", deprecated = true))])
  private class SeveralStoragesConfigured : FooComponent()

  @State(name = "A", storages = [(Storage(value = "old.xml", deprecated = true)), (Storage("new.xml"))])
  private class ActualStorageLast : FooComponent()
}

internal data class TestState(@Attribute var foo: String = "", @Attribute var bar: String = "")

@State(name = "A", storages = [(Storage("a.xml"))], additionalExportDirectory = "foo")
internal open class A : PersistentStateComponent<TestState> {
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