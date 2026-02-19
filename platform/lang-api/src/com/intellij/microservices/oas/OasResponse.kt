package com.intellij.microservices.oas

data class OasResponse(
  val code: String,
  val description: String?,
  val content: Map<String, OasMediaTypeObject> = emptyMap(),
  val headers: List<OasHeader> = emptyList()
)