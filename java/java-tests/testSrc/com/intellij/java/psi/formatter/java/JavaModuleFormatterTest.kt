/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  }
}