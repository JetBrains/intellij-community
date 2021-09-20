// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal class AutoscrollToSourceToggleAction : DumbAware, ToggleOptionAction({ it.bookmarksViewFromToolWindow?.autoScrollToSource }) {
  init {
    templatePresentation.setText(messagePointer("view.autoscroll.to.source.action.text"))
  }
}
