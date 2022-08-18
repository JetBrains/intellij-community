// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.target.local

import com.intellij.execution.process.LocalPtyOptions
import com.intellij.execution.target.PtyOptions

class LocalTargetPtyOptions(val localPtyOptions: LocalPtyOptions) : PtyOptions {
  override val initialColumns: Int
    get() = localPtyOptions.initialColumns
  override val initialRows: Int
    get() = localPtyOptions.initialRows
}