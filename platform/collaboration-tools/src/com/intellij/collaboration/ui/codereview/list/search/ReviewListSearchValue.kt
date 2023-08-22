// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

interface ReviewListSearchValue {
  val searchQuery: String?
  val filterCount: Int
    get() = if(searchQuery != null) 1 else 0
}