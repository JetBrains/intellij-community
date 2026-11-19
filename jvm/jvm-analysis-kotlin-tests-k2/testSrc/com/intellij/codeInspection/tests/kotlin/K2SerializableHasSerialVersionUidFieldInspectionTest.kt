package com.intellij.codeInspection.tests.kotlin

import com.intellij.testFramework.junit5.TestApplication

@TestApplication
class K2SerializableHasSerialVersionUidFieldInspectionTest : KotlinSerializableHasSerialVersionUidFieldInspectionTest() {
  override fun getHint(): String = "Add 'const val' property 'serialVersionUID' to 'Foo'"
}