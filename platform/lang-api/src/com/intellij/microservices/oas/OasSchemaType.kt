package com.intellij.microservices.oas

enum class OasSchemaType(val typeName: String) {
  INTEGER("integer"),
  NUMBER("number"),
  STRING("string"),
  BOOLEAN("boolean"),
  OBJECT("object"),
  ARRAY("array"),
}