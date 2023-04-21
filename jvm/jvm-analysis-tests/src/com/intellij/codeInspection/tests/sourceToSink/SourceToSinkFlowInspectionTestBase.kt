package com.intellij.codeInspection.tests.sourceToSink

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.testFramework.LightProjectDescriptor

abstract class SourceToSinkFlowInspectionTestBase : TaintedTestBase() {
  override val inspection: SourceToSinkFlowInspection =  SourceToSinkFlowInspection()

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_8
  }
}