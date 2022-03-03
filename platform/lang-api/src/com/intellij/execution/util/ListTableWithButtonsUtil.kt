// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.openapi.util.NlsContexts


fun <E, C : ListTableWithButtons<E>> C.setEmptyState(text: @NlsContexts.StatusText String): C = apply {
  tableView.getAccessibleContext().accessibleName = text
  tableView.emptyText.text = text
}

fun <E, C : ListTableWithButtons<E>> C.setVisibleRowCount(count: Int): C = apply {
  tableView.visibleRowCount = count
}