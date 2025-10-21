// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface LockingProgressSupport {
  fun <T> withWriteActionProgress(title: @NlsContexts.ModalProgressTitle String, action: () -> T): T
}