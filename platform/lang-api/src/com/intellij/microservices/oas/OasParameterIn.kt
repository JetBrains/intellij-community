package com.intellij.microservices.oas

enum class OasParameterIn(val placeName: String) {
  PATH("path"),
  QUERY("query"),
  HEADER("header"),
  COOKIE("cookie")
}

fun valueOfParameter(paramName: String): OasParameterIn? = enumValues<OasParameterIn>().find { it.placeName == paramName }
