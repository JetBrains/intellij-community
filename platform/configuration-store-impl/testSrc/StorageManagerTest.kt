// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.testFramework.ProjectRule
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Paths
import kotlin.properties.Delegates

internal class StorageManagerTest {
  companion object {
    const val MACRO = "\$MACRO1$"

    @JvmField
    @ClassRule
    val projectRule = ProjectRule()
  }

  private var storageManager: StateStorageManagerImpl by Delegates.notNull()

  @Before
  fun setUp() {
    storageManager = StateStorageManagerImpl("foo")
    storageManager.setMacros(listOf(Macro(MACRO, Paths.get("/temp/m1"))))
  }

  @Test
  fun createFileStateStorageMacroSubstituted() {
    assertThat(storageManager.getOrCreateStorage("$MACRO/test.xml")).isNotNull()
  }

  @Test
  fun `collapse macro`() {
    assertThat(storageManager.collapseMacros("/temp/m1/foo")).isEqualTo("$MACRO/foo")
    assertThat(storageManager.collapseMacros("\\temp\\m1\\foo")).isEqualTo("/temp/m1/foo")
  }

  @Test
  fun `create storage assertion thrown when unknown macro`() {
    try {
      storageManager.getOrCreateStorage("\$UNKNOWN_MACRO$/test.xml")
      TestCase.fail("Exception expected")
    }
    catch (e: IllegalStateException) {
      assertThat(e.message).isEqualTo("Cannot resolve \$UNKNOWN_MACRO\$/test.xml in [Macro(key=\$MACRO1\$, value=/temp/m1)]")
    }
  }

  @Test
  fun `create file storage macro substituted when expansion has$`() {
    storageManager.setMacros(listOf(Macro("\$DOLLAR_MACRO$", Paths.get("/temp/d$"))))
    assertThat(storageManager.getOrCreateStorage("\$DOLLAR_MACRO$/test.xml")).isNotNull()
  }
}