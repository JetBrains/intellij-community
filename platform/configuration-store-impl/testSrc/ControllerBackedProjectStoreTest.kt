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
import com.intellij.platform.settings.OldLocalValueSupplierTag
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
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.jdomToJson
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
  fun `initial controller values are saved with and without an existing file`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (hasLocalState in listOf(false, true)) {
      val file = fsRule.fs.getPath("/project-$hasLocalState/.idea/$FILE_NAME")
      if (hasLocalState) {
        file.write("""<project><component name="$BEAN_COMPONENT_NAME" foo="A" /></project>""")
      }
      val controller = createController(mapOf("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B")))
      val store = TestProjectStore(project.get(), file.parent, controller)
      store.initComponent(BeanTestComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      store.save(forceSavingAllSettings = false)

      val reloaded = BeanTestComponent()
      TestProjectStore(project.get(), file.parent, createController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state).isEqualTo(BeanTestState(foo = if (hasLocalState) "A" else "", bar = "B"))
    }
  }

  @Test
  fun `controller Element changes and resets survive a local save`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (beforeFirstLoad in listOf(false, true)) {
      val file = writeSettings("""<component name="$COMPONENT_NAME"><entry key="backend" value="A" /></component>""")
      val updated = JDOMUtil.load("""
        <component>
          <entry key="backend" value="A" />
          <entry key="frontend" value="B" />
        </component>
      """.trimIndent())
      val values = HashMap<String, JsonElement?>()
      if (beforeFirstLoad) values[COMPONENT_NAME] = jdomToJson(updated)
      val store = TestProjectStore(project.get(), file.parent, createController(values))
      store.initComponent(TestComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      if (!beforeFirstLoad) {
        values[COMPONENT_NAME] = jdomToJson(updated)
        store.reloadStates(setOf(COMPONENT_NAME))
      }
      assertThat(JDOMUtil.load(file).getChild("component").children).hasSize(1)
      store.save(forceSavingAllSettings = false)

      val reloaded = TestComponent()
      TestProjectStore(project.get(), file.parent, createController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state).isEqualTo(updated)

      values[COMPONENT_NAME] = jdomToJson(Element("component"))
      store.reloadStates(setOf(COMPONENT_NAME))
      store.save(forceSavingAllSettings = false)
      assertThat(file).doesNotExist()
    }
  }

  @Test
  fun `controller property changes and resets survive a project reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
    val values = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createController(values))
    val component = BeanTestComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

    values["$BEAN_COMPONENT_NAME.bar"] = JsonPrimitive("B")
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="B" />""")

    values["$BEAN_COMPONENT_NAME.foo"] = null
    values["$BEAN_COMPONENT_NAME.bar"] = null
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(component.state).isEqualTo(BeanTestState())
    store.save(forceSavingAllSettings = false)
    assertThat(file).doesNotExist()
  }

  @Test
  fun `explicit default values stay on disk after a project reload`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME" foo="" bar="B" />""")
    val remoteValues = HashMap<String, JsonElement?>()
    val store = TestProjectStore(project.get(), file.parent, createController(remoteValues))
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
  fun `unchanged numeric and boolean attributes preserve explicit defaults`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class PrimitiveComponent : SerializablePersistentStateComponent<PrimitiveState>(PrimitiveState())

    val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME" count="5" enabled="true" marker="" converted="value-5" />""")
    val originalXml = JDOMUtil.load(file)
    val values = hashMapOf<String, JsonElement?>(
      "$BEAN_COMPONENT_NAME.count" to JsonPrimitive(5),
      "$BEAN_COMPONENT_NAME.enabled" to JsonPrimitive(true),
      "$BEAN_COMPONENT_NAME.converted" to JsonPrimitive("value-5"),
    )
    val expectedLocalValues = mapOf(
      "$BEAN_COMPONENT_NAME.count" to JsonPrimitive("5"),
      "$BEAN_COMPONENT_NAME.enabled" to JsonPrimitive("true"),
      "$BEAN_COMPONENT_NAME.converted" to JsonPrimitive("value-5"),
    )
    val receivedLocalValues = HashMap<String, JsonElement?>()
    val controller = createController(values) { key ->
      receivedLocalValues[key.key] = key.tags.filterIsInstance<OldLocalValueSupplierTag>().single().value
    }
    val store = TestProjectStore(project.get(), file.parent, controller)
    val component = PrimitiveComponent()
    store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    assertThat(receivedLocalValues).isEqualTo(expectedLocalValues)
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    val info = store.getComponents().getValue(BEAN_COMPONENT_NAME)
    assertThat(info.lastModificationCount).isEqualTo(info.currentModificationCount)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo(originalXml)

    values["$BEAN_COMPONENT_NAME.count"] = JsonPrimitive("5")
    values["$BEAN_COMPONENT_NAME.enabled"] = JsonPrimitive("true")
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    assertThat(info.lastModificationCount).isEqualTo(info.currentModificationCount)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file)).isEqualTo(originalXml)

    values["$BEAN_COMPONENT_NAME.count"] = JsonPrimitive(6)
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(component.state.count).isEqualTo(6)
    assertThat(component.state.enabled).isTrue()
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("count")).isEqualTo("6")
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("enabled")).isEqualTo("true")

    values["$BEAN_COMPONENT_NAME.enabled"] = JsonPrimitive(false)
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(component.state.count).isEqualTo(6)
    assertThat(component.state.enabled).isFalse()
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("count")).isEqualTo("6")
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("enabled")).isNull()
  }

  @Test
  fun `controller changes to a collection without a wrapper are saved locally`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME)], allowLoadInTests = true)
    class ListComponent : SerializablePersistentStateComponent<ListState>(ListState())

    for (resetValue in listOf(null, JsonNull)) {
      val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME" marker="keep"><item value="a" /><item value="b" /></component>""")
      val values = HashMap<String, JsonElement?>()
      val originalItems = Json.parseToJsonElement("""["a", "b"]""")
      var receivedLocalItems: JsonElement? = null
      val controller = createController(values) { key ->
        receivedLocalItems = key.tags.filterIsInstance<OldLocalValueSupplierTag>().single().value
      }
      val store = TestProjectStore(project.get(), file.parent, controller)
      val component = ListComponent()
      store.initComponent(component, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      assertThat(component.state.items).containsExactly("a", "b")

      values["$BEAN_COMPONENT_NAME.items"] = resetValue
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      assertThat(receivedLocalItems).isEqualTo(originalItems)
      assertThat(component.state.items).isNullOrEmpty()
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" marker="keep" />""")

      val reloaded = ListComponent()
      TestProjectStore(project.get(), file.parent, createController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state.items).isEmpty()

      values["$BEAN_COMPONENT_NAME.items"] = originalItems
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      assertThat(receivedLocalItems).isNull()
      store.save(forceSavingAllSettings = false)
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      assertThat(receivedLocalItems).isEqualTo(originalItems)
      val info = store.getComponents().getValue(BEAN_COMPONENT_NAME)
      assertThat(info.lastModificationCount).isEqualTo(info.currentModificationCount)

      values["$BEAN_COMPONENT_NAME.items"] = Json.parseToJsonElement("""["c"]""")
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file).getChild("component").getChildren("item").map { it.getAttributeValue("value") }).containsExactly("c")
    }
  }

  @Test
  fun `an existing override skips local comparisons and still loads controller values`() {
    val localState = object : Element("component") {
      override fun getAttributes(): MutableList<org.jdom.Attribute> = error("The comparison must not read the local XML.")
    }
    val tracker = ControllerOverrideTracker()
    tracker.onResolved(controllerValue = JsonPrimitive("remote"), localValue = JsonPrimitive("local"))

    val state = deserializeStateWithController(
      stateElement = localState,
      stateClass = ComparisonListState::class.java,
      mergeInto = null,
      controller = createController(mapOf("$BEAN_COMPONENT_NAME.items" to Json.parseToJsonElement("""["remote"]"""))),
      componentName = BEAN_COMPONENT_NAME,
      pluginId = PluginManagerCore.CORE_ID,
      roamingType = null,
      overrideTracker = tracker,
    )

    assertThat(state!!.items).containsExactly("remote")
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

    for (controllerRootName in listOf(null, "component", "x")) {
      val configDir = fsRule.fs.getPath("/project-$controllerRootName/.idea")
      val file = configDir.resolve("encodings.xml")
      val xml = """
        <project version="4">
          <component name="Encoding" addBOMForNewFiles="with NO BOM" />
        </project>
      """.trimIndent()
      file.write(xml)
      val values = HashMap<String, JsonElement?>()
      if (controllerRootName != null) {
        values["Encoding"] = jdomToJson(Element(controllerRootName).setAttribute("addBOMForNewFiles", "with NO BOM"))
      }
      val store = TestProjectStore(project.get(), configDir, createController(values))
      store.initComponent(EncodingComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file)).isEqualTo(xml)
      store.reloadStates(setOf("Encoding"))
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file)).isEqualTo(xml)

      if (controllerRootName != null) {
        values["Encoding"] = jdomToJson(Element(controllerRootName).setAttribute("addBOMForNewFiles", "with BOM"))
        store.reloadStates(setOf("Encoding"))
        store.save(forceSavingAllSettings = false)
        assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="Encoding" addBOMForNewFiles="with BOM" />""")
      }
    }
  }

  @Test
  fun `controller changes and resets bypass the save threshold`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    @State(name = BEAN_COMPONENT_NAME, storages = [Storage(FILE_NAME, roamingType = RoamingType.DISABLED)], allowLoadInTests = true)
    class ThrottledComponent : SerializablePersistentStateComponent<BeanTestState>(BeanTestState())

    val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME" foo="A" bar="A" />""")
    val values = hashMapOf<String, JsonElement?>("$BEAN_COMPONENT_NAME.bar" to JsonPrimitive("B"))
    val store = TestProjectStore(project.get(), file.parent, createController(values))
    store.initComponent(ThrottledComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component").getAttributeValue("bar")).isEqualTo("B")

    values["$BEAN_COMPONENT_NAME.bar"] = null
    store.reloadStates(setOf(BEAN_COMPONENT_NAME))
    store.save(forceSavingAllSettings = false)
    assertThat(JDOMUtil.load(file).getChild("component")).isEqualTo("""<component name="$BEAN_COMPONENT_NAME" foo="A" />""")
  }

  @Test
  fun `kotlinx controller changes and resets survive a local save`(): Unit = timeoutRunBlocking(context = ClientId.localId.asContextElement()) {
    for (resetValue in listOf(null, JsonNull)) {
      val file = writeSettings("""<component name="$BEAN_COMPONENT_NAME"><![CDATA[{"foo":"A"}]]></component>""")
      val values = HashMap<String, JsonElement?>()
      val store = TestProjectStore(project.get(), file.parent, createController(values))
      store.initComponent(KotlinxComponent(), serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID)

      values[BEAN_COMPONENT_NAME] = Json.parseToJsonElement("""{"foo":"A","bar":"B"}""")
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      store.save(forceSavingAllSettings = false)
      val reloaded = KotlinxComponent()
      TestProjectStore(project.get(), file.parent, createController(emptyMap())).initComponent(
        reloaded, serviceDescriptor = null, pluginId = PluginManagerCore.CORE_ID,
      )
      assertThat(reloaded.state).isEqualTo(KotlinxState(foo = "A", bar = "B"))

      values[BEAN_COMPONENT_NAME] = resetValue
      store.reloadStates(setOf(BEAN_COMPONENT_NAME))
      store.save(forceSavingAllSettings = false)
      assertThat(JDOMUtil.load(file).getChild("component").text).isEqualTo("{}")
    }
  }

  private fun writeSettings(componentXml: String): Path {
    val file = fsRule.fs.getPath("/project/.idea").resolve(FILE_NAME)
    file.write("<project>\n  $componentXml\n</project>")
    return file
  }

  private fun createController(
    remoteValues: Map<String, JsonElement?>,
    onResolved: (SettingDescriptor<*>) -> Unit = {},
  ): SettingsController {
    return SettingsControllerMediator(
      controllers = listOf(object : DelegatedSettingsController {
        override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
          if (!remoteValues.containsKey(key.key)) {
            return GetResult.inapplicable()
          }
          onResolved(key)
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
    override fun noStateLoaded() {
      loadState(BeanTestState())
    }
  }

  private data class BeanTestState(
    @JvmField @Attribute var foo: String = "",
    @JvmField @Attribute var bar: String = "",
  )

  private class NumberConverter : Converter<Int>() {
    override fun fromString(value: String): Int = value.removePrefix("value-").toInt()

    override fun toString(value: Int): String = "value-$value"
  }

  private class PrimitiveState {
    @JvmField @Attribute var count = 0
    @JvmField @Attribute var enabled = false
    @JvmField @Attribute var marker = ""
    @JvmField @Attribute(converter = NumberConverter::class) var converted = 0
  }

  private class ListState {
    @JvmField @Attribute var marker = ""
    @JvmField @Property(surroundWithTag = false) @XCollection(elementName = "item") var items: List<String>? = emptyList()
  }

  private class ComparisonListState {
    @JvmField @Property(surroundWithTag = false) @XCollection(elementName = "item") var items: List<String> = emptyList()
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
