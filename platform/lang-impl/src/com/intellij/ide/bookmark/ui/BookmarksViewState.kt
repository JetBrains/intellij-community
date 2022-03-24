// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

class BookmarksViewState : BaseState() {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(BookmarksViewStateComponent::class.java).state
  }

  var proportionPopup by property(0.3f)
  var proportionView by property(0.5f)

  var groupLineBookmarks by property(true)
  var rewriteBookmarkType by property(false)
  var autoscrollFromSource by property(false)
  var autoscrollToSource by property(false)
  var showPreview by property(false)
}

@State(name = "BookmarksViewState", storages = [(Storage(value = StoragePathMacros.WORKSPACE_FILE))])
internal class BookmarksViewStateComponent : SimplePersistentStateComponent<BookmarksViewState>(BookmarksViewState()) {
  override fun noStateLoaded() {
    state.autoscrollToSource = UISettings.getInstance().state.defaultAutoScrollToSource
  }
}
