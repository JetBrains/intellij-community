// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigationUtil")

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
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.Companion.getInstanceEx
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager.Companion.getInstance
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import com.intellij.util.Processor
import com.intellij.util.TextWithIcon
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.ActionEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.*

private val GO_TO_EP_NAME = ExtensionPointName<GotoRelatedProvider>("com.intellij.gotoRelatedProvider")

fun getPsiElementPopup(elements: Array<PsiElement>, title: @NlsContexts.PopupTitle String?): JBPopup {
  return PsiTargetNavigator(elements).createPopup(elements[0].project, title)
}

fun getPsiElementPopup(elements: Supplier<Collection<PsiElement>>,
                       renderer: PsiTargetPresentationRenderer<PsiElement>,
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
  return getPsiElementPopup(elements, renderer, title, PsiElementProcessor { element ->
    EditSourceUtil.navigateToPsiElement(element!!)
  })
}

fun <T : PsiElement?> getPsiElementPopup(elements: Array<T?>,
                                         renderer: PsiElementListCellRenderer<in T?>,
                                         title: @NlsContexts.PopupTitle String?,
                                         processor: PsiElementProcessor<in T?>): JBPopup {
  return getPsiElementPopup(elements, renderer, title, processor, null)
}

fun <T : PsiElement?> getPsiElementPopup(elements: Array<T>,
                                         renderer: PsiElementListCellRenderer<in T>,
                                         title: @NlsContexts.PopupTitle String?,
                                         processor: PsiElementProcessor<in T>,
                                         initialSelection: T?): JBPopup {
  assert(elements.size > 0) { "Attempted to show a navigation popup with zero elements" }
  val builder = JBPopupFactory.getInstance()
    .createPopupChooserBuilder(java.util.List.of(*elements))
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
  hidePopupIfDumbModeStarts(popup, elements[0]!!.project)
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
fun activateFileWithPsiElement(elt: PsiElement, searchForOpen: Boolean = true): Boolean {
  return openFileWithPsiElement(elt, searchForOpen, true)
}

fun openFileWithPsiElement(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean {
  val openAsNative = shouldOpenAsNative(element)
  if (searchForOpen) {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
  }
  else {
    element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, true)
  }
  val resultRef = Ref<Boolean>()
  // all navigation inside should be treated as a single operation, so that 'Back' action undoes it in one go
  CommandProcessor.getInstance().executeCommand(element.project, {
    if (openAsNative || !activatePsiElementIfOpen(element, searchForOpen, requestFocus)) {
      val navigationItem = element as NavigationItem
      if (navigationItem.canNavigate()) {
        navigationItem.navigate(requestFocus)
        resultRef.set(java.lang.Boolean.TRUE)
      }
      else {
        resultRef.set(java.lang.Boolean.FALSE)
      }
    }
  }, "", null)
  if (!resultRef.isNull) return resultRef.get()
  element.putUserData(FileEditorManager.USE_CURRENT_WINDOW, null)
  return false
}

private fun shouldOpenAsNative(element: PsiElement): Boolean {
  if (element !is PsiFile) {
    return false
  }
  val virtualFile: VirtualFile = element.virtualFile ?: return false
  return shouldOpenAsNative(virtualFile)
}

@ApiStatus.Internal
fun shouldOpenAsNative(virtualFile: VirtualFile): Boolean {
  val type = virtualFile.fileType
  return type is INativeFileType || type is UnknownFileType
}

private fun activatePsiElementIfOpen(element: PsiElement, searchForOpen: Boolean, requestFocus: Boolean): Boolean {
  var element = element
  if (!element.isValid) {
    return false
  }
  element = element.navigationElement
  val file = element.containingFile
  if (file == null || !file.isValid) {
    return false
  }
  val vFile = file.virtualFile ?: return false
  val project = element.project
  return activateFileIfOpen(project, vFile, element.textRange, searchForOpen, requestFocus)
}

@ApiStatus.Internal
fun activateFileIfOpen(
  project: Project,
  vFile: VirtualFile,
  range: TextRange?,
  searchForOpen: Boolean,
  requestFocus: Boolean
): Boolean {
  EDT.assertIsEdt()
  if (!getInstance(project).hasBeenOpen(vFile)) {
    return false
  }
  val fileEditorManager = getInstanceEx(project)
  val wasAlreadyOpen = fileEditorManager.isFileOpen(vFile)
  val openOptions = FileEditorOpenOptions().withRequestFocus(requestFocus).withReuseOpen(searchForOpen)
  if (!wasAlreadyOpen) {
    fileEditorManager.openFile(vFile, null, openOptions)
  }
  if (range == null) {
    return false
  }
  for (editor in fileEditorManager.getEditors(vFile)) {
    if (editor is TextEditor) {
      val text = editor.editor
      val offset = text.caretModel.offset
      if (range.containsOffset(offset)) {
        if (wasAlreadyOpen) {
          // select the file
          fileEditorManager.openFile(vFile, null, openOptions)
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
fun patchAttributesColor(attributes: TextAttributes, range: TextRange, editor: Editor): TextAttributes {
  if (attributes.foregroundColor == null && attributes.effectColor == null) {
    return attributes
  }
  val model = DocumentMarkupModel.forDocument(editor.document, editor.project, false) ?: return attributes
  if (!(model as MarkupModelEx).processRangeHighlightersOverlappingWith(range.startOffset,
                                                                        range.endOffset) { highlighter: RangeHighlighterEx ->
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
  return getRelatedItemsPopup(items, title, false)
}

/**
 * Returns navigation popup that shows list of related items from `items` list
 * @param showContainingModules Whether the popup should show additional information that aligned at the right side of the dialog.<br></br>
 * It's usually a module name or library name of corresponding navigation item.<br></br>
 * `false` by default
 */
fun getRelatedItemsPopup(items: List<GotoRelatedItem>, title: @NlsContexts.PopupTitle String?, showContainingModules: Boolean): JBPopup {
  val elements: MutableList<Any?> = ArrayList(items.size)
  //todo[nik] move presentation logic to GotoRelatedItem class
  val itemsMap: MutableMap<PsiElement?, GotoRelatedItem?> = HashMap()
  for (item in items) {
    if (item.element != null) {
      if (itemsMap.putIfAbsent(item.element, item) == null) {
        elements.add(item.element)
      }
    }
    else {
      elements.add(item)
    }
  }
  return getPsiElementPopup(elements, itemsMap, title, showContainingModules
  ) { element: Any ->
    if (element is PsiElement) {
      itemsMap[element]!!.navigate()
    }
    else {
      (element as GotoRelatedItem).navigate()
    }
    true
  }
}

private fun getPsiElementPopup(elements: List<Any?>,
                               itemsMap: Map<PsiElement?, GotoRelatedItem?>,
                               title: @NlsContexts.PopupTitle String?,
                               showContainingModules: Boolean,
                               processor: Processor<Any>): JBPopup {
  val hasMnemonic = Ref.create(false)
  val renderer: DefaultPsiElementCellRenderer = object : DefaultPsiElementCellRenderer() {
    override fun getElementText(element: PsiElement): String {
      val customName = itemsMap[element]!!.customName
      return customName ?: super.getElementText(element)
    }

    override fun getIcon(element: PsiElement): Icon {
      val customIcon = itemsMap[element]!!.customIcon
      return customIcon ?: super.getIcon(element)
    }

    override fun getContainerText(element: PsiElement, name: String): String? {
      val customContainerName = itemsMap[element]!!.customContainerName
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
      renderer.setIcon(item.customIcon)
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
      if (!hasMnemonic.get() || psiComponent !is JPanel) {
        return psiComponent
      }
      val panelWithMnemonic = JPanel(BorderLayout())
      val mnemonic = getMnemonic(value, itemsMap)
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
  val popup: ListPopupImpl = object : ListPopupImpl(object : BaseListPopupStep<Any?>(title, elements) {
    val separators: MutableMap<Any?, ListSeparator> = HashMap()

    init {
      var current: String? = null
      var hasTitle = false
      for (element in elements) {
        val item = if (element is GotoRelatedItem) element else itemsMap[element]
        if (item != null && current != item.group) {
          current = item.group
          separators[element] = ListSeparator(
            if (hasTitle && Strings.isEmpty(current)) CodeInsightBundle.message("goto.related.items.separator.other") else current)
          if (!hasTitle && !Strings.isEmpty(current)) {
            hasTitle = true
          }
        }
      }
      if (!hasTitle) {
        separators.remove(elements[0])
      }
    }

    override fun isSpeedSearchEnabled(): Boolean {
      return true
    }

    override fun getIndexedString(value: Any): String {
      if (value is GotoRelatedItem) {
        return value.customName!!
      }
      val element = value as PsiElement
      return if (!element.isValid) "INVALID" else renderer.getElementText(element) + " " + renderer.getContainerText(element, null)
    }

    override fun onChosen(selectedValue: Any, finalChoice: Boolean): PopupStep<*>? {
      processor.process(selectedValue)
      return super.onChosen(selectedValue, finalChoice)
    }

    override fun getSeparatorAbove(value: Any): ListSeparator? {
      return separators[value]
    }
  }) {}
  popup.list.setCellRenderer(object : PopupListElementRenderer<Any?>(popup) {
    override fun getListCellRendererComponent(list: JList<*>?,
                                              value: Any,
                                              index: Int,
                                              isSelected: Boolean,
                                              cellHasFocus: Boolean): Component {
      val component = renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      if (myDescriptor.hasSeparatorAboveOf(value)) {
        val panel = JPanel(BorderLayout())
        panel.add(component, BorderLayout.CENTER)
        val sep: SeparatorWithText = object : SeparatorWithText() {
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
  popup.setMinimumSize(Dimension(200, -1))
  for (item in elements) {
    val mnemonic = getMnemonic(item, itemsMap)
    if (mnemonic != -1) {
      val action = createNumberAction(mnemonic, popup, itemsMap, processor)
      popup.registerAction(mnemonic.toString() + "Action", KeyStroke.getKeyStroke(mnemonic.toString()), action)
      popup.registerAction(mnemonic.toString() + "Action", KeyStroke.getKeyStroke("NUMPAD$mnemonic"), action)
      hasMnemonic.set(true)
    }
  }
  return popup
}

private fun createNumberAction(mnemonic: Int,
                               listPopup: ListPopupImpl,
                               itemsMap: Map<PsiElement?, GotoRelatedItem?>,
                               processor: Processor<Any>): Action {
  return object : AbstractAction() {
    override fun actionPerformed(e: ActionEvent) {
      for (item in listPopup.listStep.values) {
        if (getMnemonic(item, itemsMap) == mnemonic) {
          listPopup.setFinalRunnable { processor.process(item) }
          listPopup.closeOk(null)
        }
      }
    }
  }
}

private fun getMnemonic(item: Any?, itemsMap: Map<PsiElement?, GotoRelatedItem?>): Int {
  return (if (item is GotoRelatedItem) item else itemsMap[item as PsiElement?])!!.mnemonic
}

fun collectRelatedItems(contextElement: PsiElement, dataContext: DataContext?): List<GotoRelatedItem> {
  val items: MutableSet<GotoRelatedItem> = LinkedHashSet()
  GO_TO_EP_NAME.forEachExtensionSafe { provider: GotoRelatedProvider ->
    items.addAll(provider.getItems(contextElement))
    if (dataContext != null) {
      items.addAll(provider.getItems(dataContext))
    }
  }
  val result = items.toTypedArray<GotoRelatedItem>()
  Arrays.sort(result) { i1: GotoRelatedItem, i2: GotoRelatedItem ->
    val o1 = i1.group
    val o2 = i2.group
    if (Strings.isEmpty(o1)) 1 else if (Strings.isEmpty(o2)) -1 else o1.compareTo(o2)
  }
  return Arrays.asList(*result)
}
