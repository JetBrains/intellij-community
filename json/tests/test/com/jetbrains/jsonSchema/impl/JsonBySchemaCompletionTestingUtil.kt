// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.jetbrains.jsonSchema.extension.JsonSchemaNestedCompletionsTreeProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaShorthandValueHandler
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode
import com.jetbrains.jsonSchema.impl.nestedCompletions.buildNestedCompletionsTree
import org.intellij.lang.annotations.Language


data class JsonSchemaSetup(@Language("JSON") val schemaJson: String, val predefinedNestedCompletionsRoot: NestedCompletionsNode? = null)

fun assertThatSchema(@Language("JSON") schemaJson: String) = JsonSchemaSetup(schemaJson)
fun JsonSchemaSetup.withConfiguration(configurator: NestedCompletionsNode) = copy(predefinedNestedCompletionsRoot = configurator)
internal data class JsonSchemaAppliedToJsonSetup(val schemaSetup: JsonSchemaSetup, @Language("JSON") val json: String)

internal fun JsonSchemaSetup.appliedToJsonFile(@Language("YAML") sourceText: String) = JsonSchemaAppliedToJsonSetup(this, sourceText)


fun testNestedCompletionsWithPredefinedCompletionsRoot(predefinedNestedCompletionsRoot: NestedCompletionsNode?, test: () -> Unit) {
  JsonSchemaNestedCompletionsTreeProvider.EXTENSION_POINT_NAME.maskingExtensions(listOf(predefinedNestedCompletionsRoot.asNestedCompletionsTreeProvider())) {
    test()
  }
}

private fun <T : Any> ExtensionPointName<T>.maskingExtensions(extensions: List<T>, block: () -> Unit) {
  val disposable = Disposer.newDisposable()
  try {
    ExtensionTestUtil.maskExtensions(this, extensions, disposable)
    block()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

fun addShorthandValueHandlerForEnabledField(testRootDisposable: Disposable) {
  val shorthandValueHandler = object : JsonSchemaShorthandValueHandler {
    override fun isApplicable(file: PsiFile): Boolean = true

    override fun expandShorthandValue(path: List<String>, value: String): JsonSchemaShorthandValueHandler.KeyValue? {
      if (value == "enabled") {
        return JsonSchemaShorthandValueHandler.KeyValue("enabled", "true")
      }
      return null
    }
  }

  ExtensionTestUtil.maskExtensions(JsonSchemaShorthandValueHandler.EXTENSION_POINT_NAME, listOf(shorthandValueHandler), testRootDisposable)
}

private fun NestedCompletionsNode?.asNestedCompletionsTreeProvider(): JsonSchemaNestedCompletionsTreeProvider = object : JsonSchemaNestedCompletionsTreeProvider {
  override fun getNestedCompletionsRoot(editedFile: PsiFile): NestedCompletionsNode? {
    return this@asNestedCompletionsTreeProvider
  }
}

object TestSchemas {
  val open1ThenOpen2Then3Schema
    get() = assertThatSchema("""
     {
       "properties": {
         "one": {
           "properties": {
             "two": {
               "properties": {
                 "three": {
                   "type": "boolean"
                 }
               }
             }
           }
         }
       }
     }
    """.trimIndent())
       .withConfiguration(
         buildNestedCompletionsTree {
           open("one") {
             open("two")
           }
         }
       )

  val settingWithEnabledShorthand
    get() = assertThatSchema("""
      {
        "properties": {
          "setting": {
            "anyOf": [
              {
                "properties": {
                  "enabled": {
                    "type": "boolean"
                  },
                  "value": {
                    "type": "string"
                  }
                }
              },
              {
                "enum": ["enabled"]
              }
            ] 
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("setting")
        }
      )

  val settingWithEnabledShorthandAndCustomization
    get() = assertThatSchema("""
      {
        "properties": {
          "setting": {
            "anyOf": [
              {
                "properties": {
                  "enabled": {
                    "type": "boolean"
                  },
                  "customization": {
                    "properties": {
                      "value": {
                        "type": "string"
                      }
                    }
                  }
                }
              },
              {
                "enum": ["enabled"]
              }
            ] 
          }
        }
      }
    """.trimIndent())
      .withConfiguration(
        buildNestedCompletionsTree {
          open("setting") {
            open("customization")
          }
        }
      )
}
