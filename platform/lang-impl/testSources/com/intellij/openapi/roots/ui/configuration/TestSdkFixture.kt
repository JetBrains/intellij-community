// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ui.configuration.SdkTestCase.TestSdkGenerator
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.testFixture

fun testSdkFixture(): TestFixture<TestSdkGenerator> = testFixture {
  extensionPointFixture(SdkType.EP_NAME, SdkTestCase.TestSdkType).init()
  extensionPointFixture(SdkDownload.EP_NAME, SdkTestCase.TestSdkDownloader).init()
  TestSdkGenerator.reset()
  initialized(TestSdkGenerator) {
    TestSdkGenerator.reset()
  }
}
