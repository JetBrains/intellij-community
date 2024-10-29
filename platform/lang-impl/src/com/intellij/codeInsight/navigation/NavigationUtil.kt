// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigationUtil")
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.navigation.impl.PsiTargetPresentationRenderer
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.ide.util.EditSourceUtil
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.navigation.GotoRelatedItem
import com.intellij.navigation.GotoRelatedProvider
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.CommandProcessorEx
import com.intellij.openapi.command.CommandToken
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.Processor
import com.intellij.util.SlowOperations
import com.intellij.util.TextWithIcon
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.*

private val GO_TO_EP_NAME = ExtensionPointName<GotoRelatedProvider>("com.intellij.gotoRelatedProvider")

fun getPsiElementPopup(elements: Array<PsiElement>, title: @NlsContexts.PopupTitle String?): JBPopup {
  return PsiTargetNavigator(elements).createPopup(project = elements[0].project, title = title)
}

fun getPsiElementPopup(elements: Supplier<Collection<PsiElement>>,
                       renderer: PsiTargetPresentationRenderer<in PsiElement>,
                       title: @NlsContexts.PopupTitle String?,
                       project: Project): JBPopup {
  return PsiTargetNavigator(elements)
    .presentationProvider(renderer)
    .createPopup(project, title)
}

@Deprecated("Use {@link #getPsiElementPopup(Supplier, PsiTargetPresentationRenderer, String, Project)}")
fun getPsiElementPopup(elements: Array<PsiElement>,
                       renderer: PsiElementListCellRenderer<in PsiElement>,
                       title: @NlsContexts.PopupTitle String?): JBPopup {
  return getPsiElementPopup(elements = elements, renderer = renderer, title = title, processor = PsiElementProcessor { element ->
    EditSourceUtil.navigateToPsiElement(element)
  })
}

fun <T : PsiElement> getPsiElementPopup(elements: Array<T>,
                                         renderer: PsiElementListCellRenderer<in T>,
                                         title: @NlsContexts.PopupTitle String?,
                                         processor: PsiElementProcessor<in T>): JBPopup {
  return getPsiElementPopup(elements = elements, renderer = renderer, title = title, processor = processor, initialSelection = null)
}

fun <T : PsiElement?> getPsiElementPopup(elements: Array<T>,
                                         renderer: PsiElementListCellRenderer<in T>,
                                         title: @NlsContexts.PopupTitle String?,
                                         processor: PsiElementProcessor<in T>,
                                         initialSelection: T?): JBPopup {
  assert(elements.isNotEmpty()) { "Attempted to show a navigation popup with zero elements" }
  val builder = JBPopupFactory.getInstance()
    .createPopupChooserBuilder(elements.asList())
    .setRenderer(renderer)
    .setFont(EditorUtil.getEditorFont())
    .withHintUpdateSupply()
  if (initialSelection != null) {
    builder.setSelectedValue(initialSelection, true)
  }
  if (title != null) {
    builder.setTitle(title)
  }
  renderer.installSpeedSearch(builder, true)
  val popup = builder.setItemsChosenCallback { selectedValues: Set<T> ->
    for (element in selectedValues) {
      if (element != null) {
        processor.execute(element)
      }
    }
  }.createPopup()
  if (builder is PopupChooserBuilder<*>) {
    val pane = (builder as PopupChooserBuilder<*>).scrollPane
    pane.border = null
    pane.viewportBorder = null
  }
  hidePopupIfDumbModeStarts(popup = popup, project = elements[0]!!.project)
  return popup
}

fun hidePopupIfDumbModeStarts(popup: JBPopup, project: Project) {
  if (!isDumb(project)) {
    project.messageBus.connect(popup).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      override fun enteredDumbMode() {
        popup.cancel()
      }
    })
  }
}

@JvmOverloads
fun activateFileWithPsiElement(element: PsiElement, searchForOpen: Boolean = true): Boolean {
  return openFileWithPsiElement(element = element, searchForOpen = searchForOpen, requestFocus = true)
}

fun openFileWithPsiElement(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean {
  val openAsNative = shouldOpenAsNative(element)
  if (searchForOpen) {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
  }
  else {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true)
  }

  var resultRef: Boolean? = null
  // all navigations inside should be treated as a single operation, so that 'Back' action undoes it in one go
  CommandProcessor.getInstance().executeCommand(element.project, {
    if (openAsNative || !activatePsiElementIfOpen(element, searchForOpen, requestFocus)) {
      val navigationItem = element as NavigationItem
      if (navigationItem.canNavigate()) {
        navigationItem.navigate(requestFocus)
        resultRef = true
      }
      else {
        resultRef = false
      }
    }
  }, "", null)
  resultRef?.let {
    return it
  }
  element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
  return false
}

internal suspend fun openFileWithPsiElementAsync(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean {
  val openAsNative = shouldOpenAsNative(element)
  if (searchForOpen) {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
  }
  else {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true)
  }

  val commandProcessor = (serviceAsync<CommandProcessor>() as CommandProcessorEx)
  return withContext(Dispatchers.EDT) {
    //readaction is not enough
    writeIntentReadAction {
      // all navigations inside should be treated as a single operation, so that 'Back' action undoes it in one go
      val commandHandle = WriteIntentReadAction.compute<CommandToken> {
        commandProcessor.startCommand(element.project, "", null, UndoConfirmationPolicy.DEFAULT) ?: return@compute null
      } ?: return@writeIntentReadAction false
      try {
        if (openAsNative || !activatePsiElementIfOpen(element, searchForOpen, requestFocus)) {
          val navigationItem = element as NavigationItem
          if (navigationItem.canNavigate()) {
            navigationItem.navigate(requestFocus)
            return@writeIntentReadAction true
          }
        }
      }
      finally {
        commandProcessor.finishCommand(commandHandle, null)
        element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
      }
      false
    }
  }
}

private fun shouldOpenAsNative(element: PsiElement): Boolean {
  if (element !is PsiFile) {
    return false
  }
  return shouldOpenAsNative(element.virtualFile ?: return false)
}

@ApiStatus.Internal
fun shouldOpenAsNative(virtualFile: VirtualFile): Boolean {
  val type = virtualFile.fileType
  return type is INativeFileType || type is UnknownFileType
}

private fun activatePsiElementIfOpen(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean =
  SlowOperations.knownIssue("IDEA-333908, EA-853156; IDEA-326668, EA-856275; IDEA-326669, EA-831824").use {
    doActivatePsiElementIfOpen(element, searchForOpen, requestFocus)
  }

private fun doActivatePsiElementIfOpen(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean {
  @Suppress("NAME_SHADOWING")
  val element = element.takeIf { it.isValid }?.navigationElement ?: return false
  val vFile = element.containingFile?.takeIf { it.isValid }?.virtualFile ?: return false
  val range = element.textRange ?: return false

  val fileEditorManager = FileEditorManagerEx.getInstanceEx(element.project)
  val wasAlreadyOpen = fileEditorManager.isFileOpen(vFile)
  val openOptions = FileEditorOpenOptions(requestFocus = requestFocus, reuseOpen = searchForOpen)
  if (!wasAlreadyOpen) {
    fileEditorManager.openFile(file = vFile, window = null, options = openOptions)
  }

  for (editor in fileEditorManager.getEditorList(vFile)) {
    if (editor is TextEditor) {
      val text = editor.editor
      val offset = text.caretModel.offset
      if (range.containsOffset(offset)) {
        if (wasAlreadyOpen) {
          // select the file
          fileEditorManager.openFile(file = vFile, window = null, options = openOptions)
        }
        return true
      }
    }
  }
  return false
}

/**
 * Patches attributes to be visible under debugger active line
 */
@Suppress("UseJBColor")
fun patchAttributesColor(attributes: TextAttributes, range: TextRange, editor: Editor): TextAttributes {
  if (attributes.foregroundColor == null && attributes.effectColor == null) {
    return attributes
  }

  val model = DocumentMarkupModel.forDocument(editor.document, editor.project, false) ?: return attributes
  if (!(model as MarkupModelEx).processRangeHighlightersOverlappingWith(range.startOffset, range.endOffset) { highlighter ->
      if (highlighter.isValid && highlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE) {
        val textAttributes = highlighter.getTextAttributes(editor.colorsScheme)
        if (textAttributes != null) {
          val color = textAttributes.backgroundColor
          return@processRangeHighlightersOverlappingWith !(color != null && color.blue > 128 && color.red < 128 && color.green < 128)
        }
      }
      true
    }) {
    val clone = attributes.clone()
    clone.foregroundColor = Color.orange
    clone.effectColor = Color.orange
    return clone
  }
  return attributes
}

fun getRelatedItemsPopup(items: List<GotoRelatedItem>, title: @NlsContexts.PopupTitle String?): JBPopup {
  return getRelatedItemsPopup(items = items, title = title, showContainingModules = false)
}

/**
 * Returns navigation popup that shows list of related items from `items` list
 * @param showContainingModules Whether the popup should show additional information that aligned on the right side of the dialog.<br></br>
 * It's usually a module name or library name of corresponding navigation item.<br></br>
 * `false` by default
 */
fun getRelatedItemsPopup(items: List<GotoRelatedItem>, title: @NlsContexts.PopupTitle String?, showContainingModules: Boolean): JBPopup {
  val elements = ArrayList<Any?>(items.size)
  //todo move presentation logic to GotoRelatedItem class
  val itemMap = HashMap<PsiElement, GotoRelatedItem>()
  for (item in items) {
    val element = item.element
    if (element == null) {
      elements.add(item)
    }
    else if (itemMap.putIfAbsent(element, item) == null) {
      elements.add(element)
    }
  }
  return getPsiElementPopup(elements = elements,
                            itemMap = itemMap,
                            title = title,
                            showContainingModules = showContainingModules) { element ->
    if (element is PsiElement) {
      itemMap.get(element)!!.navigate()
    }
    else {
      (element as GotoRelatedItem).navigate()
    }
    true
  }
}

private fun getPsiElementPopup(elements: List<Any?>,
                               itemMap: Map<PsiElement, GotoRelatedItem>,
                               title: @NlsContexts.PopupTitle String?,
                               showContainingModules: Boolean,
                               processor: Processor<Any>): JBPopup {
  var hasMnemonic = false
  val renderer: DefaultPsiElementCellRenderer = object : DefaultPsiElementCellRenderer() {
    override fun getElementText(element: PsiElement): String {
      return itemMap.get(element)!!.customName ?: super.getElementText(element)
    }

    override fun getIcon(element: PsiElement): Icon {
      return itemMap.get(element)!!.customIcon ?: super.getIcon(element)
    }

    override fun getContainerText(element: PsiElement, name: String): String? {
      val customContainerName = itemMap.get(element)!!.customContainerName
      if (customContainerName != null) {
        return customContainerName
      }

      val file = element.containingFile
      return if (file != null && getElementText(element) != file.name) "(" + file.name + ")" else null
    }

    override fun getItemLocation(value: Any): TextWithIcon? {
      return if (showContainingModules) super.getItemLocation(value) else null
    }

    override fun customizeNonPsiElementLeftRenderer(renderer: ColoredListCellRenderer<*>,
                                                    list: JList<*>,
                                                    value: Any,
                                                    index: Int,
                                                    selected: Boolean,
                                                    hasFocus: Boolean): Boolean {
      val item = value as GotoRelatedItem
      val color = list.foreground
      val nameAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)
      val name = item.customName ?: return false
      renderer.append(name, nameAttributes)
      renderer.icon = item.customIcon
      val containerName = item.customContainerName
      if (containerName != null) {
        renderer.append(" $containerName", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      return true
    }

    override fun getListCellRendererComponent(list: JList<*>?,
                                              value: Any,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val psiComponent = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (!hasMnemonic || psiComponent !is JPanel) {
        return psiComponent
      }

      val panelWithMnemonic = JPanel(BorderLayout())
      val mnemonic = getMnemonic(value, itemMap)
      val label = JLabel("")
      if (mnemonic != -1) {
        label.text = "$mnemonic."
        label.displayedMnemonicIndex = 0
      }
      label.preferredSize = JLabel("8.").preferredSize
      val leftRenderer = psiComponent.components[0] as JComponent
      psiComponent.remove(leftRenderer)
      panelWithMnemonic.border = BorderFactory.createEmptyBorder(0, 7, 0, 0)
      panelWithMnemonic.background = leftRenderer.background
      label.background = leftRenderer.background
      panelWithMnemonic.add(label, BorderLayout.WEST)
      panelWithMnemonic.add(leftRenderer, BorderLayout.CENTER)
      psiComponent.add(panelWithMnemonic)
      return psiComponent
    }
  }

  @Suppress("DEPRECATION")
  val popup = ListPopupImpl(object : BaseListPopupStep<Any>(title, elements) {
    private val separators = HashMap<Any?, ListSeparator>()

    init {
      var current: String? = null
      var hasTitle = false
      for (element in elements) {
        val item = if (element is GotoRelatedItem) element else itemMap[element]
        if (item != null && current != item.group) {
          current = item.group
          separators.put(element, ListSeparator(if (hasTitle && Strings.isEmpty(current)) CodeInsightBundle.message("goto.related.items.separator.other") else current))
          if (!hasTitle && !Strings.isEmpty(current)) {
            hasTitle = true
          }
        }
      }
      if (!hasTitle) {
        separators.remove(elements[0])
      }
    }

    override fun isSpeedSearchEnabled(): Boolean = true

    override fun getIndexedString(value: Any): String {
      if (value is GotoRelatedItem) {
        return value.customName!!
      }
      val element = value as PsiElement
      return if (element.isValid) "${renderer.getElementText(element)} ${renderer.getContainerText(element, null)}" else "INVALID"
    }

    override fun onChosen(selectedValue: Any, finalChoice: Boolean): PopupStep<*>? {
      processor.process(selectedValue)
      return super.onChosen(selectedValue, finalChoice)
    }

    override fun getSeparatorAbove(value: Any) = separators.get(value)
  })
  popup.list.setCellRenderer(object : PopupListElementRenderer<Any?>(popup) {
    override fun getListCellRendererComponent(list: JList<out Any?>?,
                                              value: Any?,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val component = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (myDescriptor.hasSeparatorAboveOf(value)) {
        @Suppress("MissingAccessibleContext")
        val panel = JPanel(BorderLayout())
        panel.add(component, BorderLayout.CENTER)
        @Suppress("DEPRECATION")
        val sep = object : com.intellij.ui.SeparatorWithText() {
          override fun paintComponent(g: Graphics) {
            g.color = JBColor(Color.WHITE, JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            g.fillRect(0, 0, width, height)
            super.paintComponent(g)
          }
        }
        sep.caption = myDescriptor.getCaptionAboveOf(value)
        panel.add(sep, BorderLayout.NORTH)
        return panel
      }
      return component
    }
  })
  popup.minimumSize = Dimension(200, -1)
  for (item in elements) {
    val mnemonic = getMnemonic(item, itemMap)
    if (mnemonic != -1) {
      val action = createNumberAction(mnemonic = mnemonic, listPopup = popup, itemMap = itemMap, processor = processor)
      popup.registerAction(mnemonic.toString() + "Action", KeyStroke.getKeyStroke(mnemonic.toString()), action)
      popup.registerAction(mnemonic.toString() + "Action", KeyStroke.getKeyStroke("NUMPAD$mnemonic"), action)
      hasMnemonic = true
    }
  }
  return popup
}

private fun createNumberAction(mnemonic: Int,
                               listPopup: ListPopupImpl,
                               itemMap: Map<PsiElement, GotoRelatedItem>,
                               processor: Processor<Any>): Action {
  return object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      for (item in listPopup.listStep.values) {
        if (getMnemonic(item, itemMap) == mnemonic) {
          listPopup.setFinalRunnable { processor.process(item) }
          listPopup.closeOk(null)
        }
      }
    }
  }
}

private fun getMnemonic(item: Any?, itemMap: Map<PsiElement, GotoRelatedItem?>): Int {
  return (if (item is GotoRelatedItem) item else itemMap.get(item))!!.mnemonic
}

/**
 * Query all [GotoRelatedProvider]s for their related items.
 */
fun collectRelatedItems(contextElement: PsiElement, dataContext: DataContext): List<GotoRelatedItem> {
  val items = LinkedHashSet<GotoRelatedItem>()
  GO_TO_EP_NAME.forEachExtensionSafe { provider ->
    items.addAll(provider.getItems(contextElement))
    items.addAll(provider.getItems(dataContext))
  }
  val result = items.toTypedArray<GotoRelatedItem>()
  Arrays.sort(result) { i1, i2 ->
    val o1 = i1.group
    val o2 = i2.group
    if (o1.isEmpty()) 1 else if (o2.isEmpty()) -1 else o1.compareTo(o2)
  }
  return result.asList()
}
