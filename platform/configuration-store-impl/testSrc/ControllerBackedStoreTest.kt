// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.settings.*
import com.intellij.platform.settings.local.SettingsControllerMediator
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Text
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path
import kotlin.properties.Delegates

@TestApplication
class ControllerBackedStoreTest {
  @RegisterExtension
  @JvmField
  val fsRule = InMemoryFsExtension()

  private var appConfig: Path by Delegates.notNull()

  private val data = HashMap<String, JsonElement?>()

  @BeforeEach
  fun setUp() {
    appConfig = fsRule.fs.getPath("/app-config")
  }

  @Test
  fun `get`() = runBlocking<Unit>(Dispatchers.Default) {
    val store = createStore { key ->
      if (data.containsKey(key.key)) {
        val data = data.get(key.key)
        assertThat(key.tags.first { it is PersistenceStateComponentPropertyTag && it.componentName == "TestState" })

        if (key.key == "TestState.list") {
          return@createStore GetResult.partial(data!!)
        }

        return@createStore GetResult.resolved(data)
      }

      GetResult.inapplicable()
    }

    val component = TestComponent()
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.foo).isEmpty()
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(bar = "42")

    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(list = listOf("d", "c"))
    store.save(forceSavingAllSettings = true)

    val propertyName = "bar"
    data.put("TestState.$propertyName", encodePrimitiveValue("12"))
    data.put("TestState.text", encodePrimitiveValue("a long sad story"))
    data.put("TestState.list", Json.encodeToJsonElement(listOf("a", "b")))

    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("12")
    assertThat(component.state.text).isEqualTo("a long sad story")
    assertThat(component.state.list).containsExactlyElementsOf(listOf("a", "b"))
  }

  private fun encodePrimitiveValue(v: String) = JsonPrimitive(v)

  @Test
  fun `pass Element`() = runBlocking<Unit>(Dispatchers.Default) {
    var isRequested = false
    var requested: JsonObject? = null
    var saved: JsonObject? = null

    var toReturn: JsonObject? = null

    @Suppress("UNCHECKED_CAST")
    val store = createStore(object : DelegatedSettingsController {
      override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
        toReturn?.let {
          return GetResult.resolved(it as T)
        }

        if (key.key == "TestState") {
          isRequested = true
          requested = key.tags.asSequence().filterIsInstance<OldLocalValueSupplierTag>().first().value?.jsonObject
        }
        return GetResult.inapplicable()
      }

      override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult {
        if (value != null) {
          saved = value as JsonObject
        }
        return SetResult.INAPPLICABLE
      }
  })

    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
    class TestComponentWithElementState : SerializablePersistentStateComponent<Element>(Element("test"))

    val component = TestComponentWithElementState()
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(isRequested).isTrue()
    // no old local value
    assertThat(requested).isNull()
    assertThat(component.state.isEmpty).isTrue()

    @Language("xml")
    val testElementXml = """
      <test answer="42" foo="bar">
        <text>hello</text>
      </test>
    """.trimIndent()
    component.state = JDOMUtil.load(testElementXml)
    store.save(forceSavingAllSettings = true)

    assertThat(jsonDomToXml(saved!!)).isEqualTo(testElementXml)
    assertThat(saved!!.get("name")?.jsonPrimitive?.content).isEqualTo("test")
    assertThat(saved!!.get("attributes")?.jsonObject?.toString()).isEqualTo("""{"answer":"42","foo":"bar"}""")
    assertThat(saved!!.get("children")!!.jsonArray.get(0).jsonObject.get("content")!!.jsonPrimitive.content).isEqualTo("hello")
    assertThat(saved!!).hasSize(3)

    toReturn = saved
    saved = null
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(JDOMUtil.write(component.state)).isEqualTo(testElementXml)
  }

  @Test
  fun `override Element`() = runBlocking<Unit>(Dispatchers.Default) {
    val store = createStore {
      GetResult.resolved(jdomToJson(JDOMUtil.load("""<state foo="42" />""")))
    }

    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
    class TestComponentWithElementState : SerializablePersistentStateComponent<Element>(Element("test"))

    val component = TestComponentWithElementState()
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.getAttributeValue("foo")).isEqualTo("42")
  }

  // null is not set - it means "don't use any value and instead use the initial value of the field"
  @Test
  fun `set primitive to null`() = runBlocking(Dispatchers.Default) {
    val store = createStore { key ->
      if (data.containsKey(key.key)) {
        GetResult.resolved(this@ControllerBackedStoreTest.data.get(key.key))
      }
      else {
        GetResult.inapplicable()
      }
    }

    val component = TestComponent()
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEmpty()

    data.put("TestState.bar", null)
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEmpty()
  }

  @Test
  fun `not applicable`() = runBlocking<Unit>(Dispatchers.Default) {
    val store = ControllerBackedTestComponentStore(
      testAppConfigPath = appConfig,
      controller = SettingsControllerMediator(isPersistenceStateComponentProxy = true),
    )

    val oldContent = """
      <application>
        <component name="TestState" foo="old"/>
      </application>
      """.trimMargin()
    writeConfig(StoragePathMacros.NON_ROAMABLE_FILE, oldContent)

    val component = TestComponent()
    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.foo).isEqualTo("old")
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(bar = "42")
    store.save(forceSavingAllSettings = true)

    store.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("42")
  }

  @Suppress("SameParameterValue")
  private fun writeConfig(fileName: String, @Language("XML") data: String): Path {
    val file = appConfig.resolve(fileName)
    file.write(data)
    return file
  }

  private fun createStore(controller: DelegatedSettingsController): ControllerBackedTestComponentStore {
    return ControllerBackedTestComponentStore(
      testAppConfigPath = appConfig,
      controller = SettingsControllerMediator(
        controllers = listOf(controller),
        isPersistenceStateComponentProxy = true,
      ),
    )
  }

  private fun createStore(supplier: (SettingDescriptor<Any>) -> GetResult<Any>): ControllerBackedTestComponentStore {
    return createStore(object : DelegatedSettingsController {
      override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
        @Suppress("UNCHECKED_CAST")
        return supplier(key as SettingDescriptor<Any>) as GetResult<T>
      }

      override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult = SetResult.INAPPLICABLE
    })
  }
}

private class ControllerBackedTestComponentStore(
  testAppConfigPath: Path,
  controller: SettingsController,
) : ComponentStoreWithExtraComponents() {
  override val serviceContainer: ComponentManagerImpl
    get() = ApplicationManager.getApplication() as ComponentManagerImpl

  override val storageManager = ApplicationStateStorageManager(pathMacroManager = null, controller = controller)

  init {
    setPath(testAppConfigPath)
  }

  override fun setPath(path: Path) {
    // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
    storageManager.setMacros(listOf(Macro(APP_CONFIG, path), Macro(ROOT_CONFIG, path), Macro(StoragePathMacros.CACHE_FILE, path)))
  }
}

@State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)], allowLoadInTests = true)
private class TestComponent : SerializablePersistentStateComponent<ControllerTestState>(ControllerTestState()) {
  override fun noStateLoaded() {
    loadState(ControllerTestState())
  }
}

private data class ControllerTestState(
  @JvmField @Attribute var foo: String = "",
  @JvmField @Attribute var bar: String? = "",
  @JvmField @Text var text: String = "",
  @JvmField @XCollection val list: List<String> = emptyList(),
)