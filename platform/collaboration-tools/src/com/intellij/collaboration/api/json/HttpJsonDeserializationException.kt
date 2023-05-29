// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

class HttpJsonDeserializationException : RuntimeException {
  constructor(requestName: String) : super(getMessage(requestName))
  constructor(requestName: String, cause: Throwable) : super(getMessage(requestName), cause)
}

private fun getMessage(requestName: String) = "Deserialization of $requestName response to JSON failed"