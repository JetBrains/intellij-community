// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

interface RWActiveListener {
  fun enabled() {}
  fun disabled() {}
  fun initialize() {}
}