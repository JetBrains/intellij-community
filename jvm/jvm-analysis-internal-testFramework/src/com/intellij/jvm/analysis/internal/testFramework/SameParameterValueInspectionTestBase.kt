package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection
import com.intellij.psi.util.AccessModifier
import com.intellij.testFramework.JavaInspectionFixture
import com.intellij.testFramework.JavaInspectionTestCase
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.javaInspectionFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly

/**
 * @deprecated Use [com.intellij.testFramework.javaInspectionProjectFixture] and [sameParameterValueInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use javaInspectionProjectFixture and sameParameterValueInspectionFixture in a JUnit 5 test")
abstract class SameParameterValueInspectionTestBase(private val isLocal: Boolean) : JavaInspectionTestCase() {
  override fun getTestDataPath(): String = JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm"

  private fun getTestDir(): String = "sameParameterValue/" + getTestName(true)

  private val inspection: InspectionProfileEntry = SameParameterValueInspection().let {
    it.highestModifier = AccessModifier.PUBLIC
    if (isLocal) it.sharedLocalInspectionTool!! else it
  }

  protected fun doHighlightTest(checkRange: Boolean = false, runDeadCodeFirst: Boolean = false) {
    val inspectionToolWrapper = if (isLocal) {
      LocalInspectionToolWrapper(inspection as LocalInspectionTool)
    } else GlobalInspectionToolWrapper(inspection as GlobalInspectionTool)
    doTest(getTestDir(), inspectionToolWrapper, checkRange, runDeadCodeFirst)
  }
}

/**
 * The fixture of the same parameter value inspection tests. Create it with [sameParameterValueInspectionFixture].
 *
 * The inspection reports the methods up to the public ones. With `isLocal`, the fixture runs the local form of the inspection.
 */
@TestOnly
class SameParameterValueInspectionFixture internal constructor(
  /**
   * The inspection fixture of the test.
   */
  val inspection: JavaInspectionFixture,
  private val isLocal: Boolean,
) {
  private val tool: InspectionProfileEntry = SameParameterValueInspection().let {
    it.highestModifier = AccessModifier.PUBLIC
    if (isLocal) it.sharedLocalInspectionTool else it
  }

  /**
   * Runs the inspection on the test data directory `sameParameterValue/<test name>`, see [JavaInspectionFixture.doTest].
   */
  fun doHighlightTest(checkRange: Boolean = false, runDeadCodeFirst: Boolean = false) {
    val inspectionToolWrapper = if (isLocal) {
      LocalInspectionToolWrapper(tool as LocalInspectionTool)
    }
    else GlobalInspectionToolWrapper(tool as GlobalInspectionTool)
    inspection.doTest("sameParameterValue/" + inspection.getTestName(true), inspectionToolWrapper, checkRange, runDeadCodeFirst)
  }
}

/**
 * Creates a [SameParameterValueInspectionFixture] for one test, as [SameParameterValueInspectionTestBase] sets the test up.
 * Declare it as an instance field. The test data is in the `inspection/jvm` directory of the Java test data.
 */
@TestOnly
fun sameParameterValueInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  isLocal: Boolean,
): TestFixture<SameParameterValueInspectionFixture> {
  val inspectionFixture = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm")
  return testFixture("sameParameterValueInspectionFixture") {
    initialized(SameParameterValueInspectionFixture(inspectionFixture.init(), isLocal)) {}
  }
}
