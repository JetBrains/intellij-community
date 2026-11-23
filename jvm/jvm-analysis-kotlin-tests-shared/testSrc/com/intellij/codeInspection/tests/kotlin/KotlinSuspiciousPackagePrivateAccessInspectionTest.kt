package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.PathUtil
import com.siyeh.ig.LightJavaInspectionFixture
import com.siyeh.ig.dependency.suspiciousPackagePrivateAccessFixture
import com.siyeh.ig.dependency.suspiciousPackagePrivateAccessProjectFixture
import org.junit.jupiter.api.Test
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/suspiciousPackagePrivateAccess")
abstract class KotlinSuspiciousPackagePrivateAccessInspectionTest {
  companion object {
    private val projectFixture = testFixture("kotlinSuspiciousPackagePrivateAccessProjectFixture") {
      val modulesProject = suspiciousPackagePrivateAccessProjectFixture().init()
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      val modules = ModuleManager.getInstance(modulesProject.project).modules
      for (module in modules) {
        PsiTestUtil.addLibrary(module, "kotlin-stdlib", jar.parent, jar.name)
      }
      initialized(modulesProject) {}
    }
  }

  private val inspectionFixture by suspiciousPackagePrivateAccessFixture(
    projectFixture, "kt",
    LightJavaInspectionFixture.inspectionTestDataPath(
      "${KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/suspiciousPackagePrivateAccess"))

  @Test
  fun testAccessingPackagePrivateMembers() {
    inspectionFixture.doTestWithDependency()
  }

  @Test
  fun testAccessingProtectedMembers() {
    inspectionFixture.doTestWithDependency()
  }

  @Test
  fun testAccessingProtectedMembersFromKotlin() {
    inspectionFixture.doTestWithDependency()
  }

  @Test
  fun testOverridePackagePrivateMethod() {
    inspectionFixture.doTestWithDependency()
  }
}
