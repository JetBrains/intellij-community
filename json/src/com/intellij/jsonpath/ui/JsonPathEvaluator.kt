// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.ui

import com.intellij.json.JsonBundle
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serialization.ClassUtil
import com.jayway.jsonpath.*

class JsonPathEvaluator(val jsonFile: JsonFile?,
                        val expression: String,
                        private val evalOptions: Set<Option>) {

  fun evaluate(): EvaluateResult? {
    val jsonPath: JsonPath = try {
      if (expression.isBlank()) return null
      JsonPath.compile(expression)
    }
    catch (ip: InvalidPathException) {
      return IncorrectExpression(ip.localizedMessage)
    }

    val config = Configuration.ConfigurationBuilder()
      .options(evalOptions)
      .build()

    val json = jsonFile?.text
    if (json == null) {
      return IncorrectDocument(JsonBundle.message("jsonpath.evaluate.file.not.found"))
    }

    val jsonDocument: DocumentContext = try {
      JsonPath.parse(json, config)
    }
    catch (e: IllegalArgumentException) {
      return IncorrectDocument(e.localizedMessage)
    }
    catch (ej: InvalidJsonException) {
      if (ej.cause != ej && (ej.message ?: "").contains("ParseException")) {
        // unwrap json-smart ParseException
        val message = ej.cause?.localizedMessage
        if (message != null) {
          return IncorrectDocument(message)
        }
      }
      return IncorrectDocument(ej.localizedMessage)
    }

    val result = try {
      jsonDocument.read<Any>(jsonPath)
    }
    catch (pe: PathNotFoundException) {
      return ResultNotFound(pe.localizedMessage)
    }
    catch (jpe: JsonPathException) {
      return ResultNotFound(jpe.localizedMessage)
    }
    catch (ise: IllegalStateException) {
      return ResultNotFound(ise.localizedMessage)
    }

    return ResultString(toResultString(config, result))
  }

  private fun toResultString(config: Configuration, result: Any?): String {
    if (result == null) return "null"
    if (result is String) return "\"" + StringUtil.escapeStringCharacters(result) + "\""

    if (ClassUtil.isPrimitive(result.javaClass)) {
      return result.toString()
    }

    if (result is Collection<*>) {
      // .keys() result is Set<String>
      return "[" + result.joinToString(", ") {
        toResultString(config, it)
      } + "]"
    }

    return config.jsonProvider().toJson(result) ?: ""
  }
}

sealed class EvaluateResult

data class IncorrectExpression(val message: String) : EvaluateResult()
data class IncorrectDocument(val message: String) : EvaluateResult()
data class ResultNotFound(val message: String) : EvaluateResult()
data class ResultString(val value: String) : EvaluateResult()