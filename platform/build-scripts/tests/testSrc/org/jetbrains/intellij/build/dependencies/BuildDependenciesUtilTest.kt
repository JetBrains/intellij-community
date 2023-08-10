// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BuildDependenciesUtilTest {
  @Test fun normalization() {
    assertEquals("xxx", BuildDependenciesUtil.normalizeEntryName("///////xxx\\"))
    assertEquals("..xxx", BuildDependenciesUtil.normalizeEntryName("///////..xxx"))
  }

  @Test fun validation() {
    assertThrows("Entry names should not be blank", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("/") }
    assertThrows("Invalid entry name: ../xxx", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("/../xxx") }
    assertThrows("Normalized entry name should not contain '//': a//xxx", Exception::class.java) { BuildDependenciesUtil.normalizeEntryName("a/\\xxx") }
  }
}
