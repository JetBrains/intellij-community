// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.codeInspection.ex.InspectionElementsMerger

class TestInProductSourceInspectionMerger : InspectionElementsMerger() {
  override fun getMergedToolName(): String = "TestInProductSource"

  override fun getSourceToolNames(): Array<String> = arrayOf(
    "TestCaseInProductCode",
    "TestMethodInProductCode"
  )

  override fun getSuppressIds(): Array<String> = arrayOf(
    "JUnitTestCaseInProductSource",
    "JUnitTestMethodInProductSource"
  )
}