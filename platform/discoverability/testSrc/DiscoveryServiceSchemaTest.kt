// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.discoverability

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.networknt.schema.InputFormat
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import java.io.ByteArrayOutputStream
import java.net.InetAddress

class DiscoveryServiceSchemaTest : BasePlatformTestCase() {
  fun `test discovery info JSON matches schema`() {
    val schemaStream = javaClass.classLoader.getResourceAsStream("com/intellij/platform/discoverability/ide-instance-schema.json")
                       ?: error("Schema resource not found on classpath")
    val mapper = ObjectMapper()
    val schemaNode = schemaStream.use { mapper.readTree(it) }

    // Remove "format" keywords to avoid runtime dependency on com.ethlo.time:itu.
    removeFormatKeywords(schemaNode)

    val schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
      .getSchema(mapper.writeValueAsString(schemaNode))

    val out = ByteArrayOutputStream()
    writeDiscoveryInfoJson(out, InetAddress.getLoopbackAddress(), 63342)

    val errors = schema.validate(out.toString(Charsets.UTF_8.name()), InputFormat.JSON)
    assertTrue("JSON schema validation errors:\n${errors.joinToString("\n") { it.message }}", errors.isEmpty())
  }

  private fun removeFormatKeywords(node: JsonNode) {
    if (node is ObjectNode) {
      node.remove("format")
      node.properties().forEach { (_, value) -> removeFormatKeywords(value) }
    }
    else if (node.isArray) {
      node.forEach { removeFormatKeywords(it) }
    }
  }
}
