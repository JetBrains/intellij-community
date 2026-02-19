// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayActionPayload
import org.jetbrains.annotations.ApiStatus

/** Marker interface for payloads, which are not supposed to be persisted (to markup cache). */
@ApiStatus.Internal
interface NonPersistableInlayActionPayload : InlayActionPayload