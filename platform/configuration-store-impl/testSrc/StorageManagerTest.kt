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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.RoamingType
import com.intellij.testFramework.FixtureRule
import junit.framework.TestCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.properties.Delegates

class StorageManagerTest {
  private val fixtureManager = FixtureRule()
  public Rule fun getFixtureManager(): FixtureRule = fixtureManager

  private var storageManager: StateStorageManagerImpl by Delegates.notNull()

  companion object {
    val MACRO = "\$MACRO1$"
  }

  public Before fun setUp() {
    val application = ApplicationManager.getApplication()
    storageManager = StateStorageManagerImpl(PathMacroManager.getInstance(application).createTrackingSubstitutor(), "foo", application.getPicoContainer())
    storageManager.addMacro(MACRO, "/temp/m1")
  }

  public Test fun testCreateFileStateStorageMacroSubstituted() {
    assertThat(storageManager.getStateStorage("$MACRO/test.xml", RoamingType.PER_USER), notNullValue())
  }

  public Test fun `collapse macro`() {
    assertThat(storageManager.collapseMacros("/temp/m1/foo"), equalTo("$MACRO/foo"))
  }

  public Test fun `create storage assertion thrown when unknown macro`() {
    try {
      storageManager.getStateStorage("\$UNKNOWN_MACRO$/test.xml", RoamingType.PER_USER)
      TestCase.fail("Exception expected")
    }
    catch (e: IllegalArgumentException) {
      assertThat(e.getMessage(), equalTo("Unknown macro: \$UNKNOWN_MACRO$ in storage file spec: \$UNKNOWN_MACRO$/test.xml"))
    }
  }

  public Test fun `create file storage macro substituted when expansion has$`() {
    storageManager.addMacro("\$DOLLAR_MACRO$", "/temp/d$")
    assertThat(storageManager.getStateStorage("\$DOLLAR_MACRO$/test.xml", RoamingType.PER_USER), notNullValue())
  }
}
