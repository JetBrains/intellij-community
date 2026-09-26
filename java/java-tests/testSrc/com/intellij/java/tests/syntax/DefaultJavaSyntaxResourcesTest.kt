// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.tests.syntax

import com.intellij.java.syntax.DefaultJavaSyntaxResourcesTestAccessor
import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.platform.syntax.scripts.assertPropertiesMatch
import org.junit.jupiter.api.Test

class DefaultJavaSyntaxResourcesTest {
  @Test
  fun testJavaSyntaxResourcesMatch() {
    assertPropertiesMatch(
      propertiesFileName = JavaSyntaxBundle.BUNDLE,
      defaultResourcesFileName = DefaultJavaSyntaxResourcesTestAccessor.defaultJavaSyntaxResourcesName,
      classLoader = JavaSyntaxBundle.javaClass.classLoader,
      actualMapping = DefaultJavaSyntaxResourcesTestAccessor.mappings,
    )
  }
}