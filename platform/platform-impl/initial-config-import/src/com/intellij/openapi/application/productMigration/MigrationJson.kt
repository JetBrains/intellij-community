// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.productMigration

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.core.ObjectReadContext
import tools.jackson.core.ObjectWriteContext
import tools.jackson.core.json.JsonFactory
import java.io.StringWriter

/*
 * The JSON files of the migration are read before the application services exist, so they use the jackson streaming API.
 */

private val jsonFactory = JsonFactory()

/** The reason why a migration JSON file is not valid. */
internal class InvalidMigrationJsonException(reason: String) : Exception(reason)

internal fun invalidMigrationJson(reason: String): Nothing = throw InvalidMigrationJsonException(reason)

/**
 * Calls [property] for each property of the top-level object of [text]. [property] must read or skip the value.
 * A malformed text, a text that is not one object, and a value of a wrong type throw [InvalidMigrationJsonException].
 */
internal fun parseMigrationJson(text: String, property: (parser: JsonParser, name: String) -> Unit) {
  try {
    jsonFactory.createParser(ObjectReadContext.empty(), text).use { parser ->
      if (parser.nextToken() != JsonToken.START_OBJECT) invalidMigrationJson("not a JSON object")
      readMigrationJsonObject(parser, property)
      if (parser.nextToken() != null) invalidMigrationJson("content after the JSON object")
    }
  }
  catch (e: JacksonException) {
    invalidMigrationJson("malformed JSON: ${e.originalMessage}")
  }
}

/** Calls [property] for each property of the object at the current token of [parser]. */
internal fun readMigrationJsonObject(parser: JsonParser, property: (parser: JsonParser, name: String) -> Unit) {
  if (parser.currentToken() != JsonToken.START_OBJECT) invalidMigrationJson("'${parser.currentName()}' is not an object")
  while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
    val name = parser.currentName()
    parser.nextToken()
    property(parser, name)
  }
}

internal fun JsonParser.stringOrNull(): String? = when (currentToken()) {
  JsonToken.VALUE_STRING -> string
  JsonToken.VALUE_NULL -> null
  else -> invalidMigrationJson("'${currentName()}' is not a string")
}

internal fun JsonParser.intOrNull(): Int? = when (currentToken()) {
  JsonToken.VALUE_NUMBER_INT -> intValue
  JsonToken.VALUE_NULL -> null
  else -> invalidMigrationJson("'${currentName()}' is not an integer")
}

/** Reads a base64 string. */
internal fun JsonParser.binaryOrNull(): ByteArray? = when (currentToken()) {
  JsonToken.VALUE_STRING -> binaryValue
  JsonToken.VALUE_NULL -> null
  else -> invalidMigrationJson("'${currentName()}' is not a base64 string")
}

/** Writes one JSON object with [content]. */
internal fun formatMigrationJson(content: JsonGenerator.() -> Unit): String {
  val writer = StringWriter()
  jsonFactory.createGenerator(ObjectWriteContext.empty(), writer).use { generator ->
    generator.writeStartObject()
    generator.content()
    generator.writeEndObject()
  }
  return writer.toString()
}
