package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.jvmAnnotationsProjectFixture
import com.intellij.jvm.analysis.internal.testFramework.mustAlreadyBeRemovedApiInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaMustAlreadyBeRemovedApiInspectionTest {
  companion object {
    private val projectFixture = jvmAnnotationsProjectFixture()
  }

  private val myFixture by mustAlreadyBeRemovedApiInspectionFixture(projectFixture)

  @Test
  fun `test APIs must have been removed`() {
    myFixture.inspection.currentVersion = "3.0"
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.jetbrains.annotations.ApiStatus;

      @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
      @Deprecated
      class <error descr="API must have been removed in version 2.0 but the current version is 3.0">Warnings</error> {

        @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
        @Deprecated
        public String <error descr="API must have been removed in version 2.0 but the current version is 3.0">field</error>;

        @ApiStatus.ScheduledForRemoval(inVersion = "2.0")
        @Deprecated
        public void <error descr="API must have been removed in version 2.0 but the current version is 3.0">method</error>() {
        }
      }

      //No warnings should be produced.

      @Deprecated
      @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
      class NoWarnings {

        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
        public String field;

        @Deprecated
        @ApiStatus.ScheduledForRemoval(inVersion = "5.0")
        public void method() {
        }
      }
    """.trimIndent())
  }
}