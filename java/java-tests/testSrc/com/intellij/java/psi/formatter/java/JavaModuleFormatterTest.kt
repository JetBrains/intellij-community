// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.formatter.java

import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightPlatformTestCase

class JavaModuleFormatterTest : AbstractJavaFormatterTest() {
  override fun setUp() {
    super.setUp()
    LanguageLevelProjectExtension.getInstance(LightPlatformTestCase.getProject()).languageLevel = LanguageLevel.JDK_1_9
  }

  fun testEmpty() {
    doTextTest("module A.B { }", "module A.B {\n}")
  }

  fun testCommentBody() {
    doTextTest("module m { /* comment */ }", "module m { /* comment */\n}")
  }

  fun testStatements() {
    doTextTest("module m { requires java.base; exports a.b; }", "module m {\n    requires java.base;\n    exports a.b;\n}")
  }

  fun testQualifiedExports() {
    doTextTest("module m { exports a.b to m1,m2,m3; }", "module m {\n    exports a.b to m1, m2, m3;\n}")
  }

  fun testProvidesWith() {
    doTextTest("module m { provides I with C1,C2,C3; }", "module m {\n    provides I with C1, C2, C3;\n}")
  }

  fun testAnnotatedModule() {
    doTextTest("@Deprecated\nmodule m { }", "@Deprecated\nmodule m {\n}")
    doTextTest("@Deprecated(forRemoval = true)  module m { }", "@Deprecated(forRemoval = true)\nmodule m {\n}")
  }
}