package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.MustAlreadyBeRemovedApiInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinMustAlreadyBeRemovedApiInspectionTest : MustAlreadyBeRemovedApiInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test APIs must have been removed`() {
    inspection.currentVersion = "3.0"
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.jetbrains.annotations.ApiStatus

      @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
      @Deprecated("")
      class <error descr="API must have been removed in version 2.0 but the current version is 3.0">Warnings</error> {

        @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
        @Deprecated("")
        var <error descr="API must have been removed in version 2.0 but the current version is 3.0">field</error>: String? = null

        @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
        @Deprecated("")
        fun <error descr="API must have been removed in version 2.0 but the current version is 3.0">method</error>() {
        }
      }

      //No warnings should be produced.

      @Deprecated("")
      @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
      class NoWarnings {

        @Deprecated("")
        @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
        var field: String? = null

        @Deprecated("")
        @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
        fun method() {
        }
      }
    """.trimIndent())
  }
}