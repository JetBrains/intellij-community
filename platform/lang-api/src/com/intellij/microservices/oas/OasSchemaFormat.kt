package com.intellij.microservices.oas

enum class OasSchemaFormat(val formatName: String) {
  INT_32("int32"),
  INT_64("int64"),
  FLOAT("float"),
  DOUBLE("double"),
  BYTE("byte"),
  BINARY("binary"),
  DATE("date"),
  DATE_TIME("date-time"),
  PARTIAL_TIME("partial-time"),
  PASSWORD("password"),
  URI("uri"),
  URL("url"),
  UUID("uuid")
}