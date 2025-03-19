// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase

class DeleteUnreachableTest : LightQuickFixParameterizedTestCase() {

  override fun getBasePath(): String = "/codeInsight/daemonCodeAnalyzer/quickFix/deleteUnreachable"

}