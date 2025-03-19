package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.JavaApiUsageInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

/**
 * This is a base test case for test cases that highlight all the use of API
 * that were introduced in later language levels comparing to the current language level
 *
 * In order to add a new test case:
 * <ol>
 * <li>Go to "community/jvm/jvm-analysis-java-tests/testData/codeInspection/apiUsage"</li>
 * <li>Add a new file(s) to "./src" that contains new API. It's better to define the new API as native methods.</li>
 * <li>Set <code>JAVA_HOME</code> to jdk 1.8. In this case it's possible to redefine JDK's own classes like <code>String</code> or <code>Class</code></li>
 * <li>Invoke "./compile.sh". The new class(es) will appear in "./classes"</li>
 * </ol>
 */
@Ignore
@RunWith(BlockJUnit4ClassRunner::class) // disabled because there are currently no test for a JDK higher than the highest mock JDK
class JavaJavaApiUsageInspectionWithCustomMockJdkTest : JavaApiUsageInspectionTestBase() {
  override fun getBasePath(): String = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH

  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val dataDir = "$testDataPath/codeInspection/apiUsage"
      PsiTestUtil.newLibrary("JDKMock").classesRoot("$dataDir/classes").addTo(model)
    }
  }
}