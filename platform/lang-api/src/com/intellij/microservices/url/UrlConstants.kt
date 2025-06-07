@file:JvmName("UrlConstants")
@file:Suppress("HttpUrlsUsage")

package com.intellij.microservices.url

@JvmField
val WS_SCHEMES: List<String> = listOf("ws://", "wss://")

const val HTTP_SCHEME: String = "http://"
const val LOCALHOST: String = "localhost"

@JvmField
val HTTP_SCHEMES: List<String> = listOf("http://", "https://")

@JvmField
val KNOWN_SCHEMES_GROUPS: List<List<String>> = listOf(HTTP_SCHEMES, WS_SCHEMES)

@JvmField
val HTTP_METHODS: List<String> = listOf(
  HttpMethods.GET,
  HttpMethods.HEAD,
  HttpMethods.POST,
  HttpMethods.PUT,
  HttpMethods.DELETE,
  HttpMethods.CONNECT,
  HttpMethods.PATCH,
  HttpMethods.OPTIONS,
  HttpMethods.TRACE
)

object HttpMethods {
  const val GET: String = "GET"
  const val HEAD: String = "HEAD"
  const val POST: String = "POST"
  const val PUT: String = "PUT"
  const val DELETE: String = "DELETE"
  const val CONNECT: String = "CONNECT"
  const val PATCH: String = "PATCH"
  const val OPTIONS: String = "OPTIONS"
  const val TRACE: String = "TRACE"
}