package com.intellij.codeInspection.tests.kotlin

class K2SerializableHasSerialVersionUidFieldInspectionTest : KotlinSerializableHasSerialVersionUidFieldInspectionTest() {
  override fun getHint(): String = "Add 'const val' property 'serialVersionUID' to 'Foo'"
}