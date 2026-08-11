// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion

class NetworkntSchemaValidator(version: SpecificationVersion) {
  private val registry: SchemaRegistry = SchemaRegistry.withDefaultDialect(version)

  fun validate(schemaJson: String, instanceJson: String): List<com.networknt.schema.Error> {
    return registry.validate(schemaJson, instanceJson)
  }

  fun getSchema(schemaJson: String): Schema {
    return registry.getSchema(schemaJson)
  }
}
