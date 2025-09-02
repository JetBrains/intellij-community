package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.ObsoleteApiUsageInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor

abstract class ObsoleteApiUsageInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: ObsoleteApiUsageInspection = ObsoleteApiUsageInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      import org.jetbrains.annotations.ApiStatus;
      
      public class A {
        @ApiStatus.Obsolete 
        void f() {}
      }""".trimIndent()
    )
    enableWarnings()
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(LanguageLevel.HIGHEST, true) {}
}
