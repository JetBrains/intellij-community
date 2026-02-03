package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.MustAlreadyBeRemovedApiInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class MustAlreadyBeRemovedApiInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: MustAlreadyBeRemovedApiInspection = MustAlreadyBeRemovedApiInspection()

  @Suppress("DuplicatedCode")
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST, true) {}
}