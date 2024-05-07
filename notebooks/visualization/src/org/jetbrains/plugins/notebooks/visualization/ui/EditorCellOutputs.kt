package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.SwingClientProperty
import org.jetbrains.plugins.notebooks.visualization.outputs.*
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.InnerComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.SurroundingComponent
import org.jetbrains.plugins.notebooks.visualization.ui.EditorCellView.NotebookCellDataProvider
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

class EditorCellOutputs(
  private val editor: EditorImpl,
  private val interval: () -> NotebookCellLines.Interval,
  private val onInlayDisposed: (EditorCellOutputs) -> Unit = {}
) {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  private val _outputs = mutableListOf<EditorCellOutput>()
  val outputs
    get() = _outputs

  private val innerComponent = InnerComponent().also {
    it.maxHeight = if (!ApplicationManager.getApplication().isUnitTestMode) {
      (Toolkit.getDefaultToolkit().screenSize.height * 0.3).toInt()
    }
    else {
      200
    }
  }
  private val outerComponent = SurroundingComponent.create(editor, innerComponent).also {
    DataManager.registerDataProvider(it, NotebookCellDataProvider(editor, it, interval))
  }
  private var inlay: Inlay<*>? = null

  val bounds: Rectangle?
    get() {
      return inlay?.bounds
    }

  init {
    update()
  }

  fun dispose() {
    outputs.forEach { it.dispose() }
    inlay?.let { Disposer.dispose(it) }
  }

  fun updatePositions() {
    val b = bounds
    if (b != null) {
      cellEventListeners.multicaster.componentBoundaryChanged(b.location, b.size)
      outputs.forEach { it.updatePositions() }
    }
  }

  fun onViewportChange() {
    outputs.forEach { it.onViewportChange() }
  }

  fun updateSelection(selected: Boolean) {
    outputs.forEach { it.updateSelection(selected) }
  }

  fun showFolding() {
    outputs.forEach { it.showFolding() }
  }

  fun hideFolding() {
    outputs.forEach { it.hideFolding() }
  }

  fun update() {
    val outputDataKeys =
      NotebookOutputDataKeyExtractor.EP_NAME.extensionList.asSequence()
        .mapNotNull { it.extract(editor, interval()) }
        .firstOrNull()
        ?.takeIf { it.isNotEmpty() }
      ?: emptyList()
    updateData(outputDataKeys)
    recreateInlayIfNecessary()
  }

  private fun recreateInlayIfNecessary() {
    if (outputs.size > 0) {
      val expectedOffset = computeInlayOffset(editor.document, interval().lines)
      val currentInlay = inlay
      if (currentInlay != null) {
        if (currentInlay.offset != expectedOffset) {
          Disposer.dispose(currentInlay)
          inlay = createInlay()
        }
      }
      else {
        inlay = createInlay()
      }
    }
    else {
      inlay?.let { Disposer.dispose(it) }
    }
  }

  private fun updateData(newDataKeys: List<NotebookOutputDataKey>): Boolean {
    val newDataKeyIterator = newDataKeys.iterator()
    val oldComponentsWithFactories = getComponentsWithFactories().iterator()
    var isFilled = false
    for ((idx, pair1) in newDataKeyIterator.zip(oldComponentsWithFactories).withIndex()) {
      val (newDataKey, pair2) = pair1
      val (oldComponent: JComponent, oldFactory: NotebookOutputComponentFactory<*, *>) = pair2
      isFilled =
        when (oldFactory.matchWithTypes(oldComponent, newDataKey)) {
          NotebookOutputComponentFactory.Match.NONE -> {
            removeOutput(idx)
            val newComponent = createOutputGuessingFactory(newDataKey)
            if (newComponent != null) {
              addIntoInnerComponent(newComponent, idx)
              true
            }
            else false
          }
          NotebookOutputComponentFactory.Match.COMPATIBLE -> {
            @Suppress("UNCHECKED_CAST") (oldFactory as NotebookOutputComponentFactory<JComponent, NotebookOutputDataKey>)
            oldFactory.updateComponent(editor, oldComponent, newDataKey)
            oldComponent.parent.asSafely<CollapsingComponent>()?.updateStubIfCollapsed()
            true
          }
          NotebookOutputComponentFactory.Match.SAME -> true
        } || isFilled
    }

    for (ignored in oldComponentsWithFactories) {
      val idx = innerComponent.componentCount - 1
      removeOutput(idx)
    }

    for (outputDataKey in newDataKeyIterator) {
      val newComponent = createOutputGuessingFactory(outputDataKey)
      if (newComponent != null) {
        isFilled = true
        addIntoInnerComponent(newComponent)
      }
    }

    return isFilled
  }

  private fun removeOutput(idx: Int) {
    innerComponent.remove(idx)
    _outputs.removeAt(idx).dispose()
  }

  private fun <K : NotebookOutputDataKey> createOutputGuessingFactory(outputDataKey: K): NotebookOutputComponentFactory.CreatedComponent<*>? =
    NotebookOutputComponentFactoryGetter.instance.list.asSequence()
      .filter { factory ->
        factory.outputDataKeyClass.isAssignableFrom(outputDataKey.javaClass)
      }
      .mapNotNull { factory ->
        createOutput(@Suppress("UNCHECKED_CAST") (factory as NotebookOutputComponentFactory<*, K>), outputDataKey)
      }
      .firstOrNull()

  private fun <K : NotebookOutputDataKey> createOutput(factory: NotebookOutputComponentFactory<*, K>,
                                                       outputDataKey: K): NotebookOutputComponentFactory.CreatedComponent<*>? {
    val lines = interval().lines
    ApplicationManager.getApplication().messageBus.syncPublisher(OUTPUT_LISTENER).beforeOutputCreated(editor, lines.last)
    val result = try {
      factory.createComponent(editor, outputDataKey)
    }
    catch (t: Throwable) {
      thisLogger().error("${factory.javaClass.name} shouldn't throw exceptions at .createComponent()", t)
      null
    }
    result?.also {
      it.component.outputComponentFactory = factory
      it.component.gutterPainter = it.gutterPainter
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(OUTPUT_LISTENER).outputCreated(editor, lines.last)
    return result
  }

  private fun getComponentsWithFactories() = mutableListOf<Pair<JComponent, NotebookOutputComponentFactory<*, *>>>().also {
    for (component in innerComponent.mainComponents) {
      val factory = component.outputComponentFactory
      if (factory != null) {
        it += component to factory
      }
    }
  }

  private fun <C : JComponent, K : NotebookOutputDataKey> NotebookOutputComponentFactory<C, K>.matchWithTypes(
    component: JComponent, outputDataKey: NotebookOutputDataKey,
  ) =
    when {
      !componentClass.isAssignableFrom(component.javaClass) -> NotebookOutputComponentFactory.Match.NONE  // TODO Is the method right?
      !outputDataKeyClass.isAssignableFrom(outputDataKey.javaClass) -> NotebookOutputComponentFactory.Match.NONE
      else -> @Suppress("UNCHECKED_CAST") match(component as C, outputDataKey as K)
    }

  private fun createInlay() = editor.addComponentInlay(
    outerComponent,
    isRelatedToPrecedingText = true,
    showAbove = false,
    priority = editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY,
    offset = computeInlayOffset(editor.document, interval().lines),
  ).also {
    it.renderer.asSafely<JComponent>()?.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        cellEventListeners.multicaster.componentBoundaryChanged(e.component.location, e.component.size)
      }
    })
    Disposer.register(it) {
      onInlayDisposed(this)
    }
  }

  private fun computeInlayOffset(document: Document, lines: IntRange): Int =
    document.getLineEndOffset(lines.last)

  private fun addIntoInnerComponent(newComponent: NotebookOutputComponentFactory.CreatedComponent<*>, pos: Int = -1) {
    val collapsingComponent = CollapsingComponent(
      editor,
      newComponent.component,
      newComponent.resizable,
      newComponent.collapsedTextSupplier,
    )

    innerComponent.add(
      collapsingComponent,
      InnerComponent.Constraint(newComponent.widthStretching, newComponent.limitHeight),
      pos,
    )

    _outputs.add(if (pos == -1) _outputs.size else pos, EditorCellOutput(editor, collapsingComponent, newComponent.disposable))

    // DS-1972 Without revalidation, the component would be just invalidated, and would be rendered only after anything else requests
    // for repainting the editor.
    newComponent.component.revalidate()
  }

  private fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> = object : Iterator<Pair<A, B>> {
    override fun hasNext(): Boolean = this@zip.hasNext() && other.hasNext()
    override fun next(): Pair<A, B> = this@zip.next() to other.next()
  }

  fun paintGutter(editor: EditorImpl,
                  g: Graphics,
                  r: Rectangle) {
    val yOffset = innerComponent.yOffsetFromEditor(editor) ?: return

    val oldClip = g.clipBounds
    val ng = g.create() as Graphics2D
    ng.clip(Rectangle(oldClip.x, yOffset, oldClip.width, innerComponent.height).intersection(oldClip))
    outputs.forEach {
      it.paintGutter(editor, yOffset, ng, r)
    }
  }
}

private var JComponent.outputComponentFactory: NotebookOutputComponentFactory<*, *>? by SwingClientProperty("outputComponentFactory")
