// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionData
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationEntry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class InlayMouseArea(val actionData: InlayActionData) {
  val entries: MutableList<InlayPresentationEntry> = ArrayList()
}