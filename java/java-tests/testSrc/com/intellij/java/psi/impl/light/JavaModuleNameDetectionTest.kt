// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.light

import com.intellij.psi.impl.light.LightJavaModule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class JavaModuleNameDetectionTest {
  @Test fun plain(): Unit = doTest("foo", "foo")
  @Test fun versioned(): Unit = doTest("foo-1.2.3-SNAPSHOT", "foo")
  @Test fun trailing(): Unit = doTest("foo2bar3", "foo2bar3")
  @Test fun replacing(): Unit = doTest("foo_bar", "foo.bar")
  @Test fun collapsing(): Unit = doTest("foo...bar", "foo.bar")
  @Test fun trimming(): Unit = doTest("...foo.bar...", "foo.bar")
  @Test fun invalid(): Unit = doTest("lib_invalid_1_2", "lib.invalid.1.2")
  @Test fun withKeywords(): Unit = doTest("lib.for.ide", "lib.for.ide")

  private fun doTest(original: String, expected: String) = assertEquals(expected, LightJavaModule.moduleName(original))
}