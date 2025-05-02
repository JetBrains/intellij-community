package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.SwingClientProperty
import com.intellij.notebooks.visualization.context.EditorCellDataContext
import com.intellij.notebooks.visualization.context.NotebookDataContext.NOTEBOOK_CELL_OUTPUT_DATA_KEY
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactoryGetter
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.impl.CollapsingComponent
import com.intellij.notebooks.visualization.outputs.impl.InnerComponent
import com.intellij.notebooks.visualization.outputs.impl.SurroundingComponent
import com.intellij.notebooks.visualization.settings.NotebookSettings
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.JComponent

class EditorCellOutputsView(
  private val editor: EditorImpl,
  private val cell: EditorCell,
  private val onInlayDisposed: (EditorCellOutputsView) -> Unit = {},
) : EditorCellViewComponent(), Disposable {

  var foldingsVisible: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        outputs.forEach { it.folding.visible = value }
      }
    }

  var foldingsSelected: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        outputs.forEach { it.folding.selected = value }
      }
    }

  private val _outputs = mutableListOf<EditorCellOutputView>()
  val outputs: List<EditorCellOutputView>
    get() = _outputs

  internal val innerComponent = InnerComponent().also {
    it.maxHeight = if (!ApplicationManager.getApplication().isUnitTestMode) {
      val outputMaxHeightInEditorLines = NotebookSettings.getInstance().outputMaxHeightInEditorLines
      if (outputMaxHeightInEditorLines <= 0) {
        (Toolkit.getDefaultToolkit().screenSize.height * 0.3).toInt()
      }
      else {
        outputMaxHeightInEditorLines * editor.lineHeight
      }
    }
    else {
      200
    }
  }

  private val surroundingComponent = SurroundingComponent.create(editor, innerComponent)
  private val outerComponent = EditorCellDataContext.createContextProvider(cell, surroundingComponent)

  internal var inlay: Inlay<*>? = null
    private set(value) {
      val oldHeight = field?.heightInPixels ?: 0
      val newHeight = value?.heightInPixels ?: 0

      val shouldUpdate = oldHeight != newHeight
      field = value

      if (shouldUpdate) {
        JupyterBoundsChangeHandler.get(editor).boundsChanged()
      }
    }

  init {
    cell.outputs.outputs.afterChange(this) { keys ->
      updateView(keys)
    }
    cell.outputs.scrollingEnabled.bind(this) {
      innerComponent.scrollingEnabled = it
      innerComponent.revalidate()
    }
    editor.notebookAppearance.editorBackgroundColor.bind(this) {
      surroundingComponent.background = it
    }
    update()
  }

  override fun calculateBounds(): Rectangle {
    return inlay?.bounds ?: Rectangle(0, 0, 0, 0)
  }

  fun update(): Unit = runInEdt {
    updateView(cell.outputs.outputs.get())
  }

  fun updateView(newDataKeys: List<EditorCellOutput>): Unit = runInEdt {
    updateData(newDataKeys)
    recreateInlayIfNecessary()
  }

  private fun recreateInlayIfNecessary() {
    if (outputs.isNotEmpty()) {
      val expectedOffset = computeInlayOffset(editor.document, cell.interval.lines)
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
      inlay?.let {
        Disposer.dispose(it)
        inlay = null
      }
    }
  }

  @RequiresEdt
  private fun updateData(outputs: List<EditorCellOutput>): Boolean {
    val newOutputsIterator = outputs.iterator()
    val oldComponentsWithFactories = getComponentsWithFactories().iterator()
    var isFilled = false
    for ((idx, pair1) in newOutputsIterator.zip(oldComponentsWithFactories).withIndex()) {
      val (output, pair2) = pair1
      val (oldComponent: JComponent, oldFactory: NotebookOutputComponentFactory<*, *>) = pair2
      val outputDataKey = output.dataKey.get()
      isFilled =
        when (oldFactory.matchWithTypes(oldComponent, outputDataKey)) {
          NotebookOutputComponentFactory.Match.NONE -> {
            removeOutput(idx)
            val newComponent = createOutputGuessingFactory(output)
            if (newComponent != null) {
              addIntoInnerComponent(output, newComponent, idx)
              true
            }
            else false
          }
          NotebookOutputComponentFactory.Match.COMPATIBLE -> {
            @Suppress("UNCHECKED_CAST") (oldFactory as NotebookOutputComponentFactory<JComponent, NotebookOutputDataKey>)
            oldFactory.updateComponent(editor, oldComponent, outputDataKey)
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

    for (output in newOutputsIterator) {
      val newComponent = createOutputGuessingFactory(output)
      if (newComponent != null) {
        isFilled = true
        addIntoInnerComponent(output, newComponent)
      }
    }

    return isFilled
  }

  private fun removeOutput(idx: Int) {
    innerComponent.remove(idx)
    val outputComponent = _outputs.removeAt(idx)
    Disposer.dispose(outputComponent)
    remove(outputComponent)
  }

  private fun createOutputGuessingFactory(output: EditorCellOutput): NotebookOutputComponentFactory.CreatedComponent<*>? {
    val outputDataKey = output.dataKey.get()
    return NotebookOutputComponentFactoryGetter.instance.list.asSequence()
      .filter { factory ->
        factory.outputDataKeyClass.isAssignableFrom(outputDataKey.javaClass)
      }
      .mapNotNull { factory ->
        createOutput(@Suppress("UNCHECKED_CAST") (factory as NotebookOutputComponentFactory<*, NotebookOutputDataKey>), output, outputDataKey)
      }
      .firstOrNull()
  }

  private fun <K : NotebookOutputDataKey> createOutput(
    factory: NotebookOutputComponentFactory<*, K>,
    output: EditorCellOutput,
    outputDataKey: K,
  ): NotebookOutputComponentFactory.CreatedComponent<*>? {
    val result = try {
      factory.createComponent(editor, output, outputDataKey)
    }
    catch (t: Throwable) {
      thisLogger().error("${factory.javaClass.name} shouldn't throw exceptions at .createComponent()", t)
      null
    }
    result?.also {
      val component = it.component
      component.outputComponentFactory = factory
      component.gutterPainter = it.gutterPainter

      val disposable = it.disposable
      if (disposable != null) {
        // Parent disposable might be better, but it's better than nothing
        Disposer.register(editor.disposable, disposable)
      }
    }
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
    priority = editor.notebookAppearance.cellOutputToolbarInlayPriority,
    offset = computeInlayOffset(editor.document, cell.interval.lines),
  ).also { inlay ->
    Disposer.register(this, inlay)
    Disposer.register(inlay) {
      onInlayDisposed(this)
    }
  }

  private fun computeInlayOffset(document: Document, lines: IntRange): Int =
    document.getLineEndOffset(lines.last)

  private fun addIntoInnerComponent(output: EditorCellOutput, newComponent: NotebookOutputComponentFactory.CreatedComponent<*>, pos: Int = -1) {
    val collapsingComponent = object : CollapsingComponent(
      editor,
      newComponent.component,
      newComponent.resizable,
      newComponent.collapsedTextSupplier,
    ), UiDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[NOTEBOOK_CELL_OUTPUT_DATA_KEY] = output
      }
    }

    val outputComponent = EditorCellOutputView(editor, output, collapsingComponent, newComponent.disposable)

    innerComponent.add(
      collapsingComponent,
      InnerComponent.Constraint(newComponent.widthStretching, newComponent.limitHeight),
      pos,
    )

    outputComponent.folding.visible = foldingsVisible
    outputComponent.folding.selected = foldingsSelected

    _outputs.add(if (pos == -1) _outputs.size else pos, outputComponent)
    add(outputComponent)

    // DS-1972 Without revalidation, the component would be just invalidated and would be rendered only after anything else requests
    // for repainting the editor.
    newComponent.component.revalidate()
    inlay?.update()
  }

  private fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> = object : Iterator<Pair<A, B>> {
    override fun hasNext(): Boolean = this@zip.hasNext() && other.hasNext()
    override fun next(): Pair<A, B> = this@zip.next() to other.next()
  }

  override fun doCheckAndRebuildInlays() {
    val interval = cell.intervalOrNull ?: let {
      inlay?.let { Disposer.dispose(it) }
      inlay = null
      return
    }
    val offset = computeInlayOffset(editor.document, interval.lines)
    inlay?.let { currentInlay ->
      if (!currentInlay.isValid || currentInlay.offset != offset) {
        currentInlay.let { Disposer.dispose(it) }
        inlay = null
        recreateInlayIfNecessary()
      }
    }
  }

  companion object {
    private var JComponent.outputComponentFactory: NotebookOutputComponentFactory<*, *>? by SwingClientProperty("outputComponentFactory")
  }
}