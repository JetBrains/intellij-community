// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import org.intellij.lang.annotations.Language


data class JsonSchemaSetup(@Language("JSON") val schemaJson: String, val configurator: JsonSchemaObject.() -> Unit = {})
fun assertThatSchema(@Language("JSON") schemaJson: String) = JsonSchemaSetup(schemaJson)
fun JsonSchemaSetup.withConfiguration(configurator: JsonSchemaObject.() -> Unit) = copy(configurator = configurator)
internal data class JsonSchemaAppliedToJsonSetup(val schemaSetup: JsonSchemaSetup, @Language("JSON") val json: String)
internal fun JsonSchemaSetup.appliedToJsonFile(@Language("YAML") yaml: String) = JsonSchemaAppliedToJsonSetup(this, yaml)