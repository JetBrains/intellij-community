// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
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
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.assertions.Assertions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class UnknownSdkTrackerTestKt {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val project get() = projectFixture.get().project

  private val module get() = projectFixture.get().module

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  @Test
  fun `test sdk lookup`(): Unit = timeoutRunBlocking {
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

    withContext(Dispatchers.EDT) {
      repeat(2) {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        UIUtil.dispatchAllInvocationEvents()
      }
    }

    val notifications = UnknownSdkEditorNotification.getInstance(project).getNotifications().joinToString { it.sdkTypeAndNameText }
    Assertions.assertThat(notifications).contains("new-sdk")
  }
}
