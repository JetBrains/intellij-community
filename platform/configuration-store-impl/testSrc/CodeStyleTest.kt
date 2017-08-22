package com.intellij.configurationStore

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider
import com.intellij.psi.codeStyle.CustomCodeStyleSettings
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.loadElement
import org.assertj.core.api.Assertions.assertThat
import org.jdom.Element
import org.junit.ClassRule
import org.junit.Test

class CodeStyleTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  @Test fun `do not remove unknown`() {
    val settings = CodeStyleSettings()
    val loaded = """
    <code_scheme name="testSchemeName" version="${CodeStyleSettings.CURR_VERSION}">
      <UnknownDoNotRemoveMe>
        <option name="ALIGN_OBJECT_PROPERTIES" value="2" />
      </UnknownDoNotRemoveMe>
      <codeStyleSettings language="CoffeeScript">
        <option name="KEEP_SIMPLE_METHODS_IN_ONE_LINE" value="true" />
      </codeStyleSettings>
      <codeStyleSettings language="Gherkin">
        <indentOptions>
          <option name="USE_TAB_CHARACTER" value="true" />
        </indentOptions>
      </codeStyleSettings>
      <codeStyleSettings language="SQL">
        <option name="KEEP_LINE_BREAKS" value="false" />
        <option name="KEEP_BLANK_LINES_IN_CODE" value="10" />
      </codeStyleSettings>
    </code_scheme>""".trimIndent()
    settings.readExternal(loadElement(loaded))

    val serialized = Element("code_scheme").setAttribute("name", "testSchemeName")
    settings.writeExternal(serialized)
    assertThat(JDOMUtil.writeElement(serialized)).isEqualTo(loaded)
  }

  @Test fun `do not duplicate known extra sections`() {
    val newProvider: CodeStyleSettingsProvider = object : CodeStyleSettingsProvider() {
      override fun createCustomSettings(settings: CodeStyleSettings?): CustomCodeStyleSettings {
        return object : CustomCodeStyleSettings("NewComponent", settings) {

          override fun getKnownTagNames(): List<String> {
            return ContainerUtil.concat(super.getKnownTagNames(), listOf("NewComponent-extra"))
          }

          override fun readExternal(parentElement: Element?) {
            super.readExternal(parentElement)
          }

          override fun writeExternal(parentElement: Element?, parentSettings: CustomCodeStyleSettings) {
            super.writeExternal(parentElement, parentSettings)
            writeMain(parentElement)
            writeExtra(parentElement)
          }

          private fun writeMain(parentElement: Element?) {
            var extra = parentElement!!.getChild(tagName)
            if (extra == null) {
              extra = Element(tagName)
              parentElement.addContent(extra)
            }
            
            val option = Element("option")
            option.setAttribute("name", "MAIN")
            option.setAttribute("value", "3")
            extra.addContent(option)
          }
          private fun writeExtra(parentElement: Element?) {
            val extra = Element("NewComponent-extra")
            val option = Element("option")
            option.setAttribute("name", "EXTRA")
            option.setAttribute("value", "3")
            extra.addContent(option)
            parentElement!!.addContent(extra)
          }
        }
      }

      override fun createSettingsPage(settings: CodeStyleSettings?, originalSettings: CodeStyleSettings?): Configurable {
        throw UnsupportedOperationException("not implemented")
      }
    }

    val disposable = Disposable() {}
    PlatformTestUtil.registerExtension(com.intellij.psi.codeStyle.CodeStyleSettingsProvider.EXTENSION_POINT_NAME,
                                       newProvider, disposable)

    try {
      val settings = CodeStyleSettings()
      val text : (param: String) -> String = { param -> 
        """
      <code_scheme name="testSchemeName" version="${CodeStyleSettings.CURR_VERSION}">
        <NewComponent>
          <option name="MAIN" value="${param}" />
        </NewComponent>
        <NewComponent-extra>
          <option name="EXTRA" value="${param}" />
        </NewComponent-extra>
        <codeStyleSettings language="CoffeeScript">
          <option name="KEEP_SIMPLE_METHODS_IN_ONE_LINE" value="true" />
        </codeStyleSettings>
        <codeStyleSettings language="Gherkin">
          <indentOptions>
            <option name="USE_TAB_CHARACTER" value="true" />
          </indentOptions>
        </codeStyleSettings>
        <codeStyleSettings language="SQL">
          <option name="KEEP_LINE_BREAKS" value="false" />
          <option name="KEEP_BLANK_LINES_IN_CODE" value="10" />
        </codeStyleSettings>
      </code_scheme>""".trimIndent()
      }
      settings.readExternal(loadElement(text("2")))

      val serialized = Element("code_scheme").setAttribute("name", "testSchemeName")
      settings.writeExternal(serialized)
      assertThat(JDOMUtil.writeElement(serialized)).isEqualTo(text("3"))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test fun `reset deprecations`() {
    val settings = CodeStyleSettings()
    val initial = """
    <code_scheme name="testSchemeName">
      <option name="RIGHT_MARGIN" value="64" />
      <option name="USE_FQ_CLASS_NAMES_IN_JAVADOC" value="false" />
    </code_scheme>""".trimIndent()
    val expected = """
    <code_scheme name="testSchemeName" version="${CodeStyleSettings.CURR_VERSION}">
      <option name="RIGHT_MARGIN" value="64" />
    </code_scheme>""".trimIndent();

    settings.readExternal(loadElement(initial))
    settings.resetDeprecatedFields()

    val serialized = Element("code_scheme").setAttribute("name", "testSchemeName")
    settings.writeExternal(serialized)
    assertThat(JDOMUtil.writeElement(serialized)).isEqualTo(expected)
  }
}