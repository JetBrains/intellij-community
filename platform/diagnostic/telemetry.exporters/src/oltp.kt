// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters

fun normalizeOtlpEndPoint(value: String?): String? {
  var endpoint = value?.takeIf(String::isNotEmpty) ?: return null
  endpoint = if (endpoint == "true") "http://127.0.0.1:4318" else endpoint.removeSuffix("/")
  return "$endpoint/v1/traces"
}
