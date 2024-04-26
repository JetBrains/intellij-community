// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic.presentation

import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import kotlinx.html.*
import java.nio.charset.StandardCharsets

internal const val NOT_APPLICABLE = "N/A"
internal const val DID_NOT_START = "Didn't start"

internal val INDEX_DIAGNOSTIC_CSS_STYLES: String
  get() {
    val inputStream = IndexDiagnosticDumper::class.java.getResourceAsStream(
      "/com/intellij/util/indexing/diagnostic/presentation/res/styles.css")
    return inputStream!!.use {
      it.readAllBytes().toString(StandardCharsets.UTF_8)
    }
  }

internal inline fun TR.th(value: String, crossinline block : TH.() -> Unit = {}) {
  th {
    block()
    +value
  }
}
internal inline fun TR.td(value: String, crossinline block : TD.() -> Unit = {}) {
  td {
    if (value == NOT_APPLICABLE || value == DID_NOT_START) {
      classes += "not-applicable-data"
    }
    block()
    +value
  }
}