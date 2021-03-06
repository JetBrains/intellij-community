// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert

class JdkUpdateStoreTest : LightPlatformTestCase() {

  private fun newSdk(sdkName: String, version: String): Sdk {
    val oldSdk = ProjectJdkTable.getInstance().createSdk(sdkName, JavaSdk.getInstance())
    oldSdk.sdkModificator.apply {
      homePath = createTempDir("mock-old-home").toString()
      versionString = version
    }.commitChanges()

    if (oldSdk is Disposable) {
      Disposer.register(testRootDisposable, oldSdk)
    }

    return oldSdk
  }

  fun testState() {
    val sdkA = newSdk("a", "1.2.3")
    val sdkB = newSdk("b", "3.2.1")
    Assert.assertTrue(service.isAllowed(sdkA, mockZipNew))
    Assert.assertTrue(service.isAllowed(sdkB, mockZipNew))

    service.blockVersion(sdkA, mockZipNew)

    Assert.assertFalse(service.isAllowed(sdkA, mockZipNew))
    Assert.assertTrue(service.isAllowed(sdkA, mockZipNew.copy(jdkVersion = "1234.53.5")))
    Assert.assertTrue(service.isAllowed(sdkB, mockZipNew))

    reloadState()

    Assert.assertFalse(service.isAllowed(sdkA, mockZipNew))
    Assert.assertTrue(service.isAllowed(sdkA, mockZipNew.copy(jdkVersion = "1234.53.5")))
    Assert.assertTrue(service.isAllowed(sdkB, mockZipNew))
  }

  private val service get() = service<JdkUpdaterState>()
  private val newState get() = JdkUpdaterStateData()

  private fun resetState() {
    service.loadState(newState)
  }

  override fun setUp() {
    super.setUp()
    resetState()
  }

  private fun reloadState() {
    //just to check if the state can be saved
    val store = ApplicationManager.getApplication().stateStore
    val p = service
    store.saveComponent(p)
    //wipe the state
    p.loadState(newState)
    //load it (hopefully)
    store.reloadState(service::class.java)
  }
}
