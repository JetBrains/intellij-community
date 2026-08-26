// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus

private val BACKEND_ID = Key.create<Long>("RangeHighlighter.backendId")

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var RangeHighlighter.backendId: Long?
  get() = getUserData(BACKEND_ID)
  set(value) = putUserData(BACKEND_ID, value)
