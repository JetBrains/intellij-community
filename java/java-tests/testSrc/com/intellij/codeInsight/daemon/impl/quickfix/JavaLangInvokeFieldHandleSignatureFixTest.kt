// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase
import com.intellij.codeInspection.reflectiveAccess.JavaLangInvokeHandleSignatureInspection
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JavaLangInvokeFieldHandleSignatureFixTest : LightQuickFixParameterizedTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_9

  override fun setUp() {
    super.setUp()
    enableInspectionTool(JavaLangInvokeHandleSignatureInspection())
  }

  override fun getBasePath(): String = "/codeInsight/daemonCodeAnalyzer/quickFix/fieldHandle"
}
