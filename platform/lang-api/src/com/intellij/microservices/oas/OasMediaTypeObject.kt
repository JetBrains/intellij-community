package com.intellij.microservices.oas

class OasMediaTypeObject(
  val schema: OasSchema,
  val examples: Map<String, OasExample>
)