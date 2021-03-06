// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Assert

class JdkDownloaderStoreTest : LightPlatformTestCase() {
  fun testState() {
    val item1 = jdkItemForTest("urk-1", JdkPackageType.ZIP, 123, "sha-1")
    val item2 = jdkItemForTest("urk-2", JdkPackageType.ZIP, 432, "sha-2")
    val path1 = createTempDir("path-1").toPath()
    service.registerInstall(item1, path1)

    Assert.assertEquals(listOf(path1), service.findInstallations(item1).toList())
    Assert.assertTrue(service.findInstallations(item2).isEmpty())

    reloadState()

    Assert.assertEquals(listOf(path1), service.findInstallations(item1).toList())
    Assert.assertTrue(service.findInstallations(item2).isEmpty())
  }

  private val service get() = service<JdkInstallerStore>()
  private val newState get() = JdkInstallerState()

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
