// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.configurationStore.schemeManager.ROOT_CONFIG
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.ExceptionUtil
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.properties.Delegates

internal class ComponentStoreModificationTrackerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ApplicationRule()
  }

  private var testAppConfig: Path by Delegates.notNull()
  private var componentStore: MyComponentStore by Delegates.notNull()

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Before
  fun setUp() {
    testAppConfig = fsRule.fs.getPath("/config")
    componentStore = MyComponentStore(testAppConfig)
  }

  @Test
  fun `modification tracker`() = runBlocking<Unit> {
    @State(name = "modificationTrackerA", storages = [(Storage("a.xml"))])
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
    componentStore.initComponent(component, null, null)

    assertThat(component.modificationCount).isEqualTo(0)
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // test that store correctly set last modification count to component modification count on init
    component.lastGetStateStackTrace = null
    componentStore.save()
    @Suppress("USELESS_CAST")
    assertThat(component.lastGetStateStackTrace as String?).isNull()
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // change modification count - store will be forced to check changes using serialization and A.getState will be called
    component.incModificationCount()
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    // test that store correctly save last modification time and doesn't call our state on next save
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    val componentFile = testAppConfig.resolve("a.xml")
    assertThat(componentFile).doesNotExist()

    // update data but "forget" to update modification count
    component.options.foo = "new"

    componentStore.save()
    assertThat(componentFile).doesNotExist()

    component.incModificationCount()
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(2)

    assertThat(componentFile).hasContent("""
    <application>
      <component name="modificationTrackerA" foo="new" />
    </application>""".trimIndent())
  }

  @Test
  fun persistentStateComponentWithModificationTracker() = runBlocking<Unit> {
    @State(name = "TestPersistentStateComponentWithModificationTracker", storages = [(Storage("b.xml"))])
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
    componentStore.initComponent(component, null, null)

    assertThat(component.modificationCount.get()).isEqualTo(0)
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // test that store correctly set last modification count to component modification count on init
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(0)

    // change modification count - store will be forced to check changes using serialization and A.getState will be called
    component.incModificationCount()
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    // test that store correctly save last modification time and doesn't call our state on next save
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(1)

    val componentFile = testAppConfig.resolve("b.xml")
    assertThat(componentFile).doesNotExist()

    // update data but "forget" to update modification count
    component.options.foo = "new"

    componentStore.save()
    assertThat(componentFile).doesNotExist()

    component.incModificationCount()
    componentStore.save()
    assertThat(component.stateCalledCount.get()).isEqualTo(2)

    assertThat(componentFile).hasContent("""
    <application>
      <component name="TestPersistentStateComponentWithModificationTracker" foo="new" />
    </application>""".trimIndent())
  }
}

private class MyComponentStore(testAppConfigPath: Path) : ChildlessComponentStore() {
  private class MyStorageManager : StateStorageManagerImpl("application") {
    override fun getFileBasedStorageConfiguration(fileSpec: String) = appFileBasedStorageConfiguration

    override val isUseXmlProlog = false

    override fun normalizeFileSpec(fileSpec: String) = removeMacroIfStartsWith(super.normalizeFileSpec(fileSpec), APP_CONFIG)

    override fun expandMacro(collapsedPath: String): Path = if (collapsedPath[0] == '$') super.expandMacro(collapsedPath) else macros.get(0).value.resolve(collapsedPath)
  }

  override val storageManager: StateStorageManagerImpl = MyStorageManager()

  init {
    setPath(testAppConfigPath)
  }

  override fun setPath(path: Path) {
    // yes, in tests APP_CONFIG equals to ROOT_CONFIG (as ICS does)
    storageManager.setMacros(listOf(Macro(APP_CONFIG, path), Macro(ROOT_CONFIG, path)))
  }
}