// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.jdom.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Regression coverage for IJPL-249195: `JSON Schema 2019.09` / `2020.12` used to be serialized into
 * `jsonSchemas.xml` as the locale-grouped `JSON Schema 201,909` / `JSON Schema 202,012`.
 */
@TestApplication
class JsonSchemaVersionSerializationTest {
  @Test
  fun testNewerSchemaVersionsAreSerializedWithoutGroupingSeparator() {
    // These are exactly the values from IJPL-249195; they must be stored verbatim, without a grouping separator.
    assertEquals("JSON Schema 2019.09", serializedSchemaVersion(JsonSchemaVersion.SCHEMA_2019_09))
    assertEquals("JSON Schema 2020.12", serializedSchemaVersion(JsonSchemaVersion.SCHEMA_2020_12))
    // The plain single-digit versions were never affected, but pin them down anyway.
    assertEquals("JSON Schema 6", serializedSchemaVersion(JsonSchemaVersion.SCHEMA_6))
    assertEquals("JSON Schema 7", serializedSchemaVersion(JsonSchemaVersion.SCHEMA_7))
  }

  @Test
  fun testEveryVersionRoundTrips() {
    for (version in JsonSchemaVersion.entries) {
      val restored = XmlSerializer.deserialize(serialize(version), UserDefinedJsonSchemaConfiguration::class.java)
      assertEquals(version, restored.schemaVersion, "Round trip failed for $version")
    }
  }

  @Test
  fun testLegacyMalformedValuesAreMigratedRegardlessOfLocale() {
    // Values written by older builds; the grouping separator differs per locale (en-US, de-DE, fr-FR, de-CH).
    assertDeserializedTo("JSON Schema 201,909", JsonSchemaVersion.SCHEMA_2019_09)
    assertDeserializedTo("JSON Schema 201.909", JsonSchemaVersion.SCHEMA_2019_09)
    assertDeserializedTo("JSON Schema 201 909", JsonSchemaVersion.SCHEMA_2019_09)
    assertDeserializedTo("JSON Schema 201’909", JsonSchemaVersion.SCHEMA_2019_09)
    assertDeserializedTo("JSON Schema 202,012", JsonSchemaVersion.SCHEMA_2020_12)
    assertDeserializedTo("JSON Schema 202.012", JsonSchemaVersion.SCHEMA_2020_12)
  }

  private fun serializedSchemaVersion(version: JsonSchemaVersion): String? {
    val option = serialize(version).children.firstOrNull { it.getAttributeValue("name") == "schemaVersion" }
    assertNotNull(option, "The schemaVersion option must be serialized for $version")
    return option!!.getAttributeValue("value")
  }

  private fun serialize(version: JsonSchemaVersion): Element {
    // Populate every @NotNull-backed field so a bare, unrelated field does not derail the round trip.
    val configuration = UserDefinedJsonSchemaConfiguration("test", version, "schema.json", false, emptyList())
    configuration.setGeneratedName("test")
    return XmlSerializer.serialize(configuration)
  }

  private fun assertDeserializedTo(serializedValue: String, expected: JsonSchemaVersion) {
    val schemaInfo = Element("SchemaInfo")
    schemaInfo.addContent(Element("option").setAttribute("name", "schemaVersion").setAttribute("value", serializedValue))
    val restored = XmlSerializer.deserialize(schemaInfo, UserDefinedJsonSchemaConfiguration::class.java)
    assertEquals(expected, restored.schemaVersion, "'$serializedValue' must deserialize to $expected")
  }
}
