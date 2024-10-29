// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInsight.util

import com.intellij.platform.diagnostic.telemetry.Scope
import org.jetbrains.annotations.ApiStatus

@JvmField
val HighlightVisitorScope: Scope = Scope("highlightVisitor", verbose = true)