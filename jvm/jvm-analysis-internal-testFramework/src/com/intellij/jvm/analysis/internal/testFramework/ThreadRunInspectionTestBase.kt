package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.ThreadRunInspection

/**
 * @deprecated Use [jvmSdkProjectFixture] and [threadRunInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use jvmSdkProjectFixture and threadRunInspectionFixture in a JUnit 5 test")
abstract class ThreadRunInspectionTestBase : JvmSdkInspectionTestBase() {
  override var inspection: ThreadRunInspection = ThreadRunInspection()
}
