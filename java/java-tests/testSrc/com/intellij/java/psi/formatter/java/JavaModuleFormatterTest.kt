// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java

import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaModuleFormatterTest {
  companion object {
    private val projectFixture = javaFormatterProjectFixture()
  }

  private val formatter by javaFormatterFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    IdeaTestUtil.setProjectLanguageLevel(formatter.project, LanguageLevel.JDK_1_9)
  }

  @Test
  fun testEmpty() {
    formatter.doTextTest("module A.B { }", "module A.B {\n}")
  }

  @Test
  fun testCommentBody() {
    formatter.doTextTest("module m { /* comment */ }", "module m { /* comment */\n}")
  }

  @Test
  fun testStatements() {
    formatter.doTextTest("module m { requires java.base; exports a.b; }", "module m {\n    requires java.base;\n    exports a.b;\n}")
  }

  @Test
  fun testQualifiedExports() {
    formatter.doTextTest("module m { exports a.b to m1,m2,m3; }", "module m {\n    exports a.b to m1, m2, m3;\n}")
  }

  @Test
  fun testProvidesWith() {
    formatter.doTextTest("module m { provides I with C1,C2,C3; }", "module m {\n    provides I with C1, C2, C3;\n}")
  }

  @Test
  fun testAnnotatedModule() {
    formatter.doTextTest("@Deprecated\nmodule m { }", "@Deprecated\nmodule m {\n}")
    formatter.doTextTest("@Deprecated(forRemoval = true)  module m { }", "@Deprecated(forRemoval = true)\nmodule m {\n}")
  }

  @Test
  fun testComposedModulesNamesAreCollapsed() {
    formatter.doTextTest("""
      module m   .  b   .
         c { exports a  .  b to m1  .
            m2 ,m2  .  m3, m3
              .  m4; }
    """.trimIndent(),
               """
                 module m.b.
                         c {
                     exports a.b to m1.
                             m2, m2.m3, m3
                             .m4;
                 }
               """.trimIndent())
  }

  @Test
  fun testComposedImportModuleNamesAreCollapsed() {
    formatter.doTextTest("""
      import module m   .  b . c
      import module m2 . b  .
                   c
    """.trimIndent(),
               """
      import module m.b.c
      import module m2.b.
              c
    """.trimIndent()   )
  }
}