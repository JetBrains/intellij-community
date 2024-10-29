// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.openapi.ListSelection

val <T> ListSelection<T>.selectedItem: T?
  get() = list.getOrNull(selectedIndex)