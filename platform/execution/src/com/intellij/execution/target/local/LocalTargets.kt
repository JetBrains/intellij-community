// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LocalTargets")

package com.intellij.execution.target.local

import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.PtyOptions

fun PtyOptions.toLocalPtyOptions(): LocalPtyOptions =
  when (this) {
    is LocalTargetPtyOptions -> localPtyOptions
    else -> {
      LocalPtyOptions.DEFAULT.builder()
        .initialRows(initialRows)
        .initialColumns(initialColumns)
        .build()
    }
  }