// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.util.environment.Environment
import com.intellij.openapi.externalSystem.util.environment.TestEnvironment
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.SdkTestCase
import com.intellij.openapi.roots.ui.configuration.UnknownSdkResolver
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.replaceService

abstract class ExternalSystemJdkUtilTestCase : SdkTestCase() {

  val environment get() = Environment.getInstance() as TestEnvironment
  val jdkProvider get() = ExternalSystemJdkProvider.getInstance() as TestJdkProvider

  override fun setUp() {
    super.setUp()

    val application = ApplicationManager.getApplication()
    application.replaceService(Environment::class.java, TestEnvironment(), testRootDisposable)
    application.replaceService(ExternalSystemJdkProvider::class.java, TestJdkProvider(), testRootDisposable)

    ExtensionTestUtil.maskExtensions(UnknownSdkResolver.EP_NAME, listOf(TestUnknownSdkResolver), testRootDisposable)

    environment.variables(ExternalSystemJdkUtil.JAVA_HOME to null)

    TestUnknownSdkResolver.unknownSdkFixMode = TestUnknownSdkResolver.TestUnknownSdkFixMode.TEST_LOCAL_FIX
  }

  class TestJdkProvider : ExternalSystemJdkProvider, Disposable {
    private val lazyInternalJdk by lazy { TestSdkGenerator.createNextSdk() }

    override fun getJavaSdkType() = TestSdkType

    override fun getInternalJdk(): Sdk = lazyInternalJdk

    override fun createJdk(jdkName: String?, homePath: String): Sdk {
      val sdk = TestSdkGenerator.findTestSdk(homePath)!!
      Disposer.register(this, Disposable { removeSdk(sdk) })
      return sdk
    }

    override fun dispose() {}
  }
}