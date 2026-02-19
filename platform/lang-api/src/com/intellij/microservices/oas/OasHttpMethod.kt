package com.intellij.microservices.oas

enum class OasHttpMethod(val methodName: String) {
  GET("get"),
  PUT("put"),
  POST("post"),
  DELETE("delete"),
  OPTIONS("options"),
  HEAD("head"),
  PATCH("patch"),
  TRACE("trace")
}