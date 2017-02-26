package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.loadElement
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BinaryXmlOutputterTest {
  @Test fun noAttributes() {
    test("""<foo />""")
  }

  @Test fun attributes() {
    test("""<foo bar="1" />""")
  }

  private fun test(xml: String) {
    val byteOut = BufferExposingByteArrayOutputStream()
    byteOut.use {
      serializeElementToBinary(loadElement(xml), it)
    }

    val xmlAfter = JDOMUtil.writeElement(byteOut.toByteArray().inputStream().use { readElement(it) })

    assertThat(xml.trimIndent()).isEqualTo(xmlAfter)
  }
}