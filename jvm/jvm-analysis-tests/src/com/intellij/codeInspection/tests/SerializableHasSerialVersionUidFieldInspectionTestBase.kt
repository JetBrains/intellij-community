package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.SerializableHasSerialVersionUidFieldInspection

abstract class SerializableHasSerialVersionUidFieldInspectionTestBase : UastInspectionTestBase() {
  override val inspection: InspectionProfileEntry = SerializableHasSerialVersionUidFieldInspection()
}