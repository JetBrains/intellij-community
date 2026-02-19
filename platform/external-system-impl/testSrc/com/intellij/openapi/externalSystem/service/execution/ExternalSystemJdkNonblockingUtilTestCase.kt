// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider
import com.intellij.openapi.roots.ui.configuration.SdkLookupProvider.SdkInfo
import com.intellij.openapi.roots.ui.configuration.SdkLookupProviderImpl
import com.intellij.testFramework.common.timeoutRunBlocking

abstract class ExternalSystemJdkNonblockingUtilTestCase : ExternalSystemJdkUtilTestCase() {

  lateinit var sdkLookupProvider: SdkLookupProvider

  override fun setUp() {
    super.setUp()

    sdkLookupProvider = SdkLookupProviderImpl()
  }

  open fun nonblockingResolveJdkInfo(jdkReference: String?): SdkInfo {
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk
    return timeoutRunBlocking {
      sdkLookupProvider.resolveJdkInfo(project, projectSdk, jdkReference)
    }
  }

  fun assertSdkInfo(versionString: String, homePath: String, actualJdkReference: String?) {
    val actualSdkInfo = nonblockingResolveJdkInfo(actualJdkReference)
    require(actualSdkInfo is SdkInfo.Resolved)
    assertEquals(homePath, actualSdkInfo.homePath)
    assertEquals(versionString, actualSdkInfo.versionString)
  }

  fun assertSdkInfo(expected: Sdk, actualJdkReference: String?) {
    val actualSdkInfo = nonblockingResolveJdkInfo(actualJdkReference)
    require(actualSdkInfo is SdkInfo.Resolved) { actualSdkInfo }
    assertEquals(createSdkInfo(expected), actualSdkInfo)
  }

  fun assertSdkInfo(expected: SdkInfo, actualJdkReference: String?) {
    val actualSdkInfo = nonblockingResolveJdkInfo(actualJdkReference)
    assertEquals(expected, actualSdkInfo)
  }

  fun createResolvingSdkInfo(sdk: Sdk) = SdkInfo.Resolving(sdk.name, sdk.versionString, sdk.homePath)
}