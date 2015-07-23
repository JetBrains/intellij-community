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
package com.intellij.openapi.components.impl

import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.StateStorageOperation
import com.intellij.openapi.components.impl.stores.StorageData
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Assert.assertThat

/**
 * @author mike
 */
public class StateStorageManagerImplTest : LightPlatformTestCase() {
  private var myStateStorageManager: StateStorageManagerImpl? = null

  override fun setUp() {
    super.setUp()
    val substitutor = PathMacroManager.getInstance(LightPlatformTestCase.getProject()).createTrackingSubstitutor()
    myStateStorageManager = object : StateStorageManagerImpl(substitutor, "foo", myTestRootDisposable, ApplicationManager.getApplication().getPicoContainer()) {
      override fun createStorageData(fileSpec: String, filePath: String): StorageData {
        throw UnsupportedOperationException("Method createStorageData not implemented in " + javaClass)
      }

      override fun getOldStorageSpec(component: Any, componentName: String, operation: StateStorageOperation): String? {
        throw UnsupportedOperationException("Method getOldStorageSpec not implemented in " + javaClass)
      }
    }
    myStateStorageManager!!.addMacro("\$MACRO1$", "/temp/m1")
  }

  override fun tearDown() {
    try {
      val storageManager = myStateStorageManager
      if (storageManager != null) {
        Disposer.dispose(storageManager)
      }
    }
    finally {
      super.tearDown()
    }
  }

  public fun testCreateFileStateStorageMacroSubstituted() {
    val data = myStateStorageManager!!.getStateStorage("\$MACRO1$/test.xml", RoamingType.PER_USER)
    assertThat<StateStorage>(data, `is`(notNullValue()))
  }

  public fun testCreateStateStorageAssertionThrownWhenUnknownMacro() {
    try {
      myStateStorageManager!!.getStateStorage("\$UNKNOWN_MACRO$/test.xml", RoamingType.PER_USER)
      TestCase.fail("Exception expected")
    }
    catch (e: IllegalArgumentException) {
      TestCase.assertEquals("Unknown macro: \$UNKNOWN_MACRO$ in storage file spec: \$UNKNOWN_MACRO$/test.xml", e.getMessage())
    }

  }

  public fun `testCreateFileStateStorageMacroSubstitutedWhenExpansionHas$`() {
    myStateStorageManager!!.addMacro("\$DOLLAR_MACRO$", "/temp/d$")
    val data = myStateStorageManager!!.getStateStorage("\$DOLLAR_MACRO$/test.xml", RoamingType.PER_USER)
    assertThat<StateStorage>(data, `is`(notNullValue()))
  }
}
