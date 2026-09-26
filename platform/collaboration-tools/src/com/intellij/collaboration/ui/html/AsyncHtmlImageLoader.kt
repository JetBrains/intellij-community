// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.html

import com.intellij.openapi.util.Key
import java.awt.Image
import java.net.URL

fun interface AsyncHtmlImageLoader {
  suspend fun load(baseUrl: URL?, src: String): Image?

  companion object {
    val KEY: Key<AsyncHtmlImageLoader> = Key.create("Async.Html.Image.Loader")
  }
}