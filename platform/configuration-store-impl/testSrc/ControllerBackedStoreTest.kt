// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.platform.settings.DelegatedSettingsController
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.local.SettingsControllerMediator
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    componentStore = ControllerBackedTestComponentStore(
      testAppConfigPath = testAppConfig,
      controller = SettingsControllerMediator(
        controllers = listOf(object : DelegatedSettingsController {
          override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
            data.get(key.key)?.let {
              @Suppress("UNCHECKED_CAST")
              return GetResult.resolved(it as T?)
            }
            return GetResult.inapplicable()
          }

          override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): Boolean {
            return true
          }
        }),
        isPersistenceStateComponentProxy = true,
      ),
    )
  }

  @Test
  fun `settingsController - cache storage`() = runBlocking<Unit>(Dispatchers.Default) {
    @State(name = "TestState", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE)])
    class Component : SerializablePersistentStateComponent<TestState>(TestState())

    val component = Component()
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = null)

    assertThat(component.state.foo).isEmpty()
    assertThat(component.state.bar).isEmpty()

    component.state = TestState(bar = "42")

    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = null)
    assertThat(component.state.bar).isEqualTo("42")

    val propertyName = "bar"
    data.put("TestState.$propertyName", """
      <s $propertyName="12" />
    """.trimIndent().toByteArray())
    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = null)
    assertThat(component.state.bar).isEqualTo("12")
  }
}

private class ControllerBackedTestComponentStore(
  testAppConfigPath: Path,
  controller: SettingsController,
) : ComponentStoreWithExtraComponents() {
  override val serviceContainer: ComponentManagerImpl
    get() = ApplicationManager.getApplication() as ComponentManagerImpl

  override val storageManager = ApplicationStoreImpl.ApplicationStateStorageManager(pathMacroManager = null, controller)

  init {
    setPath(testAppConfigPath)
  }

  override fun setPath(path: Path) {
    // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
    storageManager.setMacros(listOf(Macro(APP_CONFIG, path), Macro(ROOT_CONFIG, path), Macro(StoragePathMacros.CACHE_FILE, path)))
  }
}
