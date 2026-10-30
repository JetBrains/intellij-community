// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.concurrency.currentTemporaryThreadContextOrNull
import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.diagnostic.PluginException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.local.clearCacheStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.refreshVfs
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.assertj.core.data.MapEntry
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.EnumMap
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.createDirectories
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.seconds

@OptIn(SettingsInternalApi::class)
class ApplicationStoreTest {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @JvmField @Rule val fsRule = InMemoryFsRule()
  @JvmField @Rule val disposableRule = DisposableRule()

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: TestComponentStore by Delegates.notNull()

  @Before
  fun setUp() {
    testAppConfig = fsRule.fs.getPath("/app-config")
    componentStore = TestComponentStore(testAppConfig)
  }

  @Test
  fun `stream provider save if several storages configured`() = runBlocking<Unit> {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    componentStore.storageManager.removeStreamProvider(MyStreamProvider::class.java)
    componentStore.storageManager.addStreamProvider(streamProvider)

    componentStore.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    component.foo = "newValue"
    componentStore.save()

    assertThat(streamProvider.data[RoamingType.DEFAULT]!!["new.xml"])
      .isEqualTo("<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>")
  }

  @Test
  fun `load from stream provider`(): Unit = runBlocking(Dispatchers.Default) {
    val component = SeveralStoragesConfigured()

    val streamProvider = MyStreamProvider()
    val map = HashMap<String, String>()
    val fileSpec = "new.xml"
    map[fileSpec] = "<application>\n  <component name=\"A\" foo=\"newValue\" />\n</application>"
    streamProvider.data[RoamingType.DEFAULT] = map

    val storageManager = componentStore.storageManager
    storageManager.removeStreamProvider(MyStreamProvider::class.java)
    storageManager.addStreamProvider(streamProvider)
    componentStore.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
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
    testAppConfig.resolve("new.xml").writeBytes(out.toByteArray())

    testAppConfig.refreshVfs()

    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.foo).isEqualTo("new")

    component.foo = "new2"
    componentStore.save()

    assertThat(oldFile).doesNotExist()
  }

  @Test
  fun `export settings`() {
    testAppConfig.refreshVfs()

    val storageManager = ApplicationManager.getApplication().stateStore.storageManager
    val map = getExportableComponentsMap(true, storageManager)
    assertThat(map).isNotEmpty

    fun test(item: ExportableItem) {
      assertNotNull("Map doesn't contain item for ${item.fileSpec}. Whole map: \n${map.entries.joinToString("\n")}", map[item.fileSpec])
    }

    test(ExportableItem(FileSpec("filetypes", "filetypes", true), "File types (schemes)"))
    test(ExportableItem(FileSpec("options/filetypes.xml", "filetypes.xml", false), "File types"))
    test(ExportableItem(FileSpec("options/customization.xml", "customization.xml", false), "Menus and toolbars customization"))
    test(ExportableItem(FileSpec("options/templates.xml", "templates.xml", false), "Live templates"))
    test(ExportableItem(FileSpec("templates", "templates", true), "Live templates (schemes)"))
    test(ExportableItem(FileSpec("options/project.default.xml", "project.default.xml", false), OptionsBundle.message("exportable.ProjectManager.presentable.name")))
    test(ExportableItem(FileSpec("options/jdk.table.xml", "jdk.table.xml", false), ProjectBundle.message("sdk.table.settings")))
  }

  @Test
  fun `import settings`() = runBlocking<Unit>(Dispatchers.Default) {
    val component = A()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)

    component.options.foo = "new"

    componentStore.save()

    val storageManager = componentStore.storageManager

    val configDir = storageManager.expandMacro(ROOT_CONFIG)

    val componentPath = configDir.resolve("a.xml")
    assertThat(componentPath).isRegularFile()

    // additional export path
    val additionalPath = configDir.resolve("foo").createDirectories()
    additionalPath.resolve("bar.icls").writeText("")

    val exportedData = BufferExposingByteArrayOutputStream()
    exportSettings(
      setOf(ExportableItem(FileSpec("a.xml", "a.xml", false), ""), ExportableItem(FileSpec("foo", "foo", true), "")),
      exportedData, exportableThirdPartyFiles = mapOf(), storageManager
    )

    val relativePaths = ZipInputStream(exportedData.toInputStream()).use { zip ->
      generateSequence { zip.nextEntry }.map { it.name.trimEnd('/') }.toList()
    }
    assertThat(relativePaths).containsOnly("a.xml", "foo", "foo/bar.icls", "IntelliJ IDEA Global Settings")

    fun <B> Path.to(that: B) = MapEntry.entry(this, that)

    ApplicationManager.getApplication().registerServiceInstance(A::class.java, component)
    try {
      assertThat(getExportableItemsFromLocalStorage(getExportableComponentsMap(false, storageManager), storageManager)).containsOnly(
        componentPath.to(listOf(LocalExportableItem(componentPath, ""))),
        additionalPath.to(listOf(LocalExportableItem(additionalPath, " (schemes)")))
      )
    }
    finally {
      (ApplicationManager.getApplication() as ComponentManagerEx).unregisterComponent(A::class.java)
    }
  }

  @Test
  fun `import deprecated settings`() {
    @State(name = "Comp", storages = [
      Storage("old.xml", roamingType = RoamingType.PER_OS, deprecated = true),
      Storage("new.xml", roamingType = RoamingType.PER_OS)]
    )
    class Comp : FooComponent()

    val storageManager = componentStore.storageManager
    val configDir = storageManager.expandMacro(ROOT_CONFIG)
    val os = getPerOsSettingsStorageFolderName()

    fun fileSpec(spec: String): FileSpec {
      val rawSpec = spec.removePrefix("$os/")
      return FileSpec(relativePath = configDir.resolve(spec).toString(), rawFileSpec = rawSpec, isDirectory = false)
    }

    val component = Comp()
    ApplicationManager.getApplication().registerServiceInstance(Comp::class.java, component)
    try {
      val allItems = getExportableComponentsMap(isComputePresentableNames = false, storageManager, withDeprecated = true)
      assertThat(allItems).containsKeys(
        fileSpec("old.xml"),
        fileSpec("$os/old.xml"),
        fileSpec("new.xml"),
        fileSpec("$os/new.xml")
      )

      val nonDeprecatedItems = getExportableComponentsMap(isComputePresentableNames = false, storageManager, withDeprecated = false)
      assertThat(nonDeprecatedItems).containsKeys(fileSpec("$os/new.xml"))
      assertThat(nonDeprecatedItems).doesNotContainKeys(
        fileSpec("old.xml"),
        fileSpec("$os/old.xml"),
        fileSpec("new.xml")
      )
    }
    finally {
      (ApplicationManager.getApplication() as ComponentManagerEx).unregisterComponent(Comp::class.java)
    }
  }

  private fun createComponentData(fooValue: String, componentName: String = "A") = """<component name="$componentName" foo="$fooValue" />"""

  @Test
  fun `remove data from deprecated storage if another component data exists`() = runBlocking<Unit>(Dispatchers.Default) {
    val data = createComponentData("new")
    val oldFile = writeConfig("old.xml", """<application>
    <component name="OtherComponent" foo="old" />
    ${createComponentData("old")}
    </application>""")
    writeConfig("new.xml", "<application>$data</application>")

    testAppConfig.refreshVfs()

    val component = SeveralStoragesConfigured()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.foo).isEqualTo("new")

    componentStore.save()

    assertThat(oldFile).hasContent("""<application>
  <component name="OtherComponent" foo="old" />
</application>""")
  }

  @Test
  fun `don't save if only format is changed`() = runBlocking<Unit>(Dispatchers.Default) {
    val oldContent = """<application><component name="A" foo="old" deprecated="old"/></application>"""
    val file = writeConfig("a.xml", oldContent)
    val oldModificationTime = file.getLastModifiedTime()
    testAppConfig.refreshVfs()

    val component = A()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.options).isEqualTo(TestState("old"))

    componentStore.save()

    assertThat(file).hasContent(oldContent)
    assertThat(oldModificationTime).isEqualTo(file.getLastModifiedTime())

    component.options.bar = "2"
    component.options.foo = "1"
    componentStore.save()

    assertThat(file).hasContent("""
    <application>
      <component name="A" foo="1" bar="2" />
    </application>""")
  }

  @Test
  fun `loadState failed with exception it won't be called next time`() = runBlocking<Unit>(Dispatchers.Default) {
    writeConfig("a.xml", """<application><component name="A" foo="old" deprecated="old"/></application>""")
    testAppConfig.refreshVfs()

    val component = A()
    component.loadStateCallback = {
      throw ProcessCanceledException()
    }
    try {
      componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    }
    catch (e: Throwable) {
      assertThat(e).isInstanceOf(ProcessCanceledException::class.java)
    }
    assertThat(component.options).isEqualTo(TestState())

    component.loadStateCallback = null
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.options).isEqualTo(TestState("old"))
  }

  @Test
  fun `prefetched state is used by initComponent`() = runBlocking<Unit>(Dispatchers.Default) {
    val file = writeConfig("a.xml", """<application><component name="A" foo="old"/></application>""")
    testAppConfig.refreshVfs()

    componentStore.prefetchComponentState(A::class.java)
    // initComponent must not read the file again
    file.deleteIfExists()

    val component = A()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.options).isEqualTo(TestState("old"))
  }

  @Test
  fun `prefetch skips the second storage`() = runBlocking<Unit>(Dispatchers.Default) {
    writeConfig("new.xml", """<application><component name="B" foo="new"/></application>""")
    writeConfig("old.xml", """<application><component name="A" foo="old"/></application>""")
    testAppConfig.refreshVfs()
    val streamProvider = ReadRecordingStreamProvider()
    componentStore.storageManager.addStreamProvider(streamProvider)

    componentStore.prefetchComponentState(SeveralStoragesConfigured::class.java)
    // the first storage has no state of the component, but the prefetch must not read the second storage
    assertThat(streamProvider.readFileSpecs).containsExactly("new.xml")

    val component = SeveralStoragesConfigured()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    // initComponent reads the second storage, and it uses the prefetched data of the first storage
    assertThat(component.foo).isEqualTo("old")
    assertThat(streamProvider.readFileSpecs).containsExactly("new.xml", "old.xml")
  }

  @Test
  fun `long read of the service state is cancellable before the service initialization`() = runBlocking<Unit>(Dispatchers.Default) {
    val app = ApplicationManager.getApplication()
    val storageManager = app.stateStore.storageManager
    val streamProvider = SlowStreamProvider(
      fileSpec = SLOW_READ_FILE_SPEC,
      data = """<application><component name="SlowReadService" foo="stored"/></application>""",
    )
    storageManager.addStreamProvider(streamProvider)
    val loggedErrors = CopyOnWriteArrayList<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        loggedErrors.add("$message: $t")
        return super.processError(category, message, details, t)
      }
    }
    try {
      LoggedErrorProcessor.executeWith(errorProcessor).use {
        val getService = launch {
          app.service<SlowReadService>()
        }
        withTimeout(10.seconds) {
          streamProvider.readStarted.await()
        }
        // the read runs before the initialization, so other callers do not wait for it
        assertThat(app.serviceIfCreated<SlowReadService>()).isNull()

        // the read checks the cancellation, so the caller must stop soon
        withTimeout(10.seconds) {
          getService.cancelAndJoin()
        }
        assertThat(app.serviceIfCreated<SlowReadService>()).isNull()
      }
      // the cancellation is not an error of the read
      assertThat(loggedErrors).isEmpty()

      // the canceled read must not keep an empty state for the storage
      streamProvider.isSlow = false
      assertThat(app.service<SlowReadService>().state).isEqualTo(TestState("stored"))
    }
    finally {
      storageManager.removeStreamProvider(SlowStreamProvider::class.java)
    }
  }

  @Test
  fun `cancellation of the progress indicator of the caller cancels the read of the service state`() = runBlocking<Unit>(Dispatchers.Default) {
    val app = ApplicationManager.getApplication()
    val storageManager = app.stateStore.storageManager
    val streamProvider = IndicatorCancelingStreamProvider(
      fileSpec = INDICATOR_CANCELED_READ_FILE_SPEC,
      data = """<application><component name="IndicatorCanceledReadService" foo="stored"/></application>""",
    )
    storageManager.addStreamProvider(streamProvider)
    val loggedErrors = CopyOnWriteArrayList<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        loggedErrors.add("$message: $t")
        return Action.NONE
      }
    }
    try {
      val indicator = EmptyProgressIndicator()
      streamProvider.indicatorToCancel = indicator
      var error: Throwable? = null
      LoggedErrorProcessor.executeWith(errorProcessor).use {
        try {
          ProgressManager.getInstance().runProcess({ app.service<IndicatorCanceledReadService>() }, indicator)
        }
        catch (e: Throwable) {
          error = e
        }
      }
      // the caller is canceled through the indicator, so the read must stop with the cancellation, not with an error
      assertThat(error).isInstanceOf(ProcessCanceledException::class.java)
      assertThat(loggedErrors).isEmpty()
      assertThat(app.serviceIfCreated<IndicatorCanceledReadService>()).isNull()

      // the canceled read must not keep an empty state for the storage
      streamProvider.indicatorToCancel = null
      assertThat(app.service<IndicatorCanceledReadService>().state).isEqualTo(TestState("stored"))
      assertThat(streamProvider.readCount.get()).isEqualTo(2)
    }
    finally {
      storageManager.removeStreamProvider(IndicatorCancelingStreamProvider::class.java)
    }
  }

  @Test
  fun `cancellation exception without the cancellation of the caller breaks the read of the service state`() = runBlocking<Unit>(Dispatchers.Default) {
    val app = ApplicationManager.getApplication()
    val storageManager = app.stateStore.storageManager
    val readError = CancellationException("the provider fails, but the caller is not canceled")
    val streamProvider = ThrowingStreamProvider(fileSpec = BROKEN_READ_FILE_SPEC, error = readError)
    storageManager.addStreamProvider(streamProvider)
    val loggedErrors = CopyOnWriteArrayList<Pair<String, Throwable?>>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        loggedErrors.add(message to t)
        return Action.NONE
      }
    }
    try {
      val service = LoggedErrorProcessor.executeWith(errorProcessor).use {
        // the caller is active, so the exception does not cancel the caller
        app.service<BrokenReadService>()
      }
      assertThat(isActive).isTrue()

      // the exception is reported as an error of the read, and the service gets the default state
      assertThat(loggedErrors).hasSize(1)
      val (message, error) = loggedErrors.single()
      assertThat(message).isEqualTo("Cannot load data for $BROKEN_READ_FILE_SPEC")
      assertThat(error).isNotInstanceOf(CancellationException::class.java).hasCause(readError)
      assertThat(service.state).isEqualTo(TestState())

      // the storage keeps the empty data, so the service initialization does not read the file again
      assertThat(streamProvider.readCount.get()).isEqualTo(1)
    }
    finally {
      storageManager.removeStreamProvider(ThrowingStreamProvider::class.java)
    }
  }

  @Test
  fun `registry key disables the preload of the service state`() = runBlocking<Unit>(Dispatchers.Default) {
    val app = ApplicationManager.getApplication()
    val storageManager = app.stateStore.storageManager
    val streamProvider = PreparationRecordingStreamProvider()
    storageManager.addStreamProvider(streamProvider)
    try {
      app.service<PreloadedService>()
      assertThat(streamProvider.readsInPreparation).containsExactly(MapEntry.entry(PRELOADED_FILE_SPEC, true))

      Registry.get("ide.service.preload.state").setValue(false, disposableRule.disposable)
      app.service<NotPreloadedService>()
      assertThat(streamProvider.readsInPreparation).containsExactly(
        MapEntry.entry(PRELOADED_FILE_SPEC, true),
        MapEntry.entry(NOT_PRELOADED_FILE_SPEC, false),
      )
    }
    finally {
      storageManager.removeStreamProvider(PreparationRecordingStreamProvider::class.java)
    }
  }

  private class MyElement(val id: String) : CoroutineContext.Element {
    object Key : CoroutineContext.Key<MyElement>

    override val key: CoroutineContext.Key<*>
      get() = Key
  }

  @Test
  fun `parent scope is used if passed`() = runBlocking(Dispatchers.Default) {
    writeConfig("a.xml", """<application><component name="A" foo="old" deprecated="old"/></application>""")
    testAppConfig.refreshVfs()

    val component = A()
    try {
      withContext(MyElement("p")) {
        val parentScope = this@withContext
        withContext(MyElement("child")) {
          component.loadStateCallback = {
            val currentTemporaryThreadContextOrNull = currentTemporaryThreadContextOrNull()
            assertThat(currentTemporaryThreadContextOrNull).isNotNull()
            assertThat(currentTemporaryThreadContextOrNull!![MyElement.Key]?.id).isNull()
          }
          componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID, parentScope = parentScope)
        }
      }
    }
    catch (_: CancellationException) { }
    assertThat(component.options).isEqualTo(TestState(foo="old"))
    component.loadStateCallback = null
  }

  @Test
  fun `do not check if only format changed for non-roamable storage`() = runBlocking<Unit> {
    @State(name = "A", storages = [(Storage(value = "b.xml", roamingType = RoamingType.DISABLED))])
    class AWorkspace : A()

    val oldContent = """<application><component name="A" foo="old" deprecated="old"/></application>"""
    val file = writeConfig("b.xml", oldContent)
    testAppConfig.refreshVfs()

    val component = AWorkspace()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
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
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    component.options.foo = "old"

    componentStore.save()

    assertThat(testAppConfig.resolve(StoragePathMacros.NON_ROAMABLE_FILE)).doesNotExist()
  }

  @Test
  fun `remove stalled data`() = runBlocking<Unit> {
    val obsoleteStorageBean = ObsoleteStorageBean()
    obsoleteStorageBean.file = "i_do_not_want_to_be_deleted_but.xml"
    obsoleteStorageBean.components.addAll(listOf("loser1", "loser2", "lucky"))
    ExtensionTestUtil.maskExtensions(ObsoleteStorageBean.EP_NAME, listOf(obsoleteStorageBean), disposableRule.disposable)

    @State(name = "loser1", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class AOther : A()
    @State(name = "loser2", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class BOther : A()
    @State(name = "lucky", storages = [(Storage(value = "i_do_not_want_to_be_deleted_but.xml"))])
    class COther : A()

    val component = AOther()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    component.options.foo = "old"

    val component2 = BOther()
    componentStore.initComponent(component2, null, PluginManagerCore.CORE_ID)
    component2.options.foo = "old?"

    val component3 = COther()
    componentStore.initComponent(component3, null, PluginManagerCore.CORE_ID)
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

    // "loser1" is deleted since state equals to default (no committed component data)
    componentStore.save()
    assertThat(testAppConfig.resolve(obsoleteStorageBean.file)).isEqualTo("""
      <application>
        <component name="loser2" foo="old?" />
        <component name="lucky" bar="foo" />
      </application>
    """.trimIndent())

    component2.options.foo = ""

    // "loser2" is deleted since state equals to default (no committed component data)
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
    ExtensionTestUtil.maskExtensions(ObsoleteStorageBean.EP_NAME, listOf(obsoleteStorageBean), disposableRule.disposable)

    testAppConfig.resolve(obsoleteStorageBean.file).createParentDirectories().writeText("""
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
  fun `survive on error`() = runBlocking<Unit>(Dispatchers.Default) {
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
    try {
      componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    }
    catch (e: Throwable) {
      assertThat(e.message).contains("Cannot init component state (componentName=Bad, componentClass=MyComponent) [Plugin: com.intellij]")
    }
    assertThat(componentStore.getComponents()).doesNotContainKey("Bad")
  }

  @Test
  fun `test per-os components are stored in subfolder`() = runBlocking(Dispatchers.Default) {
    val component = PerOsComponent()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
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
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.foo).isEqualTo("new")

    componentStore.save()

    val osCode = getPerOsSettingsStorageFolderName()
    val fs = testAppConfig.fileSystem
    assertTrue("${osCode}/per-os.xml doesn't exist", testAppConfig.resolve(fs.getPath(osCode, "per-os.xml")).exists())
    assertFalse("Old per-os.xml without os prefix was not removed", testAppConfig.resolve("per-os.xml").exists())
  }

  @Test
  fun `per-os setting is preferred from os subfolder`() = runBlocking<Unit>(Dispatchers.Default) {
    val osCode = getPerOsSettingsStorageFolderName()
    writeConfig("per-os.xml", "<application>${createComponentData("old")}</application>")
    writeConfig("${osCode}/per-os.xml", "<application>${createComponentData("new")}</application>")

    testAppConfig.refreshVfs()

    val component = PerOsComponent()
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
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
    componentStore.initComponent(component, null, PluginManagerCore.CORE_ID)
    assertThat(component.foo).isEqualTo("old")

    componentStore.save()

    val fs = testAppConfig.fileSystem
    assertFalse("$os/old.xml was not removed", testAppConfig.resolve(fs.getPath(os, "old.xml")).exists())
    assertTrue("New old.xml without os prefix not found", testAppConfig.resolve("old.xml").exists())
  }

  @Test
  fun `reload components`() = runBlocking(Dispatchers.Default) {
    @State(name = "A", storages = [Storage(value = "a.xml")])
    class Component : FooComponent()

    val component = Component()
    componentStore.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    testAppConfig.resolve("a.xml").createParentDirectories().writeText("")
    componentStore.reloadComponents(changedFileSpecs = listOf("a.xml"), deletedFileSpecs = emptyList())
    assertEquals("defaultValue", component.foo)

    writeConfig("a.xml", createComponentFileContent("initial"))
    componentStore.reloadComponents(changedFileSpecs = listOf("a.xml"), deletedFileSpecs = emptyList())
    assertEquals("initial", component.foo)

    writeConfig("a.xml", createComponentFileContent("changed"))
    componentStore.reloadComponents(changedFileSpecs = listOf("a.xml"), deletedFileSpecs = emptyList())
    assertEquals("changed", component.foo)

    testAppConfig.resolve("a.xml").deleteIfExists()
    componentStore.reloadComponents(changedFileSpecs = emptyList(), deletedFileSpecs = listOf("a.xml"))
    assertEquals("defaultValue", component.foo)
  }

  @Test
  fun `settingsController - cache storage`() = runBlocking<Unit>(Dispatchers.Default) {
    clearCacheStore()

    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.CACHE_FILE)])
    class Component : SerializablePersistentStateComponent<TestState>(TestState())

    val component = Component()
    componentStore.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.foo).isEmpty()
    assertThat(component.state.bar).isEmpty()

    component.state = TestState(bar = "42")
    componentStore.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("42")
  }

  @Test
  fun `check if storage is exportable`() {
    clearCacheStore()

    @State(name = "RegularComponent", storages = [Storage(value = "somefile.xml")])
    class RegularComponent : SerializablePersistentStateComponent<TestState>(TestState())

    @State(name = "NonRoamableStorage", storages = [Storage(value = "someNR.xml", roamingType = RoamingType.DISABLED)])
    class NonRoamableStorageComponent : SerializablePersistentStateComponent<TestState>(TestState())

    @State(name = "NonRoamableExportableStorage", storages = [Storage(value = "someNR.xml", exportable = true, roamingType = RoamingType.DISABLED)])
    class NonRoamableExportableStorageComponent : SerializablePersistentStateComponent<TestState>(TestState())

    @State(name = "NonRoamableState", exportable = true, storages = [Storage(value = "someNR.xml", roamingType = RoamingType.DISABLED)])
    class NonRoamableStateComponent : SerializablePersistentStateComponent<TestState>(TestState())

    @State(name = "SpecialStorage", exportable = true, storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, exportable = true)])
    class SpecialStorage : SerializablePersistentStateComponent<TestState>(TestState())

    with(RegularComponent::class.java.getAnnotation(State::class.java)!!) {
      assertTrue(isStorageExportable("Component", this, this.storages[0], false))
      assertTrue(isStorageExportable("Component", this, this.storages[0], true))
    }

    with(NonRoamableStorageComponent::class.java.getAnnotation(State::class.java)!!) {
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
    }

    with(NonRoamableExportableStorageComponent::class.java.getAnnotation(State::class.java)!!) {
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
      assertTrue(isStorageExportable("Component", this, this.storages[0], true))
    }

    with(NonRoamableStateComponent::class.java.getAnnotation(State::class.java)!!) {
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
      assertTrue(isStorageExportable("Component", this, this.storages[0], true))
    }

    with(SpecialStorage::class.java.getAnnotation(State::class.java)!!) {
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
      assertFalse(isStorageExportable("Component", this, this.storages[0], false))
    }
  }

  private fun createComponentFileContent(fooValue: String, componentName: String = "A"): String {
    return """<application>${createComponentData(fooValue, componentName)}</application>"""
  }

  @State(name = "A", storages = [Storage(value = "per-os.xml", roamingType = RoamingType.PER_OS)])
  private class PerOsComponent : FooComponent()

  private fun writeConfig(fileName: String, @Language("XML") data: String): Path =
    testAppConfig.resolve(fileName).createParentDirectories().apply { writeText(data) }

  /**
   * Serves [fileSpec] like a slow remote storage.
   * While [isSlow] is `true`, a read of [fileSpec] waits until the caller is canceled.
   */
  private class SlowStreamProvider(private val fileSpec: String, private val data: String) : StreamProvider {
    val readStarted: CompletableDeferred<Unit> = CompletableDeferred()

    @Volatile
    var isSlow: Boolean = true

    override val isExclusive: Boolean = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = fileSpec == this.fileSpec

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (fileSpec != this.fileSpec) {
        return false
      }

      if (isSlow) {
        readStarted.complete(Unit)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
          Cancellation.checkCancelled()
          Thread.sleep(10)
        }
        throw AssertionError("The read of $fileSpec is not canceled")
      }

      consumer(ByteArrayInputStream(data.toByteArray()))
      return true
    }

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ): Boolean = false

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = false
  }

  /**
   * Serves [fileSpec] like a remote storage that checks the progress indicator of the caller.
   * While [indicatorToCancel] is not `null`, a read of [fileSpec] cancels it and checks the cancellation.
   */
  private class IndicatorCancelingStreamProvider(private val fileSpec: String, private val data: String) : StreamProvider {
    val readCount: AtomicInteger = AtomicInteger()

    @Volatile
    var indicatorToCancel: ProgressIndicator? = null

    override val isExclusive: Boolean = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = fileSpec == this.fileSpec

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (fileSpec != this.fileSpec) {
        return false
      }
      readCount.incrementAndGet()

      val indicator = indicatorToCancel
      if (indicator != null) {
        indicator.cancel()
        ProgressManager.checkCanceled()
        throw AssertionError("The read of $fileSpec is not canceled")
      }

      consumer(ByteArrayInputStream(data.toByteArray()))
      return true
    }

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ): Boolean = false

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = false
  }

  /**
   * Throws [error] on each read of [fileSpec].
   */
  private class ThrowingStreamProvider(private val fileSpec: String, private val error: Throwable) : StreamProvider {
    val readCount: AtomicInteger = AtomicInteger()

    override val isExclusive: Boolean = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = fileSpec == this.fileSpec

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (fileSpec != this.fileSpec) {
        return false
      }
      readCount.incrementAndGet()
      throw error
    }

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ): Boolean = false

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = false
  }

  /**
   * Records if the container reads [PRELOADED_FILE_SPEC] and [NOT_PRELOADED_FILE_SPEC] in the preparation of the initialization.
   * It does not serve any data.
   */
  private class PreparationRecordingStreamProvider : StreamProvider {
    val readsInPreparation: MutableMap<String, Boolean> = ConcurrentHashMap()

    override val isExclusive: Boolean = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean {
      return fileSpec == PRELOADED_FILE_SPEC || fileSpec == NOT_PRELOADED_FILE_SPEC
    }

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      if (isApplicable(fileSpec, roamingType)) {
        readsInPreparation[fileSpec] = StackWalker.getInstance().walk { frames ->
          frames.anyMatch { it.methodName == "prepareInitialization" }
        }
      }
      return false
    }

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ): Boolean = false

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = false
  }

  /**
   * Records the file specs of all reads.
   * It does not serve any data, so the storage reads the local file.
   */
  private class ReadRecordingStreamProvider : StreamProvider {
    val readFileSpecs: MutableList<String> = CopyOnWriteArrayList()

    override val isExclusive: Boolean = false

    override fun isApplicable(fileSpec: String, roamingType: RoamingType): Boolean = true

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
    }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      readFileSpecs.add(fileSpec)
      return false
    }

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ): Boolean = false

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean = false
  }

  private class MyStreamProvider : StreamProvider {
    override val isExclusive = true

    override fun processChildren(
      path: String,
      roamingType: RoamingType,
      filter: (String) -> Boolean,
      processor: (String, InputStream, Boolean) -> Boolean,
    ) = true

    val data: MutableMap<RoamingType, MutableMap<String, String>> = EnumMap(RoamingType::class.java)

    override fun write(fileSpec: String, content: ByteArray, roamingType: RoamingType) {
      getMap(roamingType)[fileSpec] = content.decodeToString()
    }

    private fun getMap(roamingType: RoamingType): MutableMap<String, String> = data.computeIfAbsent(roamingType) { HashMap() }

    override fun read(fileSpec: String, roamingType: RoamingType, consumer: (InputStream?) -> Unit): Boolean {
      val data = getMap(roamingType)[fileSpec]
      data?.let { ByteArrayInputStream(it.toByteArray()) }.let(consumer)
      return true
    }

    override fun delete(fileSpec: String, roamingType: RoamingType): Boolean {
      data[roamingType]?.remove(fileSpec)
      return true
    }
  }

  private class TestComponentStore(testAppConfigPath: Path) : ComponentStoreWithExtraComponents() {
    override val serviceContainer: ComponentManagerEx
      get() = ApplicationManager.getApplication() as ComponentManagerEx

    override val storageManager = ApplicationStateStorageManager(pathMacroManager = null, service<SettingsController>())

    init {
      setPath(testAppConfigPath)
    }

    override fun setPath(path: Path) {
      // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
      storageManager.setMacros(listOf(Macro(APP_CONFIG, path), Macro(ROOT_CONFIG, path), Macro(StoragePathMacros.CACHE_FILE, path)))
    }

    override fun error(error: PluginException): Unit = throw error
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

  @Service
  @State(name = "SlowReadService", storages = [Storage(SLOW_READ_FILE_SPEC)], allowLoadInTests = true)
  private class SlowReadService : PersistentStateComponent<TestState> {
    private var state = TestState()

    override fun getState(): TestState = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  @Service
  @State(name = "IndicatorCanceledReadService", storages = [Storage(INDICATOR_CANCELED_READ_FILE_SPEC)], allowLoadInTests = true)
  private class IndicatorCanceledReadService : PersistentStateComponent<TestState> {
    private var state = TestState()

    override fun getState(): TestState = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  @Service
  @State(name = "BrokenReadService", storages = [Storage(BROKEN_READ_FILE_SPEC)], allowLoadInTests = true)
  private class BrokenReadService : PersistentStateComponent<TestState> {
    private var state = TestState()

    override fun getState(): TestState = state

    override fun loadState(state: TestState) {
      this.state = state
    }
  }

  @Service
  @State(name = "PreloadedService", storages = [Storage(PRELOADED_FILE_SPEC)], allowLoadInTests = true)
  private class PreloadedService : A()

  @Service
  @State(name = "NotPreloadedService", storages = [Storage(NOT_PRELOADED_FILE_SPEC)], allowLoadInTests = true)
  private class NotPreloadedService : A()
}

private const val SLOW_READ_FILE_SPEC = "slow-read.xml"
private const val BROKEN_READ_FILE_SPEC = "broken-read.xml"
private const val INDICATOR_CANCELED_READ_FILE_SPEC = "indicator-canceled-read.xml"
private const val PRELOADED_FILE_SPEC = "preloaded.xml"
private const val NOT_PRELOADED_FILE_SPEC = "not-preloaded.xml"

internal data class TestState(@JvmField @Attribute var foo: String = "", @JvmField @Attribute var bar: String = "")

@State(name = "A", storages = [(Storage("a.xml"))], additionalExportDirectory = "foo")
internal open class A : PersistentStateComponent<TestState> {
  @JvmField
  var options = TestState()

  @JvmField
  var loadStateCallback: ((TestState) -> Unit)? = null

  override fun getState() = options

  override fun loadState(state: TestState) {
    loadStateCallback?.invoke(state)
    options = state
  }
}
