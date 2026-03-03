package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore

@Ignore("AT-3959")
class K2SerializableHasSerialVersionUidFieldInspectionTest : KotlinSerializableHasSerialVersionUidFieldInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
  override fun getHint(): String = "Add 'private const val' property 'serialVersionUID' to 'Foo'"
}