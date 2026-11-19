package com.intellij.jvm.analysis

import com.intellij.jvm.analysis.internal.testFramework.JvmAnalysisTestUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.addRoot

object KotlinJvmAnalysisTestUtil {
  const val TEST_DATA_PROJECT_RELATIVE_BASE_PATH = "${JvmAnalysisTestUtil.MODULE_PATH}/jvm-analysis-kotlin-tests-shared/testData"

  /**
   * The absolute path of the test data of this module.
   */
  val testDataPath: String
    get() = FileUtil.toSystemIndependentName(PathManager.getCommunityHomePath()) + TEST_DATA_PROJECT_RELATIVE_BASE_PATH
}

/**
 * Adds the Kotlin standard library and `kotlin-test` to the module, as [ConfigLibraryUtil.configureKotlinRuntime] does.
 * Call it in the descriptor of a project fixture.
 */
fun ModifiableRootModel.addKotlinRuntime() {
  ConfigLibraryUtil.addLibrary(this, "KOTLIN_STDLIB_LIB_NAME") {
    addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
  }
  ConfigLibraryUtil.addLibrary(this, "KOTLIN_TEST_LIB_NAME") {
    addRoot(TestKotlinArtifacts.kotlinTest, OrderRootType.CLASSES)
  }
}
