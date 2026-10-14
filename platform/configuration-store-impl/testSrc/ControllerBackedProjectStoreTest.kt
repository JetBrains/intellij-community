// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(SettingsInternalApi::class)

package com.intellij.configurationStore

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.settings.DelegatedSettingsController
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.local.SettingsControllerMediator
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.rules.InMemoryFsExtension
import com.intellij.util.io.write
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.jdomToJson
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jdom.Element
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.nio.file.Path

@TestApplication
@RegistryKey(key = "store.save.use.modificationCount", value = "true")
class ControllerBackedProjectStoreTest {
  private companion object {
    val project = projectFixture()
    const val COMPONENT_NAME = "ControllerProjectSettings"
    const val BEAN_COMPONENT_NAME = "ControllerProjectBeanSettings"
    const val FILE_NAME = "controllerProjectSettings.xml"
  }

  @RegisterExtension
  @JvmField
  val fsRule = InMemoryFsExtension()

  @Test
  fun `an early controller value initializes the component and notifies listeners once`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (hasLocalState in listOf(false, true)) {
      val file = fsRule.fs.getPath("/project-$hasLocalState/.idea/$FILE_NAME")
      if (hasLocalState) {
        file.write("""
          <project>
            <component name="$COMPONENT_NAME">
              <option name="ASSIGNMENT_WRAP" value="1" />
              <option name="backend" value="A" />
            </component>
          </project>
        """.trimIndent())
      }
      val remoteValues = hashMapOf<String, JsonElement?>(COMPONENT_NAME to jdomToJson(JDOMUtil.load("""
        <component>
          <option name="backend" value="A" />
          <option name="frontend" value="B" />
        </component>
      """.trimIndent())))
      val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
      val component = InitializedElementOptionsComponent()
      val valuesSeenByListeners = ArrayList<String>()
      store.addComponentInitializationListener(object : ComponentInitializationListener {
        override fun onComponentInitialized(stateSpec: State?) {
          assertThat(stateSpec!!.name).isEqualTo(COMPONENT_NAME)
          assertThat(component.initializationCount).isEqualTo(1)
          valuesSeenByListeners.add(component.frontend)
        }
      })
      store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      assertThat(component.initializationCount).isEqualTo(1)
      assertThat(component.loadsBeforeInitialization).isEqualTo(1)
      assertThat(component.loadsAfterInitialization).isEqualTo(if (hasLocalState) 1 else 0)
      assertThat(valuesSeenByListeners).containsExactly("B")
      store.save(forceSavingAllSettings = false)

      remoteValues[COMPONENT_NAME] = jdomToJson(JDOMUtil.load("""
        <component>
          <option name="backend" value="A" />
          <option name="frontend" value="C" />
        </component>
      """.trimIndent()))
      store.reloadStates(setOf(COMPONENT_NAME))
      assertThat(component.initializationCount).isEqualTo(1)
      assertThat(component.loadsAfterInitialization).isEqualTo(if (hasLocalState) 2 else 1)
      assertThat(valuesSeenByListeners).containsExactly("B")
      store.save(forceSavingAllSettings = false)

      val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
        it.getAttributeValue("name") to it.getAttributeValue("value")
      }
      val expected = hashMapOf("backend" to "A", "frontend" to "C")
      if (hasLocalState) expected["ASSIGNMENT_WRAP"] = "1"
      assertThat(values).isEqualTo(expected)
    }
  }

  @Test
  fun `an external XML change refreshes the baseline before a controller reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (storedWrap in listOf(0, 1)) {
      val file = fsRule.fs.getPath("/project-$storedWrap/.idea/$FILE_NAME")
      file.write("""<project><component name="$COMPONENT_NAME"><option name="backend" value="A" /></component></project>""")
      val remoteValues = HashMap<String, JsonElement?>()
      val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
      val component = InitializedElementOptionsComponent()
      var listenerCalls = 0
      store.addComponentInitializationListener(object : ComponentInitializationListener {
        override fun onComponentInitialized(stateSpec: State?) {
          listenerCalls++
        }
      })
      store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      file.write("""
        <project>
          <component name="$COMPONENT_NAME">
            <option name="ASSIGNMENT_WRAP" value="$storedWrap" />
            <option name="backend" value="A" />
          </component>
        </project>
      """.trimIndent())
      remoteValues[COMPONENT_NAME] = jdomToJson(JDOMUtil.load("""
        <component>
          <option name="backend" value="A" />
          <option name="frontend" value="B" />
        </component>
      """.trimIndent()))
      store.reloadState(InitializedElementOptionsComponent::class.java)
      assertThat(component.initializationCount).isEqualTo(1)
      assertThat(component.loadsBeforeInitialization).isEqualTo(1)
      assertThat(component.loadsAfterInitialization).isEqualTo(2)
      assertThat(listenerCalls).isEqualTo(1)
      assertThat(component.frontend).isEqualTo("B")
      assertThat(component.assignmentWrap).isEqualTo(1)
      store.save(forceSavingAllSettings = false)
      val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
        it.getAttributeValue("name") to it.getAttributeValue("value")
      }
      val expected = hashMapOf("backend" to "A", "frontend" to "B")
      if (storedWrap == 1) expected["ASSIGNMENT_WRAP"] = "1"
      assertThat(values).isEqualTo(expected)
    }
  }

  @Test
  fun `an initial controller change is saved without a modification count reset`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.bar).isEqualTo("B")
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />""")

    val reloaded = BeanTestComponent()
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.state).isEqualTo(BeanTestState(foo = "A", bar = "B"))
  }

  @Test
  fun `an initial controller value creates a file for a tracked component`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = fsRule.fs.getPath("/project/.idea/$FILE_NAME")
    val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
    val store = TestProjectStore(project.get(), file.parent, controller)
    store.initComponent(BeanTestComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("bar")).isEqualTo("B")
  }

  @Test
  fun `a controller change persists when loaded state archiving is disabled`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true, useLoadedStateAsExisting = false)
    class UnarchivedComponent : SerializablePersistentStateComponent<BeanTestState>(BeanTestState())

    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
    val store = TestProjectStore(project.get(), file.parent, controller)
    store.initComponent(UnarchivedComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />""")
  }

  @Test
  fun `disabled state archiving does not read the component state after a controller load`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true, useLoadedStateAsExisting = false)
    class UnarchivedComponent : PersistentStateComponent<Element> {
      var stateReads = 0
        private set
      private var value = ""

      override fun getState(): Element {
        stateReads++
        return Element("component").setAttribute("value", value)
      }

      override fun loadState(state: Element) {
        value = state.getAttributeValue("value") ?: ""
      }
    }

    val file = writeBeanSettings("""<component name="$COMPONENT_NAME" value="A" />""")
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(Element("component").setAttribute("value", "B"))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = UnarchivedComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.stateReads).isZero()
    store.save(forceSavingAllSettings = false)
    assertThat(component.stateReads).isEqualTo(1)
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("value")).isEqualTo("B")
  }

  @Test
  fun `a controller override does not read the bean state before saving`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class Component : PersistentStateComponent<BeanTestState> {
      var stateReads = 0
        private set
      private var value = BeanTestState()

      override fun getState(): BeanTestState {
        stateReads++
        return value
      }

      override fun loadState(state: BeanTestState) {
        value = state
      }
    }

    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = Component()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.stateReads).isZero()
    store.save(forceSavingAllSettings = false)
    assertThat(component.stateReads).isEqualTo(1)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />""")
  }

  @Test
  fun `controller changes and resets bypass the save threshold`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME, roamingType = RoamingType.DISABLED)], allowLoadInTests = true)
    class ThrottledComponent : SerializablePersistentStateComponent<BeanTestState>(BeanTestState())

    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="A" />""")
    val values = hashMapOf<String, JsonElement?>("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B"))
    val store = TestProjectStore(project.get(), file.parent, createBeanController(values))
    store.initComponent(ThrottledComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("bar")).isEqualTo("B")

    values["$BEAN_COMPONENT_NAME.bar"] = null
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="" />""")
  }

  @Test
  fun `an initial remote Element load preserves an explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent()))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    withContext(ClientId("initial-load-test").asContextElement()) {
      store.initComponent(ElementOptionsComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    }
    store.save(forceSavingAllSettings = false)
    val reloaded = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.assignmentWrap).isEqualTo(1)
    assertThat(reloaded.backend).isEqualTo("A")
    assertThat(reloaded.frontend).isEqualTo("B")
  }

  @Test
  fun `an initial encoding reset clears the project charset`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = fsRule.fs.getPath("/project/.idea/encodings.xml")
    file.write("""
      <project>
        <component name="Encoding" addBOMForNewFiles="with NO BOM">
          <file url="PROJECT" charset="UTF-16" />
        </component>
      </project>
    """.trimIndent())
    val controller = createBeanController(mapOf("Encoding" to jdomToJson(Element("component"))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl(project.get())
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state!!.getChildren("file")).isEmpty()
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="Encoding" addBOMForNewFiles="with NO BOM" />""")
  }

  @Test
  @RegistryKey(key = "store.save.use.modificationCount", value = "true")
  fun `controller changes survive a project reload and local save`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val configDir = fsRule.fs.getPath("/project/.idea")
    val file = configDir.resolve(FILE_NAME)
    file.write("""
      <project>
        <component name="$COMPONENT_NAME">
          <entry key="backend" value="A" />
        </component>
      </project>
    """.trimIndent())

    val effectiveState = JDOMUtil.load("""
      <component>
        <entry key="backend" value="A" />
        <entry key="frontend" value="B" />
      </component>
    """.trimIndent())
    var remoteState: JsonElement? = null
    var serializedState: JsonElement? = null
    val controller = SettingsControllerMediator(
      controllers = listOf(object : DelegatedSettingsController {
        override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
          val value = remoteState ?: return GetResult.inapplicable()
          @Suppress("UNCHECKED_CAST")
          return GetResult.resolved(value as T)
        }

        override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult {
          serializedState = value as JsonElement?
          return SetResult.inapplicable()
        }
      }),
      isPersistenceStateComponentProxy = true,
    )
    val store = TestProjectStore(project.get(), configDir, controller)
    val component = TestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.children).hasSize(1)

    remoteState = jdomToJson(effectiveState)
    store.reloadStates(setOf(COMPONENT_NAME))
    assertThat(component.state).isEqualTo(effectiveState)
    assertThat(JDOMUtil.load(file).getChild("component").children).hasSize(1)

    store.save(forceSavingAllSettings = false)
    assertThat(serializedState).isEqualTo(remoteState)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$COMPONENT_NAME">
          <entry key="backend" value="A" />
          <entry key="frontend" value="B" />
        </component>
      </project>
    """.trimIndent())

    remoteState = null
    val reloadedComponent = TestComponent()
    TestProjectStore(project.get(), configDir, controller).initComponent(
      reloadedComponent, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloadedComponent.state).isEqualTo(effectiveState)
  }

  @Test
  @RegistryKey(key = "store.save.use.modificationCount", value = "true")
  fun `controller property changes survive a project reload and local save`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    remoteValues.put("$BEAN_COMPONENT_NAME.foo", JsonPrimitive("A"))
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("B"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state).isEqualTo(BeanTestState(foo = "A", bar = "B"))

    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />
      </project>
    """.trimIndent())
  }

  @Test
  @RegistryKey(key = "store.save.use.modificationCount", value = "true")
  fun `controller reset to defaults is saved after a project reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.foo).isEqualTo("A")

    remoteValues.put("$BEAN_COMPONENT_NAME.foo", null)
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state).isEqualTo(BeanTestState())

    store.save(forceSavingAllSettings = false)
    assertThat(file).doesNotExist()
  }

  @Test
  @RegistryKey(key = "store.save.use.modificationCount", value = "true")
  fun `explicit default values stay on disk after a project reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="B" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    remoteValues.put("$BEAN_COMPONENT_NAME.foo", null)
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("B"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state).isEqualTo(BeanTestState(bar = "B"))

    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME" foo="" bar="B" />
      </project>
    """.trimIndent())
  }

  @Test
  fun `the explicit encoding default survives project load and reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = "Encoding", storages = [Storage("encodings.xml")], allowLoadInTests = true)
    class EncodingComponent : PersistentStateComponent<Element> {
      private var bom = "with NO BOM"

      override fun getState(): Element = Element("state").also {
        if (bom != "with NO BOM") {
          it.setAttribute("addBOMForNewFiles", bom)
        }
      }

      override fun loadState(state: Element) {
        bom = state.getAttributeValue("addBOMForNewFiles") ?: "with NO BOM"
      }
    }

    for (resolveLocalValue in listOf(false, true)) {
      val configDir = fsRule.fs.getPath("/project-$resolveLocalValue/.idea")
      val file = configDir.resolve("encodings.xml")
      val xml = """
        <project version="4">
          <component name="Encoding" addBOMForNewFiles="with NO BOM" />
        </project>
      """.trimIndent()
      file.write(xml)
      val values = if (resolveLocalValue) {
        mapOf("Encoding" to jdomToJson(JDOMUtil.load("""<component addBOMForNewFiles="with NO BOM" />""")))
      }
      else {
        emptyMap()
      }
      val store = TestProjectStore(project.get(), configDir, createBeanController(values))
      store.initComponent(EncodingComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file)).isEqualTo(xml)
      store.reloadStates(setOf("Encoding"))
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file)).isEqualTo(xml)
    }
  }

  @Test
  fun `an explicit wrap option survives a change of the IDE default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = "CodeStyle", storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class CodeStyleComponent(private val defaultWrap: Int = 1) : PersistentStateComponent<Element> {
      var assignmentWrap: Int = defaultWrap

      override fun getState(): Element = Element("state").also {
        if (assignmentWrap != defaultWrap) {
          it.addContent(Element("option").setAttribute("name", "ASSIGNMENT_WRAP").setAttribute("value", assignmentWrap.toString()))
        }
      }

      override fun loadState(state: Element) {
        assignmentWrap = state.getChild("option")?.getAttributeValue("value")?.toInt() ?: defaultWrap
      }
    }

    val file = writeBeanSettings("""<component name="CodeStyle"><option name="ASSIGNMENT_WRAP" value="1" /></component>""")
    val originalXml = JDOMUtil.load(file)
    val controller = createBeanController(emptyMap())
    val store = TestProjectStore(project.get(), file.parent, controller)
    val newIdeComponent = CodeStyleComponent(defaultWrap = 1)
    store.initComponent(newIdeComponent, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(newIdeComponent.state.isEmpty).isTrue()
    store.save(forceSavingAllSettings = false)
    store.reloadStates(setOf("CodeStyle"))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo(originalXml)

    val oldIdeComponent = CodeStyleComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, controller).initComponent(
      oldIdeComponent, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(oldIdeComponent.assignmentWrap).isEqualTo(1)
  }

  @Test
  fun `an Element controller change preserves an untouched explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.backend).isEqualTo("A")
    assertThat(component.assignmentWrap).isEqualTo(1)

    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    assertThat(component.backend).isEqualTo("A")
    assertThat(component.frontend).isEqualTo("B")
    assertThat(component.assignmentWrap).isEqualTo(1)
    store.save(forceSavingAllSettings = false)

    val diskState = JDOMUtil.load(file)
    val diskValues = diskState.getChild("component").getChildren("option")
      .associate { it.getAttributeValue("name") to it.getAttributeValue("value") }
    assertThat(diskValues.get("backend")).isEqualTo("A")
    assertThat(diskValues.get("frontend")).isEqualTo("B")

    val oldIdeComponent = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      oldIdeComponent, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(oldIdeComponent.backend).isEqualTo("A")
    assertThat(oldIdeComponent.frontend).isEqualTo("B")
    assertThat(diskValues.get("ASSIGNMENT_WRAP")).isEqualTo("1")
    assertThat(diskState.getChild("component").children).hasSize(3)
    assertThat(oldIdeComponent.assignmentWrap).isEqualTo(1)
  }

  @Test
  fun `an Element controller reset preserves an untouched explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.frontend).isEqualTo("B")

    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    assertThat(component.frontend).isEmpty()
    store.save(forceSavingAllSettings = false)

    val diskState = JDOMUtil.load(file).getChild("component")
    val diskValues = diskState.getChildren("option").associate { it.getAttributeValue("name") to it.getAttributeValue("value") }
    assertThat(diskValues.get("backend")).isEqualTo("A")
    assertThat(diskValues.get("frontend")).isNull()
    assertThat(diskValues.get("ASSIGNMENT_WRAP")).isEqualTo("1")
    assertThat(diskState.children).hasSize(2)

    val reloaded = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.backend).isEqualTo("A")
    assertThat(reloaded.frontend).isEmpty()
    assertThat(reloaded.assignmentWrap).isEqualTo(1)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo(diskState)
  }

  @Test
  fun `an initial Element controller value keeps defaults present in the loaded XML`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(JDOMUtil.load("""
      <component>
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent()))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    store.initComponent(ElementOptionsComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    store.save(forceSavingAllSettings = false)
    val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(values).isEqualTo(mapOf("ASSIGNMENT_WRAP" to "1", "backend" to "A", "frontend" to "B"))
  }

  @Test
  fun `an initial Element controller value preserves an omitted explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent()))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.backend).isEqualTo("A")
    assertThat(component.frontend).isEqualTo("B")
    assertThat(component.assignmentWrap).isEqualTo(1)
    store.save(forceSavingAllSettings = false)

    val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(values).isEqualTo(mapOf("ASSIGNMENT_WRAP" to "1", "backend" to "A", "frontend" to "B"))

    val oldIdeComponent = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      oldIdeComponent, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(oldIdeComponent.assignmentWrap).isEqualTo(1)
    assertThat(oldIdeComponent.backend).isEqualTo("A")
    assertThat(oldIdeComponent.frontend).isEqualTo("B")
  }

  @Test
  fun `an initial Element controller reset removes a nondefault value`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="0" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent()))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.assignmentWrap).isEqualTo(1)
    assertThat(component.backend).isEqualTo("A")
    assertThat(component.frontend).isEqualTo("B")
    store.save(forceSavingAllSettings = false)

    val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(values).isEqualTo(mapOf("backend" to "A", "frontend" to "B"))
    val reloaded = ElementOptionsComponent()
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.assignmentWrap).isEqualTo(1)
    assertThat(reloaded.backend).isEqualTo("A")
    assertThat(reloaded.frontend).isEqualTo("B")
  }

  @Test
  fun `an initial null Element state preserves only the untouched explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())
    val controller = createBeanController(mapOf(COMPONENT_NAME to jdomToJson(Element("component"))))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = ElementOptionsComponent(omitEmptyState = true)
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state).isNull()
    store.save(forceSavingAllSettings = false)
    val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(values).isEqualTo(mapOf("ASSIGNMENT_WRAP" to "1"))

    val reloaded = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.assignmentWrap).isEqualTo(1)
    assertThat(reloaded.frontend).isEmpty()
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").children).hasSize(1)
  }

  @Test
  fun `a remote Element reload preserves the local baseline`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    store.initComponent(ElementOptionsComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load(file).getChild("component").clone().also { it.removeAttribute("name") }))
    withContext(ClientId("project-reload-test").asContextElement()) {
      store.reloadStates(setOf(COMPONENT_NAME))
    }
    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    val values = JDOMUtil.load(file).getChild("component").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(values).isEqualTo(mapOf("ASSIGNMENT_WRAP" to "1", "backend" to "A", "frontend" to "B"))
  }

  @Test
  fun `an Element reset removes a value changed after the first save`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <option name="ASSIGNMENT_WRAP" value="0" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").getChildren("option").first().getAttributeValue("value")).isEqualTo("0")

    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""<component><option name="frontend" value="B" /></component>""")))
    store.reloadStates(setOf(COMPONENT_NAME))
    assertThat(component.assignmentWrap).isEqualTo(1)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$COMPONENT_NAME">
          <option name="frontend" value="B" />
        </component>
      </project>
    """.trimIndent())
  }

  @Test
  fun `a null Element state retains only the untouched explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = ElementOptionsComponent(omitEmptyState = true)
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put(COMPONENT_NAME, jdomToJson(Element("component")))
    store.reloadStates(setOf(COMPONENT_NAME))
    assertThat(component.state).isNull()
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$COMPONENT_NAME">
          <option name="ASSIGNMENT_WRAP" value="1" />
        </component>
      </project>
    """.trimIndent())
    val reloaded = ElementOptionsComponent(defaultWrap = 0)
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.assignmentWrap).isEqualTo(1)
    assertThat(reloaded.frontend).isEmpty()
  }

  @Test
  fun `an Element save does not restore an explicit default removed externally`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
        <option name="backend" value="A" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = ElementOptionsComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    file.write("""<project><component name="$COMPONENT_NAME"><option name="backend" value="A" /></component></project>""")
    store.reloadState(ElementOptionsComponent::class.java)
    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <option name="backend" value="A" />
        <option name="frontend" value="B" />
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$COMPONENT_NAME">
          <option name="backend" value="A" />
          <option name="frontend" value="B" />
        </component>
      </project>
    """.trimIndent())
  }

  @Test
  fun `an Element change keeps nested defaults and removes deleted list entries`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class StructuredComponent : SerializablePersistentStateComponent<Element>(Element("component")) {
      override fun loadState(state: Element) {
        val normalized = state.clone()
        normalized.getChild("settings")?.getChildren("option")?.filter {
          it.getAttributeValue("name") == "explicitDefault" && it.getAttributeValue("value") == "1"
        }?.forEach { it.detach() }
        super.loadState(normalized)
      }
    }

    val file = writeBeanSettings("""
      <component name="$COMPONENT_NAME">
        <settings>
          <option name="explicitDefault" value="1" />
          <option name="changed" value="old" />
        </settings>
        <items><item value="A" /><item value="B" /></items>
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    store.initComponent(StructuredComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put(COMPONENT_NAME, jdomToJson(JDOMUtil.load("""
      <component>
        <settings><option name="changed" value="new" /></settings>
        <items><item value="B" /></items>
      </component>
    """.trimIndent())))
    store.reloadStates(setOf(COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    val diskState = JDOMUtil.load(file).getChild("component")
    val options = diskState.getChild("settings").getChildren("option").associate {
      it.getAttributeValue("name") to it.getAttributeValue("value")
    }
    assertThat(options).isEqualTo(mapOf("explicitDefault" to "1", "changed" to "new"))
    assertThat(diskState.getChild("items").children.map { it.getAttributeValue("value") }).containsExactly("B")
  }

  @Test
  fun `an Element attribute change preserves an explicit text default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class TextComponent : PersistentStateComponent<Element> {
      private var text = "default text"
      private var other = ""

      override fun getState(): Element = Element("component").also {
        if (text != "default text") it.text = text
        if (other.isNotEmpty()) it.setAttribute("other", other)
      }

      override fun loadState(state: Element) {
        text = state.text.ifEmpty { "default text" }
        other = state.getAttributeValue("other") ?: ""
      }
    }

    val file = writeBeanSettings("""<component name="$COMPONENT_NAME" other="A">default text</component>""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    store.initComponent(TextComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put(COMPONENT_NAME, jdomToJson(Element("component").setAttribute("other", "B")))
    store.reloadStates(setOf(COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    val diskState = JDOMUtil.load(file).getChild("component")
    assertThat(diskState.getAttributeValue("other")).isEqualTo("B")
    assertThat(diskState.text).isEqualTo("default text")
  }

  @Test
  fun `a serialization filter does not hide a controller reset`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class FilteredComponent : SerializablePersistentStateComponent<FilteredState>(FilteredState()) {
      override fun noStateLoaded() {
        loadState(FilteredState())
      }
    }

    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = FilteredComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.foo).isEqualTo("A")

    remoteValues.put("$BEAN_COMPONENT_NAME.foo", null)
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("B"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state.foo).isEmpty()
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME" bar="B" />
      </project>
    """.trimIndent())
  }

  @Test
  fun `an unchanged alwaysWrite default preserves the XML after normalization`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="UPPER" />""")
    val originalXml = JDOMUtil.load(file)
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = NormalizingComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put("$BEAN_COMPONENT_NAME.foo", null)
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("UPPER"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state.bar).isEqualTo("upper")

    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo(originalXml)
  }

  @Test
  fun `controller changes persist after loadState normalization`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="OLD" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = NormalizingComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put("$BEAN_COMPONENT_NAME.foo", null)
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("NEW"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state.bar).isEqualTo("new")

    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME" foo="" bar="new" />
      </project>
    """.trimIndent())
  }

  @Test
  fun `a controller change preserves an explicit default of another property`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="A" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    store.initComponent(BeanTestComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put("$BEAN_COMPONENT_NAME.bar", JsonPrimitive("B"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)

    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME" bar="B" foo="" />
      </project>
    """.trimIndent())
    val reloaded = BeanTestComponent()
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.state).isEqualTo(BeanTestState(bar = "B"))
  }

  @Test
  fun `local changes after a controller save preserve an untouched explicit default`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (newValue in listOf("C", "")) {
      val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="A" />""")
      val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
      val store = TestProjectStore(project.get(), file.parent, controller)
      val component = BeanTestComponent()
      store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("foo")).isEqualTo("")

      component.setBar(newValue)
      store.save(forceSavingAllSettings = false)
      val stored = JDOMUtil.load(file).getChild("component")
      assertThat(stored.getAttributeValue("foo")).isEqualTo("")
      assertThat(stored.getAttributeValue("bar")).isEqualTo(newValue.takeIf { it.isNotEmpty() })

      val reloaded = BeanTestComponent()
      TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state).isEqualTo(BeanTestState(bar = newValue))
    }
  }

  @Test
  fun `an unchanged controller load preserves explicit defaults for later local changes`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="A" />""")
    val controller = createBeanController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("A")))
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    for (value in listOf("B", "")) {
      component.setBar(value)
      store.save(forceSavingAllSettings = false)
      val stored = JDOMUtil.load(file).getChild("component")
      assertThat(stored.getAttributeValue("foo")).isEqualTo("")
      assertThat(stored.getAttributeValue("bar")).isEqualTo(value.takeIf { it.isNotEmpty() })
    }
  }

  @Test
  fun `a controller change preserves an explicit default option`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class OptionsComponent : SerializablePersistentStateComponent<OptionsState>(OptionsState())

    val file = writeBeanSettings("""
      <component name="$BEAN_COMPONENT_NAME">
        <option name="ASSIGNMENT_WRAP" value="1" />
      </component>
    """.trimIndent())
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    store.initComponent(OptionsComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    remoteValues.put("$BEAN_COMPONENT_NAME.other", JsonPrimitive("changed"))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)

    assertThat(JDOMUtil.load(file)).isEqualTo("""
      <project>
        <component name="$BEAN_COMPONENT_NAME">
          <option name="other" value="changed" />
          <option name="ASSIGNMENT_WRAP" value="1" />
        </component>
      </project>
    """.trimIndent())
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").children).hasSize(2)
  }

  @Test
  fun `kotlinx controller changes survive a project reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME"><![CDATA[{"foo":"A"}]]></component>""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
    val component = KotlinxComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(component.state.foo).isEqualTo("A")
    remoteValues.put(BEAN_COMPONENT_NAME, Json.parseToJsonElement("""{"foo":"A","bar":"B"}"""))
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)

    val reloaded = KotlinxComponent()
    TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
      reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
    )
    assertThat(reloaded.state).isEqualTo(KotlinxState(foo = "A", bar = "B"))
  }

  @Test
  fun `kotlinx controller null resets survive a project reload`(): Unit = checkKotlinxReset(controllerValue = null)

  @Test
  fun `kotlinx controller JsonNull resets survive a project reload`(): Unit = checkKotlinxReset(controllerValue = JsonNull)

  private fun checkKotlinxReset(controllerValue: JsonElement?): Unit =
    timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
      val file = writeBeanSettings("""<component name="$BEAN_COMPONENT_NAME"><![CDATA[{"foo":"A","bar":"B"}]]></component>""")
      val remoteValues = HashMap<String, JsonElement?>()
      val store = TestProjectStore(project.get(), file.parent, createBeanController(remoteValues))
      val component = KotlinxComponent()
      store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      assertThat(component.state).isEqualTo(KotlinxState(foo = "A", bar = "B"))
      remoteValues.put(BEAN_COMPONENT_NAME, controllerValue)
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      assertThat(component.state).isEqualTo(KotlinxState())

      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file).getChild("component").text).isEqualTo("{}")
      val reloaded = KotlinxComponent()
      TestProjectStore(project.get(), file.parent, createBeanController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state).isEqualTo(KotlinxState())
    }

  private fun writeBeanSettings(componentXml: String): Path {
    val file = fsRule.fs.getPath("/project/.idea").resolve(FILE_NAME)
    file.write("<project>\n  $componentXml\n</project>")
    return file
  }

  private fun createBeanController(remoteValues: Map<String, JsonElement?>): SettingsController {
    return SettingsControllerMediator(
      controllers = listOf(object : DelegatedSettingsController {
        override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
          if (!remoteValues.containsKey(key.key)) {
            return GetResult.inapplicable()
          }
          @Suppress("UNCHECKED_CAST")
          return GetResult.resolved(remoteValues.get(key.key) as T?)
        }

        override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult = SetResult.inapplicable()
      }),
      isPersistenceStateComponentProxy = true,
    )
  }

  @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], useLoadedStateAsExisting = true, allowLoadInTests = true)
  private class TestComponent : SerializablePersistentStateComponent<Element>(Element("component"))

  @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
  private class BeanTestComponent : SerializablePersistentStateComponent<BeanTestState>(BeanTestState()) {
    fun setBar(value: String) {
      updateState { it.copy(bar = value) }
    }

    override fun noStateLoaded() {
      loadState(BeanTestState())
    }
  }

  private data class BeanTestState(
    @JvmField @Attribute var foo: String = "",
    @JvmField @Attribute var bar: String = "",
  )

  private data class OptionsState(
    @JvmField var ASSIGNMENT_WRAP: Int = 1,
    @JvmField var other: String = "",
  )

  @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
  private open class ElementOptionsComponent(private val defaultWrap: Int = 1, private val omitEmptyState: Boolean = false) : PersistentStateComponent<Element> {
    var assignmentWrap: Int = defaultWrap
    var backend: String = ""
    var frontend: String = ""

    override fun getState(): Element? = Element("component").also {
      if (assignmentWrap != defaultWrap) {
        it.addContent(Element("option").setAttribute("name", "ASSIGNMENT_WRAP").setAttribute("value", assignmentWrap.toString()))
      }
      if (backend.isNotEmpty()) {
        it.addContent(Element("option").setAttribute("name", "backend").setAttribute("value", backend))
      }
      if (frontend.isNotEmpty()) {
        it.addContent(Element("option").setAttribute("name", "frontend").setAttribute("value", frontend))
      }
    }.takeUnless { omitEmptyState && it.isEmpty }

    override fun loadState(state: Element) {
      val values = state.getChildren("option").associate { it.getAttributeValue("name") to it.getAttributeValue("value") }
      assignmentWrap = values.get("ASSIGNMENT_WRAP")?.toInt() ?: defaultWrap
      backend = values.get("backend") ?: ""
      frontend = values.get("frontend") ?: ""
    }

    override fun noStateLoaded() {
      loadState(Element("component"))
    }
  }

  @State(name = COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
  private class InitializedElementOptionsComponent : ElementOptionsComponent() {
    var initializationCount = 0
      private set
    var loadsBeforeInitialization = 0
      private set
    var loadsAfterInitialization = 0
      private set

    override fun initializeComponent() {
      check(initializationCount == 0)
      initializationCount++
    }

    override fun getState(): Element? {
      check(initializationCount == 1)
      return super.getState()
    }

    override fun loadState(state: Element) {
      if (initializationCount == 0) loadsBeforeInitialization++ else loadsAfterInitialization++
      super.loadState(state)
    }
  }

  private data class FilteredState(
    @JvmField @Attribute @Property(filter = RejectPropertyFilter::class) var foo: String = "",
    @JvmField @Attribute var bar: String = "",
  )

  class RejectPropertyFilter : SerializationFilter {
    override fun accepts(accessor: Accessor, bean: Any): Boolean = false
  }

  private data class AlwaysWriteState(
    @JvmField @Attribute @Property(alwaysWrite = true) var foo: String = "",
    @JvmField @Attribute var bar: String = "",
  )

  @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
  private class NormalizingComponent : SerializablePersistentStateComponent<AlwaysWriteState>(AlwaysWriteState()) {
    override fun loadState(state: AlwaysWriteState) {
      super.loadState(state.copy(bar = state.bar.lowercase()))
    }
  }

  @Serializable
  private data class KotlinxState(var foo: String = "", var bar: String = "")

  @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
  private class KotlinxComponent : SerializablePersistentStateComponent<KotlinxState>(KotlinxState()) {
    override fun noStateLoaded() {
      loadState(KotlinxState())
    }
  }

  private class TestProjectStore(
    override val project: Project,
    configDir: Path,
    controller: SettingsController,
  ) : ComponentStoreImpl() {
    override val storageManager = object : StateStorageManagerImpl("project", componentManager = null, controller = controller) {
      override val isUseXmlProlog: Boolean = false

      override fun expandMacro(collapsedPath: String): Path =
        if (collapsedPath.startsWith('$')) super.expandMacro(collapsedPath) else configDir.resolve(collapsedPath)
    }

    override var isStoreInitialized: Boolean = false

    init {
      setPath(configDir)
    }

    override fun setPath(path: Path) {
      storageManager.setMacros(listOf(Macro(PROJECT_CONFIG_DIR, path)))
      isStoreInitialized = true
    }
  }
}
