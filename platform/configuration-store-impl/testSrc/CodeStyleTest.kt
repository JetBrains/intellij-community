package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.ProjectRule
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
    <code_scheme name="testSchemeName">
      <UnknownDoNotRemoveMe>
        <option name="ALIGN_OBJECT_PROPERTIES" value="2" />
      </UnknownDoNotRemoveMe>
      <codeStyleSettings language="CoffeeScript">
        <option name="LINE_COMMENT_AT_FIRST_COLUMN" value="false" />
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
}