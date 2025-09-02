// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.ex

import com.intellij.openapi.editor.Document
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface DocumentFullUpdateListener: EventListener {
  fun onFullUpdateDocument(document: Document)
}