// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.JavaRequiresAutoModuleInspection
import com.intellij.java.testFramework.fixtures.lightJava9ModulesFixture
import com.intellij.java.testFramework.fixtures.lightJava9ModulesProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaRequiresAutoModuleInspectionTest {
  companion object {
    private val projectFixture = lightJava9ModulesProjectFixture()
  }

  private val java9 by lightJava9ModulesFixture(projectFixture)
  private val myFixture get() = java9.fixture

  private lateinit var inspection: JavaRequiresAutoModuleInspection

  @BeforeEach
  fun setUp() {
    inspection = JavaRequiresAutoModuleInspection()
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testTransitive() {
    highlighting("""module M { requires transitive <warning descr="'requires transitive' directive for an automatic module">lib.claimed</warning>; }""")
  }

  @Test
  fun testTransitiveSuppressedJavac() {
    highlighting("""@SuppressWarnings("requires-transitive-automatic") module M { requires transitive lib.claimed; }""")
  }

  @Test
  fun testTransitiveOldSuppression() {
    highlighting("""@SuppressWarnings("JavaRequiresAutoModule") module M { requires transitive lib.claimed; }""")
  }

  @Test
  fun testAny() {
    inspection.TRANSITIVE_ONLY = false
    highlighting("""module M { requires <warning descr="'requires' directive for an automatic module">lib.claimed</warning>; }""")
  }

  private fun highlighting(text: String) {
    myFixture.configureByText("module-info.java", text)
    myFixture.checkHighlighting()
  }
}