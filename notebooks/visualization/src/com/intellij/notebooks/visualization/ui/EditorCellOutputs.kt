package com.intellij.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.notebooks.ui.visualization.notebookAppearance
import com.intellij.notebooks.visualization.SwingClientProperty
import com.intellij.notebooks.visualization.inlay.JupyterBoundsChangeHandler
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactoryGetter
import com.intellij.notebooks.visualization.outputs.NotebookOutputDataKey
import com.intellij.notebooks.visualization.outputs.impl.CollapsingComponent
import com.intellij.notebooks.visualization.outputs.impl.InnerComponent
import com.intellij.notebooks.visualization.outputs.impl.SurroundingComponent
import com.intellij.notebooks.visualization.ui.EditorCellView.NotebookCellDataProvider
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

class EditorCellOutputs(
  private val editor: EditorImpl,
  private val cell: EditorCell,
  private val onInlayDisposed: (EditorCellOutputs) -> Unit = {},
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

  private val _outputs = mutableListOf<EditorCellOutput>()
  val outputs: List<EditorCellOutput>
    get() = _outputs

  internal val innerComponent = InnerComponent().also {
    it.maxHeight = if (!ApplicationManager.getApplication().isUnitTestMode) {
      (Toolkit.getDefaultToolkit().screenSize.height * 0.3).toInt()
    }
    else {
      200
    }
  }

  private val outerComponent = SurroundingComponent.create(editor, innerComponent).also {
    DataManager.registerDataProvider(it, NotebookCellDataProvider(editor, it) { cell.interval })
  }

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
    cell.outputs.afterChange(this) { keys ->
      updateView(keys)
    }
    update()
  }

  override fun calculateBounds(): Rectangle {
    return inlay?.bounds ?: Rectangle(0, 0, 0, 0)
  }

  fun update() = runInEdt {
    updateView(cell.outputs.get())
    onViewportChange()
  }

  fun updateView(newDataKeys: Collection<NotebookOutputDataKey>) = runInEdt {
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
  private fun updateData(newDataKeys: Collection<NotebookOutputDataKey>): Boolean {
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
    val outputComponent = _outputs.removeAt(idx)
    Disposer.dispose(outputComponent)
    remove(outputComponent)
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

  private fun <K : NotebookOutputDataKey> createOutput(
    factory: NotebookOutputComponentFactory<*, K>,
    outputDataKey: K,
  ): NotebookOutputComponentFactory.CreatedComponent<*>? {
    val result = try {
      factory.createComponent(editor, outputDataKey)
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
    priority = editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY,
    offset = computeInlayOffset(editor.document, cell.interval.lines),
  ).also { inlay ->
    Disposer.register(this, inlay)
    Disposer.register(inlay) {
      onInlayDisposed(this)
    }
  }

  private fun computeInlayOffset(document: Document, lines: IntRange): Int =
    document.getLineEndOffset(lines.last)

  private fun addIntoInnerComponent(newComponent: NotebookOutputComponentFactory.CreatedComponent<*>, pos: Int = -1) {
    lateinit var outputComponent: EditorCellOutput
    val collapsingComponent = object : CollapsingComponent(
      editor,
      newComponent.component,
      newComponent.resizable,
      newComponent.collapsedTextSupplier,
    ), UiDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[NOTEBOOK_CELL_OUTPUT_DATA_KEY] = outputComponent
      }
    }

    outputComponent = EditorCellOutput(editor, collapsingComponent, newComponent.disposable)

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
  }

  private fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> = object : Iterator<Pair<A, B>> {
    override fun hasNext(): Boolean = this@zip.hasNext() && other.hasNext()
    override fun next(): Pair<A, B> = this@zip.next() to other.next()
  }

  override fun doGetInlays(): Sequence<Inlay<*>> {
    return inlay?.let { sequenceOf(it) } ?: emptySequence()
  }

  override fun doCheckAndRebuildInlays() {
    val offset = computeInlayOffset(editor.document, cell.interval.lines)
    inlay?.let { currentInlay ->
      if (!currentInlay.isValid || currentInlay.offset != offset) {
        currentInlay.let { Disposer.dispose(it) }
        inlay = null
        recreateInlayIfNecessary()
      }
    }
  }
}

private var JComponent.outputComponentFactory: NotebookOutputComponentFactory<*, *>? by SwingClientProperty("outputComponentFactory")