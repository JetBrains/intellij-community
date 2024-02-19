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
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.write
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Text
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import kotlin.properties.Delegates

class ControllerBackedStoreTest {
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
  private var componentStore: ControllerBackedTestComponentStore by Delegates.notNull()

  private val data = HashMap<String, ByteArray>()

  @Before
  fun setUp() {
    testAppConfig = fsRule.fs.getPath("/app-config")
  }

  @Test
  fun `get`() = runBlocking<Unit>(Dispatchers.Default) {
    componentStore = ControllerBackedTestComponentStore(
      testAppConfigPath = testAppConfig,
      controller = SettingsControllerMediator(
        controllers = listOf(object : DelegatedSettingsController {
          @Suppress("UNCHECKED_CAST")
          override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
            data.get(key.key)?.let { data ->
              assertThat(key.tags.first { it is PersistenceStateComponentPropertyTag && it.componentName == "TestState" })

              if (key.key == "TestState.list") {
                return GetResult.partial(data as T)
              }

              return GetResult.resolved(data as T?)
            }

            return GetResult.inapplicable()
          }

          override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult = SetResult.STOP
        }),
        isPersistenceStateComponentProxy = true,
      ),
    )

    val component = TestComponent()
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.foo).isEmpty()
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(bar = "42")

    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(list = listOf("d", "c"))
    componentStore.save(forceSavingAllSettings = true)

    val propertyName = "bar"
    data.put("TestState.$propertyName", "12".toByteArray())
    data.put("TestState.text", "a long sad story".toByteArray())
    data.put("TestState.list", JDOMUtil.write(serializeForController(ControllerTestState(list = listOf("a", "b")))!!).toByteArray())

    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("12")
    assertThat(component.state.text).isEqualTo("a long sad story")
    assertThat(component.state.list).containsExactlyElementsOf(listOf("a", "b"))
  }

  @Test
  fun `pass Element`() = runBlocking<Unit>(Dispatchers.Default) {
    var requested = false
    componentStore = ControllerBackedTestComponentStore(
      testAppConfigPath = testAppConfig,
      controller = SettingsControllerMediator(
        controllers = listOf(object : DelegatedSettingsController {
          override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
            requested = true
            return GetResult.inapplicable()
          }

          override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult = SetResult.STOP
        }),
        isPersistenceStateComponentProxy = true,
      ),
    )

    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
    class TestComponentWithElementState : SerializablePersistentStateComponent<Element>(Element("test"))

    val component = TestComponentWithElementState()
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(requested).isTrue()
    assertThat(component.state.isEmpty).isTrue()
  }

  @Test
  fun `override Element`() = runBlocking<Unit>(Dispatchers.Default) {
    componentStore = ControllerBackedTestComponentStore(
      testAppConfigPath = testAppConfig,
      controller = SettingsControllerMediator(
        controllers = listOf(object : DelegatedSettingsController {
          override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
            @Suppress("UNCHECKED_CAST")
            return GetResult.resolved("""<state foo="42" />""".encodeToByteArray() as T)
          }

          override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult = SetResult.STOP
        }),
        isPersistenceStateComponentProxy = true,
      ),
    )

    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
    class TestComponentWithElementState : SerializablePersistentStateComponent<Element>(Element("test"))

    val component = TestComponentWithElementState()
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.getAttributeValue("foo")).isEqualTo("42")
  }

  @Test
  fun `not applicable`() = runBlocking<Unit>(Dispatchers.Default) {
    componentStore = ControllerBackedTestComponentStore(
      testAppConfigPath = testAppConfig,
      controller = SettingsControllerMediator(isPersistenceStateComponentProxy = true),
    )

    val oldContent = """
      <application>
        <component name="TestState" foo="old"/>
      </application>
      """.trimMargin()
    writeConfig(StoragePathMacros.NON_ROAMABLE_FILE, oldContent)

    val component = TestComponent()
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    assertThat(component.state.foo).isEqualTo("old")
    assertThat(component.state.bar).isEmpty()

    component.state = ControllerTestState(bar = "42")
    componentStore.save(forceSavingAllSettings = true)

    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("42")
  }

  @Suppress("SameParameterValue")
  private fun writeConfig(fileName: String, @Language("XML") data: String): Path {
    val file = testAppConfig.resolve(fileName)
    file.write(data)
    return file
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
  @JvmField @Attribute var bar: String = "",
  @JvmField @Text var text: String = "",
  @JvmField @XCollection val list: List<String> = emptyList(),
)