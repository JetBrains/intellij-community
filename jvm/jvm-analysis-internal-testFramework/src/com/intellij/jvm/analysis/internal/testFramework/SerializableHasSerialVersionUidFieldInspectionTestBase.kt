package com.intellij.jvm.analysis.internal.testFramework

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.SerializableHasSerialVersionUidFieldInspection
import com.intellij.jvm.analysis.testFramework.JvmInspectionTestBase

abstract class SerializableHasSerialVersionUidFieldInspectionTestBase : JvmInspectionTestBase() {
  override val inspection: InspectionProfileEntry = SerializableHasSerialVersionUidFieldInspection()
}