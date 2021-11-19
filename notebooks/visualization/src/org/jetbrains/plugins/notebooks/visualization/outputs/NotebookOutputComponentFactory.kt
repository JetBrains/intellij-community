package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Rectangle
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

  interface GutterPainter {
    fun paintGutter(editor: EditorImpl, g: Graphics, r: Rectangle)
  }

  /**
   * @param limitHeight if the height of the component should be limited by 2/3 of visible vertical space. It's  the [component]'s
   * responsibility to handle cutoffs, by using scroll panes, for example.
   * @param collapsedTextSupplier every time a user collapses a cell output, this delegate is called, and its result is written
   * in collapsed component's stead.
   *
   * [disposable] will be disposed when system destroys component. Default value is [component] itself if it implements [Disposable]
   */
  data class CreatedComponent<C : JComponent> (
    val component: C,
    val widthStretching: WidthStretching,
    val gutterPainter: GutterPainter?,
    val limitHeight: Boolean,
    val collapsedTextSupplier: () -> @Nls String,
    val disposable: Disposable? = component as? Disposable
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
  fun createComponent(editor: EditorImpl, output: K): CreatedComponent<C>?

  companion object {
    val EP_NAME: ExtensionPointName<NotebookOutputComponentFactory<out JComponent, out NotebookOutputDataKey>> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputComponentFactory")

    var JComponent.gutterPainter: GutterPainter?
      get() =
        getClientProperty(GutterPainter::class.java) as GutterPainter?
      internal set(value) =
        putClientProperty(GutterPainter::class.java, value)
  }
}
