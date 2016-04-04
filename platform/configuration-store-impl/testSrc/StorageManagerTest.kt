/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.ProjectRule
import com.intellij.util.SmartList
import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import kotlin.properties.Delegates

internal class StorageManagerTest {
  companion object {
    val MACRO = "\$MACRO1$"

    @JvmField
    @ClassRule val projectRule = ProjectRule()
  }

  private var storageManager: StateStorageManagerImpl by Delegates.notNull()

  @Before fun setUp() {
    storageManager = StateStorageManagerImpl("foo")
    storageManager.addMacro(MACRO, "/temp/m1")
  }

  @Test fun createFileStateStorageMacroSubstituted() {
    assertThat(storageManager.getOrCreateStorage("$MACRO/test.xml")).isNotNull()
  }

  @Test fun `collapse macro`() {
    assertThat(storageManager.collapseMacros("/temp/m1/foo")).isEqualTo("$MACRO/foo")
    assertThat(storageManager.collapseMacros("\\temp\\m1\\foo")).isEqualTo("/temp/m1/foo")
  }

  @Test fun `add system-dependent macro`() {
    val key = "\$INVALID$"
    val expansion = "\\temp"
    assertThatThrownBy({storageManager.addMacro(key, expansion) }).hasMessage("Macro $key set to system-dependent expansion $expansion")
  }

  @Test fun `create storage assertion thrown when unknown macro`() {
    try {
      storageManager.getOrCreateStorage("\$UNKNOWN_MACRO$/test.xml")
      TestCase.fail("Exception expected")
    }
    catch (e: IllegalArgumentException) {
      assertThat(e.message).isEqualTo("Unknown macro: \$UNKNOWN_MACRO$ in storage file spec: \$UNKNOWN_MACRO$/test.xml")
    }
  }

  @Test fun `create file storage macro substituted when expansion has$`() {
    storageManager.addMacro("\$DOLLAR_MACRO$", "/temp/d$")
    assertThat(storageManager.getOrCreateStorage("\$DOLLAR_MACRO$/test.xml")).isNotNull()
  }
}

fun ComponentManager.saveStore() {
  stateStore.save(SmartList())
}