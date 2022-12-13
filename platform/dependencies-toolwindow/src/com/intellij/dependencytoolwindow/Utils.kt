// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dependencytoolwindow

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

@Suppress("FunctionName")
internal fun SelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return object : ContentManagerListener {
    override fun selectionChanged(event: ContentManagerEvent) {
      action(event)
    }
  }
}

internal fun ContentManager.addSelectionChangedListener(action: (ContentManagerEvent) -> Unit): ContentManagerListener {
  return SelectionChangedListener(action).also(::addContentManagerListener)
}

internal const val BUNDLE_NAME = "messages.dependenciesToolwindow"

