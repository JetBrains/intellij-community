package com.intellij.jvm.analysis.internal.testFramework.sourceToSink

import com.intellij.codeInspection.sourceToSink.SourceToSinkFlowInspection
import com.intellij.testFramework.LightProjectDescriptor

abstract class SourceToSinkFlowInspectionTestBase : TaintedTestBase() {
  override val inspection: SourceToSinkFlowInspection = SourceToSinkFlowInspection().also {
      it.warnIfComplex = true
      it.taintedAnnotations.add("javax.annotation.Tainted")
      it.untaintedAnnotations.add("javax.annotation.Untainted")
    }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_19
  }
}
