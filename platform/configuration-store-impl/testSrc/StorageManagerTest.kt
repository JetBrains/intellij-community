// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Path
import kotlin.properties.Delegates

class StorageManagerTest {
  companion object {
    private const val MACRO = "\$MACRO1$"

    @JvmField @ClassRule val projectRule = ProjectRule()
  }

  private var storageManager: StateStorageManagerImpl by Delegates.notNull()

  @Before
  fun setUp() {
    storageManager = StateStorageManagerImpl("foo", componentManager = null)
    storageManager.setMacros(listOf(Macro(MACRO, Path.of("/temp/m1"))))
  }

  @Test
  fun createFileStateStorageMacroSubstituted() {
    assertThat(storageManager.getOrCreateStorage("$MACRO/test.xml", RoamingType.DEFAULT)).isNotNull()
  }

  @Test
  fun `collapse macro`() {
    assertThat(storageManager.collapseMacro("/temp/m1/foo")).isEqualTo("$MACRO/foo")
    assertThat(storageManager.collapseMacro("\\temp\\m1\\foo")).isEqualTo("/temp/m1/foo")
  }

  @Test
  fun `create storage assertion thrown when unknown macro`() {
    assertThatThrownBy { storageManager.getOrCreateStorage("\$UNKNOWN_MACRO$/test.xml", RoamingType.DEFAULT) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessage("Cannot resolve \$UNKNOWN_MACRO\$/test.xml in [Macro(key=\$MACRO1\$, value=${FileUtil.toSystemDependentName("/temp/m1")})]")
  }

  @Test
  fun `create file storage macro substituted when expansion has$`() {
    storageManager.setMacros(listOf(Macro("\$DOLLAR_MACRO$", Path.of("/temp/d$"))))
    assertThat(storageManager.getOrCreateStorage("\$DOLLAR_MACRO$/test.xml", RoamingType.DEFAULT)).isNotNull()
  }
}
