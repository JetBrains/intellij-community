package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.castSafelyTo
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.SwingClientProperty
import org.jetbrains.plugins.notebooks.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.InnerComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.SurroundingComponent
import org.jetbrains.plugins.notebooks.visualization.ui.addComponentInlay
import org.jetbrains.plugins.notebooks.visualization.ui.yOffsetFromEditor
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.JComponent

private const val DEFAULT_INLAY_HEIGHT = 200

// ToDo: merge with NotebookOutputListener
interface OutputListener {
  fun beforeOutputCreated(editor: Editor, line: Int) {}
  fun outputCreated(editor: Editor, line: Int) {}
}
val OUTPUT_LISTENER: Topic<OutputListener> = Topic.create("OutputAdded", OutputListener::class.java)

val EditorCustomElementRenderer.notebookInlayOutputComponent: JComponent?
  get() = castSafelyTo<JComponent>()?.components?.firstOrNull()?.castSafelyTo<SurroundingComponent>()

val EditorCustomElementRenderer.notebookCellOutputComponents: List<JComponent>?
  get() = notebookInlayOutputComponent?.components?.firstOrNull()?.castSafelyTo<JComponent>()?.components?.map { it as JComponent }

/**
 * Shows outputs for intervals using [NotebookOutputDataKeyExtractor] and [NotebookOutputComponentFactory].
 */
class NotebookOutputInlayController private constructor(
  override val factory: NotebookCellInlayController.Factory,
  private val editor: EditorImpl,
  private val lines: IntRange,
) : NotebookCellInlayController {

  private companion object {
    private val key = NotebookOutputInlayController::class.qualifiedName!!
    fun JComponent.addDisposable(disposable: Disposable) {
      putClientProperty(key, disposable)
    }

    fun JComponent.disposeComponent() {
      (getClientProperty(key) as? Disposable)?.let(Disposer::dispose)
    }
  }

  private val innerComponent = InnerComponent(editor)
  private val outerComponent = SurroundingComponent.create(editor, innerComponent)

  override val inlay: Inlay<*> =
    editor.addComponentInlay(
      outerComponent,
      isRelatedToPrecedingText = true,
      showAbove = false,
      priority = editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY,
      offset = editor.document.getLineEndOffset(lines.last),
    )

  init {
    innerComponent.maxHeight = if (!ApplicationManager.getApplication().isUnitTestMode) {
      (Toolkit.getDefaultToolkit().screenSize.height * 0.3).toInt()
    }
    else {
      DEFAULT_INLAY_HEIGHT
    }

    Disposer.register(inlay) {
      for (disposable in innerComponent.mainComponents) {
        disposable.disposeComponent()
      }
    }
  }

  override fun paintGutter(editor: EditorImpl, g: Graphics, r: Rectangle, intervalIterator: ListIterator<NotebookCellLines.Interval>) {
    val yOffset = innerComponent.yOffsetFromEditor(editor) ?: return
    val bounds = Rectangle()
    val oldClip = g.clipBounds
    g.clip = Rectangle(oldClip.x, yOffset, oldClip.width, innerComponent.height).intersection(oldClip)
    for (collapsingComponent in innerComponent.components) {
      val mainComponent = (collapsingComponent as CollapsingComponent).mainComponent

      collapsingComponent.paintGutter(editor, yOffset, g)

      mainComponent.gutterPainter?.let { painter ->
        mainComponent.yOffsetFromEditor(editor)?.let { yOffset ->
          bounds.setBounds(r.x, yOffset, r.width, mainComponent.height)
          painter.paintGutter(editor, g, bounds)
        }
      }
    }
    g.clip = oldClip
  }

  private fun rankCompatibility(outputDataKeys: List<NotebookOutputDataKey>): Int =
    getComponentsWithFactories().zip(outputDataKeys).sumBy { (pair, outputDataKey) ->
      val (component, factory) = pair
      when (factory.matchWithTypes(component, outputDataKey)) {
        NotebookOutputComponentFactory.Match.NONE -> 0
        NotebookOutputComponentFactory.Match.COMPATIBLE -> 1
        NotebookOutputComponentFactory.Match.SAME -> 1000
      }
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
            innerComponent.remove(idx)
            oldComponent.disposeComponent()
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
            oldComponent.parent.castSafelyTo<CollapsingComponent>()?.updateStubIfCollapsed()
            true
          }
          NotebookOutputComponentFactory.Match.SAME -> true
        } || isFilled
    }

    for (ignored in oldComponentsWithFactories) {
      val idx = innerComponent.componentCount - 1
      val old = innerComponent.getComponent(idx).let { if (it is CollapsingComponent) it.mainComponent else it }
      innerComponent.remove(idx)
      //Must be JComponent because of ``createComponent`` signature
      (old as JComponent).disposeComponent()
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

  private fun addIntoInnerComponent(newComponent: NotebookOutputComponentFactory.CreatedComponent<*>, pos: Int = -1) {
    newComponent.apply {
      disposable?.let {
        component.addDisposable(it)
      }
    }
    val collapsingComponent = CollapsingComponent(
      editor,
      newComponent.component,
      newComponent.limitHeight,
      newComponent.collapsedTextSupplier,
    )

    innerComponent.add(
      collapsingComponent,
      InnerComponent.Constraint(newComponent.widthStretching, newComponent.limitHeight),
      pos,
    )
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
    ApplicationManager.getApplication().messageBus.syncPublisher(OUTPUT_LISTENER).beforeOutputCreated(editor, lines.last)
    val result = factory.createComponent(editor, outputDataKey)?.also {
      it.component.outputComponentFactory = factory
      it.component.gutterPainter = it.gutterPainter
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(OUTPUT_LISTENER).outputCreated(editor, lines.last)
    return result
  }

  class Factory : NotebookCellInlayController.Factory {
    override fun compute(
      editor: EditorImpl,
      currentControllers: Collection<NotebookCellInlayController>,
      intervalIterator: ListIterator<NotebookCellLines.Interval>,
    ): NotebookCellInlayController? {
      val interval = intervalIterator.next()
      if (interval.type != NotebookCellLines.CellType.CODE) return null

      val outputDataKeys =
        NotebookOutputDataKeyExtractor.EP_NAME.extensionList.asSequence()
          .mapNotNull { it.extract(editor, interval) }
          .firstOrNull()
          ?.takeIf { it.isNotEmpty() }
        ?: return null

      val controller =
        currentControllers
          .filterIsInstance<NotebookOutputInlayController>()
          .maxByOrNull { it.rankCompatibility(outputDataKeys) }
        ?: NotebookOutputInlayController(this, editor, interval.lines)
      return controller.takeIf { it.updateData(outputDataKeys) }
    }
  }
}

@Service
private class NotebookOutputComponentFactoryGetter : Disposable, Runnable {
  var list: List<NotebookOutputComponentFactory<*, *>> = emptyList()
    get() =
      field.ifEmpty {
        field = makeValidatedList()
        field
      }
    private set

  init {
    NotebookOutputComponentFactory.EP_NAME.addChangeListener(this, this)
  }

  override fun run() {
    list = emptyList()
  }

  override fun dispose(): Unit = Unit

  private fun makeValidatedList(): MutableList<NotebookOutputComponentFactory<*, *>> {
    val newList = mutableListOf<NotebookOutputComponentFactory<*, *>>()
    for (extension in NotebookOutputComponentFactory.EP_NAME.extensionList) {
      val collidingExtension = newList
        .firstOrNull {
          (
            it.componentClass.isAssignableFrom(extension.componentClass) ||
            extension.componentClass.isAssignableFrom(it.componentClass)
          ) &&
          (
            it.outputDataKeyClass.isAssignableFrom(extension.outputDataKeyClass) ||
            extension.outputDataKeyClass.isAssignableFrom(it.outputDataKeyClass)
          )
        }
      if (collidingExtension != null) {
        LOG.error("Can't register $extension: it clashes with $collidingExtension by using similar component and data key classes.")
      }
      else {
        newList += extension
      }
    }
    return newList
  }

  companion object {
    private val LOG = logger<NotebookOutputComponentFactoryGetter>()

    @JvmStatic
    val instance: NotebookOutputComponentFactoryGetter get() = service()
  }
}

private fun <A, B> Iterator<A>.zip(other: Iterator<B>): Iterator<Pair<A, B>> = object : Iterator<Pair<A, B>> {
  override fun hasNext(): Boolean = this@zip.hasNext() && other.hasNext()
  override fun next(): Pair<A, B> = this@zip.next() to other.next()
}

private var JComponent.outputComponentFactory: NotebookOutputComponentFactory<*, *>? by SwingClientProperty("outputComponentFactory")
