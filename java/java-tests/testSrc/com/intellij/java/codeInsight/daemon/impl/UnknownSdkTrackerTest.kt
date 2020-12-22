// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.UnknownSdkEditorNotification
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver.UnknownSdkLookup
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.setSystemPropertyForTest
import com.intellij.util.ui.UIUtil

class UnknownSdkTrackerTestKt : LightPlatformTestCase() {
  fun `test sdk lookup`() {
    val auto = object: UnknownSdkResolver {
      override fun supportsResolution(sdkTypeId: SdkTypeId) = true
      override fun createResolver(project: Project?, indicator: ProgressIndicator) = object: UnknownSdkLookup {
        override fun proposeLocalFix(sdk: UnknownSdk, indicator: ProgressIndicator) = null
        override fun proposeDownload(sdk: UnknownSdk, indicator: ProgressIndicator) = null
      }
    }
    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(auto), testRootDisposable)

    runWriteActionAndWait {
      ModuleRootManager.getInstance(module).modifiableModel.also {
        it.setInvalidSdk("new-sdk", JavaSdk.getInstance().name)
      }.commit()
    }

    UnknownSdkTracker.getInstance(project).updateUnknownSdks()

    repeat(2) {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      UIUtil.dispatchAllInvocationEvents()
    }

    val notifications = UnknownSdkEditorNotification.getInstance(project).notifications.joinToString { it.sdkTypeAndNameText }
    Assertions.assertThat(notifications).contains("new-sdk")
  }
}
