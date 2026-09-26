// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.outputs

import com.intellij.notebooks.visualization.ui.EditorCellOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

interface NotebookOutputComponentFactory<C : JComponent, K : NotebookOutputDataKey> {
  /** Result type of [match]. Not intended to be used elsewhere. */
  enum class Match { NONE, COMPATIBLE, SAME }

  /** Instructs how the component should be stretched horizontally. */
  enum class WidthStretching {
    /** The component gets the width of the visible area regardless of its preferred width. */
    STRETCH_AND_SQUEEZE,

    /** The component is expanded to the width of the visible area if its preferred width is less. */
    STRETCH,

    /** The component is shrinked to the width of the visible area if its preferred width is more. */
    SQUEEZE,

    /** The component has its preferred width. */
    NOTHING,
  }

  /**
   * This mehanizm of execution count holder is obsolete. The business logic that uses this interface should be reviewed and must probably
   *   removed.
   *
   * In previous implementations of the notebooks, the execution count was displayed in the cell output's gutter (see screenshots in DS-851).
   * However, this is not the case anymore. This interface was used to support the gutter text in the cell. However, some parts of the notebooks
   *   contains update logic based on the value of the execution count. It's not clear if this logic is needed or not anymore.
   *
   * As the first refactoring, the logic behind the gutter was removed, leaving only the counter information.
   */
  @ApiStatus.Obsolete
  interface ExecutionCountHolder

  /**
   * @param limitHeight if the height of the component should be limited by 2/3 of visible vertical space. It's  the [component]'s
   * responsibility to handle cutoffs, by using scroll panes, for example. In case when [resizable] is true, it affects only the initial
   * height.
   * @param collapsedTextSupplier every time a user collapses a cell output, this delegate is called, and its result is written
   * in collapsed component's stead.
   * @param resizable defines if the component allowed to be resized by a user.
   *
   * [disposable] will be disposed when system destroys component. Default value is [component] itself if it implements [Disposable]
   */
  data class CreatedComponent<C : JComponent>(
    val component: C,
    val widthStretching: WidthStretching,
    val limitHeight: Boolean,
    val resizable: Boolean,
    val collapsedTextSupplier: () -> @Nls String,
    @ApiStatus.Obsolete val executionCountHolder: ExecutionCountHolder? = null, // See the docs for ExecutionCountHolder
    val disposable: Disposable?,
    val gutterRenderer: GutterIconRenderer? = null,
  )

  val componentClass: Class<C>

  val outputDataKeyClass: Class<K>

  /**
   * Check if the [component] can update it's content with the [outputDataKey].
   *
   * @returns
   *  [Match.NONE] if the [component] can't represent the [outputDataKey].
   *  [Match.COMPATIBLE] if the [component] can represent the [outputDataKey] by calling [updateComponent].
   *  [Match.SAME] if the [component] already represents the [outputDataKey], and call of [updateComponent] would change nothing.
   */
  fun match(component: C, outputDataKey: K): Match

  /**
   * Updates the data representing by the component. Can never be called if [match] with the same arguments returned [Match.NONE].
   */
  fun updateComponent(editor: EditorImpl, component: C, outputDataKey: K)

  /**
   * May return `null` if the factory can't create any component for specific subtype of [NotebookOutputDataKey].
   */
  fun createComponent(editor: EditorImpl, outputDataKey: K): CreatedComponent<C>? = null

  fun createComponent(editor: EditorImpl, output: EditorCellOutput, outputDataKey: K): CreatedComponent<C>? {
    return createComponent(editor, outputDataKey)
  }

  companion object {
    val EP_NAME: ExtensionPointName<NotebookOutputComponentFactory<out JComponent, out NotebookOutputDataKey>> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputComponentFactory")

    var JComponent.executionCountHolder: ExecutionCountHolder?
      get() =
        getClientProperty(ExecutionCountHolder::class.java) as ExecutionCountHolder?
      internal set(value) =
        putClientProperty(ExecutionCountHolder::class.java, value)
  }
}
