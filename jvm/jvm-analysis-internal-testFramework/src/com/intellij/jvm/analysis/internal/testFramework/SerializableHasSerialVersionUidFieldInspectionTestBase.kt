package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.SerializableHasSerialVersionUidFieldInspection

/**
 * @deprecated Use [jvmSdkProjectFixture] and [serializableHasSerialVersionUidFieldInspectionFixture] in a JUnit 5 test.
 */
@Suppress("DEPRECATION")
@Deprecated("Use jvmSdkProjectFixture and serializableHasSerialVersionUidFieldInspectionFixture in a JUnit 5 test")
abstract class SerializableHasSerialVersionUidFieldInspectionTestBase : JvmSdkInspectionTestBase() {
  override val inspection: SerializableHasSerialVersionUidFieldInspection = SerializableHasSerialVersionUidFieldInspection()
}
