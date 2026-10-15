// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.json

class HttpJsonDeserializationException(requestName: String, cause: Throwable)
  : RuntimeException(getMessage(requestName, cause), cause)

private fun getMessage(requestName: String, cause: Throwable) =
  "Deserialization of $requestName response to JSON failed - ${cause.localizedMessage}"