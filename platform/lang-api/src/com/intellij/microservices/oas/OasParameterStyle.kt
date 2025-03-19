package com.intellij.microservices.oas

enum class OasParameterStyle(val value: String) {
  DEFAULT(""),
  MATRIX("matrix"),
  LABEL("label"),
  FORM("form"),
  SPACE_DELIMITED("spaceDelimited"),
  PIPE_DELIMITED("pipeDelimited"),
  DEEP_OBJECT("deepObject"),
  SIMPLE("simple");
}