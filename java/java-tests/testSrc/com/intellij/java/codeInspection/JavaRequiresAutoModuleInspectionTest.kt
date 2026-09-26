// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.JavaRequiresAutoModuleInspection
import com.intellij.java.testFramework.fixtures.LightJava9ModulesCodeInsightFixtureTestCase

class JavaRequiresAutoModuleInspectionTest : LightJava9ModulesCodeInsightFixtureTestCase() {
  private lateinit var inspection: JavaRequiresAutoModuleInspection

  override fun setUp() {
    super.setUp()
    inspection = JavaRequiresAutoModuleInspection()
    myFixture.enableInspections(inspection)
  }

  fun testTransitive() {
    highlighting("""module M { requires transitive <warning descr="'requires transitive' directive for an automatic module">lib.claimed</warning>; }""")
  }

  fun testTransitiveSuppressedJavac() {
    highlighting("""@SuppressWarnings("requires-transitive-automatic") module M { requires transitive lib.claimed; }""")
  }

  fun testTransitiveOldSuppression() {
    highlighting("""@SuppressWarnings("JavaRequiresAutoModule") module M { requires transitive lib.claimed; }""")
  }

  fun testAny() {
    inspection.TRANSITIVE_ONLY = false
    highlighting("""module M { requires <warning descr="'requires' directive for an automatic module">lib.claimed</warning>; }""")
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}