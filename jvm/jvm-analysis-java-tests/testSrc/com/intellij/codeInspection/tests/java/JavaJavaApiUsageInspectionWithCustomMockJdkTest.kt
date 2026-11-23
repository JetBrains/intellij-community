package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.javaApiUsageInspectionFixture
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Disabled

/**
 * This is a base test case for test cases that highlight all the use of APIs
 * that were introduced in later language levels compared to the current language level.
 *
 * To add a new test case:
 * - Go to `community/jvm/jvm-analysis-java-tests/testData/codeInspection/apiUsage`
 * - Add a new file(s) to `./src` that contains the new API. It's better to define the new API as native methods
 * - Set `JAVA_HOME` to JDK 1.8. In this case it's possible to redefine JDK's own classes like `String` or `Class`
 * - Invoke `./compile.sh`. The new class(es) will appear in `./classes`
 */
@Disabled("There are currently no tests for a JDK higher than the highest mock JDK")
@TestApplication
@Suppress("JUnitTestCaseWithNoTests")
class JavaJavaApiUsageInspectionWithCustomMockJdkTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(object : ProjectDescriptor(LanguageLevel.HIGHEST) {
      override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        val dataDir = "${JavaJvmAnalysisTestUtil.testDataPath}/codeInspection/apiUsage"
        PsiTestUtil.newLibrary("JDKMock").classesRoot("$dataDir/classes").addTo(model)
      }
    })
  }

  @Suppress("unused")
  private val myFixture by javaApiUsageInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath)
}
