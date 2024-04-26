package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection
import com.intellij.psi.util.AccessModifier
import com.intellij.testFramework.JavaInspectionTestCase

abstract class SameParameterValueInspectionTestBase(private val isLocal: Boolean) : JavaInspectionTestCase() {
  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm"

  private fun getTestDir(): String = "sameParameterValue/" + getTestName(true)

  private val inspection: InspectionProfileEntry = SameParameterValueInspection().let {
    it.highestModifier = AccessModifier.PUBLIC
    if (isLocal) it.sharedLocalInspectionTool!! else it
  }

  protected fun doHighlightTest(checkRange: Boolean = false, runDeadCodeFirst: Boolean = false) {
    val inspectionToolWrapper = if (isLocal) {
      LocalInspectionToolWrapper(inspection as LocalInspectionTool)
    } else GlobalInspectionToolWrapper(inspection as GlobalInspectionTool)
    doTest(getTestDir(), inspectionToolWrapper, checkRange, runDeadCodeFirst)
  }
}