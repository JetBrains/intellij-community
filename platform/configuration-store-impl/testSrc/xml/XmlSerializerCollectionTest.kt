@file:Suppress("DEPRECATION")

package com.intellij.configurationStore.xml

import com.intellij.configurationStore.deserialize
import com.intellij.ide.plugins.PluginBean
import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.SmartList
import com.intellij.util.element
import com.intellij.util.loadElement
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.AbstractCollection
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element
import org.junit.Test
import java.util.*

internal class XmlSerializerCollectionTest {
  @Test fun jdomExternalizableStringList() {
    val bean = Bean3()
    bean.list.add("\u0001one")
    bean.list.add("two")
    bean.list.add("three")
    testSerializer(
      """
      <b>
        <list>
          <item value="one" />
          <item value="two" />
          <item value="three" />
        </list>
      </b>""",
      bean, SkipDefaultsSerializationFilter())
  }

  @Test
  fun jdomExternalizableStringListWithoutClassAttribute() {
    val testList = arrayOf("foo", "bar")
    val element = Element("test")
    val listElement = element.element("list")
    for (id in testList) {
      listElement.addContent(Element("item").setAttribute("itemvalue", id))
    }

    val result = SmartList<String>()
    JDOMExternalizableStringList.readList(result, element)
    assertThat(result).isEqualTo(testList.toList())
  }

  @Test fun collectionBean() {
    val bean = Bean4()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    testSerializer("<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun collectionBeanReadJDOMExternalizableStringList() {
    @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    val list = JDOMExternalizableStringList()
    list.add("one")
    list.add("two")
    list.add("three")

    val value = Element("value")
    list.writeExternal(value)
    val o = Element("state").addContent(Element("option").setAttribute("name", "myList").addContent(value)).deserialize<Bean4>()
    assertSerializer(o, "<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", SkipDefaultsSerializationFilter())
  }

  @Test fun polymorphicArray() {
    @Tag("bean")
    class BeanWithPolymorphicArray {
      @AbstractCollection(elementTypes = arrayOf(BeanWithPublicFields::class, BeanWithPublicFieldsDescendant::class))
      var v = arrayOf<BeanWithPublicFields>()
    }

    val bean = BeanWithPolymorphicArray()

    testSerializer("<bean>\n  <option name=\"v\">\n    <array />\n  </option>\n</bean>", bean)

    bean.v = arrayOf(BeanWithPublicFields(), BeanWithPublicFieldsDescendant(), BeanWithPublicFields())

    testSerializer("""<bean>
  <option name="v">
    <array>
      <BeanWithPublicFields>
        <option name="INT_V" value="1" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFields>
      <BeanWithPublicFieldsDescendant>
        <option name="NEW_S" value="foo" />
        <option name="INT_V" value="1" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFieldsDescendant>
      <BeanWithPublicFields>
        <option name="INT_V" value="1" />
        <option name="STRING_V" value="hello" />
      </BeanWithPublicFields>
    </array>
  </option>
</bean>""", bean)
  }

  @Test fun xCollection() {
    val bean = BeanWithArrayWithoutTagName()
     testSerializer(
     """
      <BeanWithArrayWithoutTagName>
        <option name="foo">
          <option value="a" />
        </option>
      </BeanWithArrayWithoutTagName>""".trimIndent(), bean)
  }

  @Test fun arrayAnnotationWithElementTag() {
    @Tag("bean") class Bean {
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v")
      var v = arrayOf("a", "b")
    }

    val bean = Bean()

    testSerializer("""
    <bean>
      <option name="v">
        <array>
          <vvalue v="a" />
          <vvalue v="b" />
        </array>
      </option>
    </bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    testSerializer(
      "<bean>\n" + "  <option name=\"v\">\n" + "    <array>\n" + "      <vvalue v=\"1\" />\n" + "      <vvalue v=\"2\" />\n" + "      <vvalue v=\"3\" />\n" + "    </array>\n" + "  </option>\n" + "</bean>",
      bean)
  }

  @Test fun arrayWithoutTag() {
    @Tag("bean")
    class Bean {
      @XCollection(elementName = "vvalue", valueAttributeName = "v")
      var v = arrayOf("a", "b")
      var INT_V = 1
    }

    val bean = Bean()

    testSerializer("""
    <bean>
      <option name="INT_V" value="1" />
      <option name="v">
        <vvalue v="a" />
        <vvalue v="b" />
      </option>
    </bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    testSerializer("""
    <bean>
      <option name="INT_V" value="1" />
      <option name="v">
        <vvalue v="1" />
        <vvalue v="2" />
        <vvalue v="3" />
      </option>
    </bean>""", bean)
  }

  private data class BeanWithArray(var ARRAY_V: Array<String> = arrayOf("a", "b"))

  @Test fun array() {
    val bean = BeanWithArray()
    testSerializer(
      "<BeanWithArray>\n  <option name=\"ARRAY_V\">\n    <array>\n      <option value=\"a\" />\n      <option value=\"b\" />\n    </array>\n  </option>\n</BeanWithArray>",
      bean)

    bean.ARRAY_V = arrayOf("1", "2", "3", "")
    testSerializer(
      "<BeanWithArray>\n  <option name=\"ARRAY_V\">\n    <array>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n      <option value=\"\" />\n    </array>\n  </option>\n</BeanWithArray>",
      bean)
  }

  private class BeanWithList {
    var VALUES: List<String> = ArrayList(Arrays.asList("a", "b", "c"))
  }

  @Test fun ListSerialization() {
    val bean = BeanWithList()

    testSerializer(
      "<BeanWithList>\n  <option name=\"VALUES\">\n    <list>\n      <option value=\"a\" />\n      <option value=\"b\" />\n      <option value=\"c\" />\n    </list>\n  </option>\n</BeanWithList>",
      bean)

    bean.VALUES = ArrayList(Arrays.asList("1", "2", "3"))

    testSerializer(
      "<BeanWithList>\n  <option name=\"VALUES\">\n    <list>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n    </list>\n  </option>\n</BeanWithList>",
      bean)
  }

  internal class BeanWithSet {
    var VALUES: Set<String> = LinkedHashSet(Arrays.asList("a", "b", "w"))
  }

  @Test fun SetSerialization() {
    val bean = BeanWithSet()
    testSerializer(
      "<BeanWithSet>\n  <option name=\"VALUES\">\n    <set>\n      <option value=\"a\" />\n      <option value=\"b\" />\n      <option value=\"w\" />\n    </set>\n  </option>\n</BeanWithSet>",
      bean)
    bean.VALUES = LinkedHashSet(Arrays.asList("1", "2", "3"))

    testSerializer(
      "<BeanWithSet>\n  <option name=\"VALUES\">\n    <set>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n    </set>\n  </option>\n</BeanWithSet>",
      bean)
  }

  @Test fun testPropertyAndNoSurround() {
    val bean = XmlSerializer.deserialize<PluginBean>(loadElement("""<idea-plugin>
          <id>com.intellij.database.ide</id>
          <name>DataGrip Customization</name>
          <vendor>JetBrains</vendor>
          <category>Database</category>

          <depends>com.intellij.modules.datagrip</depends>
          <depends>com.intellij.database</depends>

          <extensions defaultExtensionNs="com.intellij">
            <projectViewPane implementation="com.intellij.database.ide.DatabaseProjectViewPane"/>
            <directoryProjectConfigurator implementation="com.intellij.database.ide.DatabaseProjectConfigurator"/>
            <nonProjectFileWritingAccessExtension implementation="com.intellij.database.vfs.DbNonProjectFileWritingAccessExtension"/>

            <applicationService serviceInterface="com.intellij.openapi.wm.impl.FrameTitleBuilder"
                                serviceImplementation="com.intellij.database.ide.DatabaseFrameTitleBuilder"
                                overrides="true"/>
            <applicationService serviceInterface="com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter"
                                serviceImplementation="com.intellij.database.ide.DatabaseEditorEmptyTextPainter"
                                overrides="true"/>
            <applicationService serviceInterface="com.intellij.ide.RecentProjectsManager"
                                serviceImplementation="com.intellij.database.ide.DatabaseRecentProjectManager"
                                overrides="true"/>
            <projectService serviceInterface="com.intellij.ide.projectView.ProjectView"
                            serviceImplementation="com.intellij.database.ide.DatabaseProjectView"
                            overrides="true"/>
            <projectService serviceInterface="com.intellij.psi.search.ProjectScopeBuilder"
                            serviceImplementation="com.intellij.database.ide.DatabaseProjectScopeBuilder"
                            overrides="true"/>

            <goto.nonProjectScopeDisabler/>
          </extensions>

          <application-components>
            <component>
              <implementation-class>com.intellij.database.ide.DataGripInitialConfigurator</implementation-class>
              <headless-implementation-class/>
            </component>
          </application-components>

          <project-components>
            <component>
              <option name="overrides" value="true"/>
              <interface-class>com.intellij.database.autoconfig.DatabaseConfigFileWatcher</interface-class>
              <implementation-class/>
            </component>
          </project-components>

          <actions>
            <action id="DBE.AddContentRoot" class="com.intellij.database.ide.actions.AddContentRootAction" text="Attach Directory">
              <add-to-group group-id="ProjectViewPopupMenu" anchor="before" relative-to-action="WeighingNewGroup"/>
              <add-to-group group-id="FileOpenGroup" anchor="last"/>
            </action>
            <group id="NewProjectOrModuleGroup">
              <action id="NewProject" class="com.intellij.database.ide.actions.NewProjectAction" text="Project..."/>
              <separator/>
            </group>

            <action id="NewSqlFile" class="com.intellij.database.ide.actions.NewSqlFileAction">
              <add-to-group group-id="DBE.NewFile" anchor="first"/>
            </action>

            <action overrides="true" id="GotoClass" class="com.intellij.ide.actions.GotoClassAction" text="Table/Class..."/>

            <reference id="DatabaseView.PropertiesAction">
              <add-to-group group-id="FileMainSettingsGroup" anchor="after" relative-to-action="ShowSettings"/>
            </reference>

            <action overrides="true" id="DatabaseView.ImportDataSources" class="com.intellij.openapi.actionSystem.EmptyAction"
                    text="Import from Sources..."/>
          </actions>
        </idea-plugin>"""), PluginBean::class.java)

    assertThat(bean.actions.joinToString("\n") { JDOMUtil.writeElement(it) }).isEqualTo("""
      <actions>
        <action id="DBE.AddContentRoot" class="com.intellij.database.ide.actions.AddContentRootAction" text="Attach Directory">
          <add-to-group group-id="ProjectViewPopupMenu" anchor="before" relative-to-action="WeighingNewGroup" />
          <add-to-group group-id="FileOpenGroup" anchor="last" />
        </action>
        <group id="NewProjectOrModuleGroup">
          <action id="NewProject" class="com.intellij.database.ide.actions.NewProjectAction" text="Project..." />
          <separator />
        </group>
        <action id="NewSqlFile" class="com.intellij.database.ide.actions.NewSqlFileAction">
          <add-to-group group-id="DBE.NewFile" anchor="first" />
        </action>
        <action overrides="true" id="GotoClass" class="com.intellij.ide.actions.GotoClassAction" text="Table/Class..." />
        <reference id="DatabaseView.PropertiesAction">
          <add-to-group group-id="FileMainSettingsGroup" anchor="after" relative-to-action="ShowSettings" />
        </reference>
        <action overrides="true" id="DatabaseView.ImportDataSources" class="com.intellij.openapi.actionSystem.EmptyAction" text="Import from Sources..." />
      </actions>""".trimIndent())
  }
}

@Tag("b")
private class Bean3 {
  @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
  var list = JDOMExternalizableStringList()
}

@Tag("b")
private class Bean4 {
  @CollectionBean
  val list = SmartList<String>()
}

private class BeanWithArrayWithoutTagName {
  @XCollection
  var foo = arrayOf("a")
}