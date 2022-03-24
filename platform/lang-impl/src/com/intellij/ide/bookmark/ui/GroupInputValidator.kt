// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.BookmarkBundle.message
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.ValidationInfo
import java.util.function.Supplier
import javax.swing.JComponent

internal class GroupInputValidator(val manager: BookmarksManager, val groups: Collection<BookmarkGroup>) : InputValidatorEx {
  constructor(manager: BookmarksManager, group: BookmarkGroup) : this(manager, listOf(group))
  constructor(manager: BookmarksManager) : this(manager, emptySet())

  override fun getErrorText(name: String?) = when {
    name.isNullOrBlank() -> ""
    manager.getGroup(name.trim()).let { it == null || groups.contains(it) } -> null
    else -> message("dialog.name.exists.error")
  }

  internal fun findValidName(name: String): String {
    val initial = name.trim()
    if (checkInput(initial)) return initial
    for (index in 1..99) {
      val indexed = "$initial ($index)"
      if (checkInput(indexed)) return indexed
    }
    return initial
  }

  internal fun install(parent: Disposable, component: JComponent, text: () -> String?) = ComponentValidator(parent)
    .withValidator(Supplier { getErrorText(text())?.let { ValidationInfo(it, component) } })
    .installOn(component)
}
