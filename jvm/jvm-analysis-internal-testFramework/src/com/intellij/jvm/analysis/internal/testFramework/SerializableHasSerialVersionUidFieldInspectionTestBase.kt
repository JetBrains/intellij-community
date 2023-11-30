package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.SerializableHasSerialVersionUidFieldInspection

abstract class SerializableHasSerialVersionUidFieldInspectionTestBase : JvmSdkInspectionTestBase() {
  override val inspection: InspectionProfileEntry = SerializableHasSerialVersionUidFieldInspection()
}