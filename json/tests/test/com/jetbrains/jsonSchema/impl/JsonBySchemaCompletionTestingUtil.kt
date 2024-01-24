// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.jetbrains.jsonSchema.extension.JsonSchemaNestedCompletionsTreeProvider
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode
import org.intellij.lang.annotations.Language


data class JsonSchemaSetup(@Language("JSON") val schemaJson: String, val predefinedNestedCompletionsRoot: NestedCompletionsNode? = null)

fun assertThatSchema(@Language("JSON") schemaJson: String) = JsonSchemaSetup(schemaJson)
fun JsonSchemaSetup.withConfiguration(configurator: NestedCompletionsNode) = copy(predefinedNestedCompletionsRoot = configurator)
internal data class JsonSchemaAppliedToJsonSetup(val schemaSetup: JsonSchemaSetup, @Language("JSON") val json: String)

internal fun JsonSchemaSetup.appliedToJsonFile(@Language("YAML") yaml: String) = JsonSchemaAppliedToJsonSetup(this, yaml)


fun testNestedCompletionsWithPredefinedCompletionsRoot(predefinedNestedCompletionsRoot: NestedCompletionsNode?, test: () -> Unit) {
  JsonSchemaNestedCompletionsTreeProvider.EXTENSION_POINT_NAME.maskingExtensions(listOf(predefinedNestedCompletionsRoot.asNestedCompletionsTreeProvider())) {
    test()
  }
}

private fun <T : Any> ExtensionPointName<T>.maskingExtensions(extensions: List<T>, block: () -> Unit) {
  val disposable = Disposer.newDisposable()
  try {
    (point as ExtensionPointImpl).maskAll(extensions, disposable, false)
    block()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun NestedCompletionsNode?.asNestedCompletionsTreeProvider(): JsonSchemaNestedCompletionsTreeProvider = object : JsonSchemaNestedCompletionsTreeProvider {
  override fun getNestedCompletionsRoot(editedFile: PsiFile): NestedCompletionsNode? {
    return this@asNestedCompletionsTreeProvider
  }
}