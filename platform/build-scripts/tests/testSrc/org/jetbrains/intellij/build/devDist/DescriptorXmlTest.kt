// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.devDist

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

/** [hasRootXmlAttribute] reads the root start tag alone. */
class DescriptorXmlTest {
  @Test
  fun `the attribute of the root element is found after a prolog`() {
    val xml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!-- a comment before the root -->
      <idea-plugin package="com.example" visibility="public"><content/></idea-plugin>
    """.trimIndent()

    assertThat(hasRootXmlAttribute(xml, "package")).isTrue()
    assertThat(hasRootXmlAttribute(xml, "visibility")).isTrue()
  }

  @Test
  fun `an attribute of a child element is not an attribute of the root`() {
    val xml = """<idea-plugin><content><module name="a" package="com.example"/></content></idea-plugin>"""

    assertThat(hasRootXmlAttribute(xml, "package")).isFalse()
    assertThat(hasRootXmlAttribute(xml, "name")).isFalse()
  }

  @Test
  fun `the file overload reads the same answer`(@TempDir root: Path) {
    val withPackage = root.resolve("with.xml")
    withPackage.writeText("""<idea-plugin package="com.example"/>""")
    val without = root.resolve("without.xml")
    without.writeText("<idea-plugin/>")

    assertThat(hasRootXmlAttribute(withPackage, "package")).isTrue()
    assertThat(hasRootXmlAttribute(without, "package")).isFalse()
  }
}
