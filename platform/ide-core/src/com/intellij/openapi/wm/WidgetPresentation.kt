// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import javax.swing.Icon

@ApiStatus.Experimental
interface WidgetPresentation {
  suspend fun getTooltipText(): @NlsContexts.Tooltip String? = null

  suspend fun getShortcutText(): @Nls String? = null

  fun getClickConsumer(): ((MouseEvent) -> Unit)? = null
}

@ApiStatus.Experimental
interface WidgetPresentationFactory {
  fun createPresentation(context: WidgetPresentationDataContext, scope: CoroutineScope): WidgetPresentation
}

@ApiStatus.Experimental
interface TextWidgetPresentation : WidgetPresentation {
  /**
   * Taken on account only on a first init, dynamic change is not supported.
   */
  val alignment: Float

  /**
   * Using `distinctUntilChanged` is not required - handled by a platform.
   */
  fun text(): Flow<@NlsContexts.Label String?>
}

@ApiStatus.Experimental
interface IconWidgetPresentation : WidgetPresentation {
  /**
   * Using `distinctUntilChanged` is not required - handled by a platform.
   */
  fun icon(): Flow<Icon?>
}

@ApiStatus.Experimental
interface WidgetPresentationDataContext {
  val project: Project

  val currentFileEditor: StateFlow<FileEditor?>
}