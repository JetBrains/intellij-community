// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.util.indexing.diagnostic.dto


import com.intellij.util.indexing.diagnostic.TimeMillis
import com.intellij.util.indexing.diagnostic.TimeNano
import org.jetbrains.annotations.ApiStatus

fun TimeNano.toMillis(): TimeMillis = this / 1_000_000
