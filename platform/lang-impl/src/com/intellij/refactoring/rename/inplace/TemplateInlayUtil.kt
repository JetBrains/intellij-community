// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.FusInputEvent
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameInplacePopupUsagesCollector
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.rename.impl.TextOptions
import com.intellij.refactoring.rename.impl.isEmpty
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.ui.layout.*
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

@ApiStatus.Experimental
object TemplateInlayUtil {

  @JvmStatic
  fun createNavigatableButton(templateState: TemplateState,
                              inEditorOffset: Int,
                              presentation: SelectableInlayPresentation,
                              templateElement: VirtualTemplateElement): Inlay<PresentationRenderer>? {
    VirtualTemplateElement.installOnTemplate(templateState, templateElement)
    return createNavigatableButton(templateState, inEditorOffset, presentation)
  }

  @JvmStatic
  fun createNavigatableButton(templateState: TemplateState,
                              inEditorOffset: Int,
                              presentation: InlayPresentation): Inlay<PresentationRenderer>? {
    val renderer = PresentationRenderer(presentation)
    val inlay = templateState.editor.inlayModel.addInlineElement(inEditorOffset, true, renderer) ?: return null
    presentation.addListener(object : PresentationListener {
      override fun contentChanged(area: Rectangle) {
        inlay.repaint()
      }

      override fun sizeChanged(previous: Dimension, current: Dimension) {
        inlay.repaint()
      }
    })
    Disposer.register(templateState, inlay)
    return inlay
  }

  open class SelectableTemplateElement(val presentation: SelectableInlayPresentation) : VirtualTemplateElement {

    override fun onSelect(templateState: TemplateState) {
      presentation.isSelected = true
      templateState.focusCurrentHighlighter(false)
    }
  }

  @Deprecated("Use overload with JPanel",
              ReplaceWith("createNavigatableButtonWithPopup(templateState, inEditorOffset, presentation, panel as JPanel, templateElement, logStatisticsOnHide)"))
  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: DialogPanel,
                                       templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
                                       logStatisticsOnHide: () -> Unit = {}): Inlay<PresentationRenderer>? {
    return createNavigatableButtonWithPopup(templateState, inEditorOffset, presentation, panel as JPanel, templateElement, logStatisticsOnHide)
  }
  
  @JvmOverloads
  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: JPanel,
                                       templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
                                       logStatisticsOnHide: () -> Unit = {}): Inlay<PresentationRenderer>? {
    val editor = templateState.editor
    val inlay = createNavigatableButton(templateState, inEditorOffset, presentation, templateElement) ?: return null
    fun showPopup() {
      try {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, inlay.visualPosition)
        panel.border = JBEmptyBorder(JBInsets.create(Insets(8, 12, 4, 12)))
        val focusedComponent = if (panel is DialogPanel) panel.preferredFocusedComponent else panel
        val popup = JBPopupFactory.getInstance()
          .createComponentPopupBuilder(panel, focusedComponent)
          .setRequestFocus(true)
          .addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
              presentation.isSelected = false
              templateState.focusCurrentHighlighter(true)
              logStatisticsOnHide.invoke()
            }
          })
          .createPopup()
        val customEnterAction = object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            popup.cancel()
            CommandProcessor.getInstance().executeCommand(templateState.project, {templateState.nextTab()}, null, null)
          }
        }
        customEnterAction.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_ENTER), panel)
        Disposer.register(popup) {
          customEnterAction.unregisterCustomShortcutSet(panel)
        }
        popup.showInBestPositionFor(editor)
      }
      finally {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null)
      }
    }
    presentation.addSelectionListener { isSelected -> if (isSelected) showPopup() }
    return inlay
  }

  @JvmOverloads
  @JvmStatic
  fun createSettingsPresentation(editor: EditorImpl, onClick: (MouseEvent) -> Unit = {}): SelectableInlayPresentation {
    val factory = PresentationFactory(editor)
    fun button(background: Color?): InlayPresentation {
      val button = factory.container(
        presentation = factory.icon(AllIcons.Actions.InlayGear),
        padding = InlayPresentationFactory.Padding(4, 4, 4, 4),
        roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6),
        background = background
      )
      return factory.container(button, padding = InlayPresentationFactory.Padding(3, 6, 0, 0))
    }

    val colorsScheme = editor.colorsScheme
    var hovered = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_HOVERED))
    val shortcut = KeymapUtil.getPrimaryShortcut("SelectVirtualTemplateElement")
    if (shortcut != null) {
      val tooltip = RefactoringBundle.message("refactoring.extract.method.inplace.options.tooltip", KeymapUtil.getShortcutText(shortcut))
      hovered = factory.withTooltip(tooltip, hovered)
    }
    return object : SelectableInlayButton(editor,
                                          default = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_DEFAULT)),
                                          active = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_FOCUSED)),
                                          hovered) {
      override fun mouseClicked(event: MouseEvent, translated: Point) {
        super.mouseClicked(event, translated)
        onClick(event)
      }
    }
  }

  @JvmStatic
  fun createRenameSettingsInlay(templateState: TemplateState,
                                offset: Int,
                                elementToRename: PsiNamedElement,
                                restart: Runnable): Inlay<PresentationRenderer>? {
    val processor = RenamePsiElementProcessor.forElement(elementToRename)
    val initOptions = TextOptions(
      commentStringOccurrences = processor.isToSearchInComments(elementToRename),
      textOccurrences = if (TextOccurrencesUtil.isSearchTextOccurrencesEnabled(elementToRename)) {
        processor.isToSearchForTextOccurrences(elementToRename)
      }
      else {
        null
      }
    )
    return createRenameSettingsInlay(templateState, offset, initOptions) { (commentStringOccurrences, textOccurrences) ->
      if (commentStringOccurrences != null) {
        processor.setToSearchInComments(elementToRename, commentStringOccurrences)
      }
      if (textOccurrences != null) {
        processor.setToSearchForTextOccurrences(elementToRename, textOccurrences)
      }
      restart.run()
    }
  }

  internal fun createRenameSettingsInlay(
    templateState: TemplateState,
    offset: Int,
    initOptions: TextOptions,
    optionsListener: (TextOptions) -> Unit,
  ): Inlay<PresentationRenderer>? {
    if (initOptions.isEmpty) {
      return null
    }

    val editor = templateState.editor as EditorImpl
    val factory = PresentationFactory(editor)
    val colorsScheme = editor.colorsScheme
    fun button(presentation: InlayPresentation, second: Boolean) = factory.container(
      presentation = presentation,
      padding = InlayPresentationFactory.Padding(if (second) 0 else 4, 4, 4, 4)
    )

    var tooltip = LangBundle.message("inlay.rename.tooltip.header")
    val commentStringPresentation = initOptions.commentStringOccurrences?.let { commentStringOccurrences ->
      tooltip += LangBundle.message("inlay.rename.tooltip.comments.strings")
      BiStatePresentation(
        first = { factory.icon(AllIcons.Actions.InlayRenameInCommentsActive) },
        second = { factory.icon(AllIcons.Actions.InlayRenameInComments) },
        initiallyFirstEnabled = commentStringOccurrences,
      )
    }
    val textPresentation = initOptions.textOccurrences?.let { textOccurrences ->
      tooltip += LangBundle.message("inlay.rename.tooltip.non.code")
      BiStatePresentation(
        first = { factory.icon(AllIcons.Actions.InlayRenameInNoCodeFilesActive) },
        second = { factory.icon(AllIcons.Actions.InlayRenameInNoCodeFiles) },
        initiallyFirstEnabled = textOccurrences,
      )
    }
    val buttonsPresentation = if (commentStringPresentation != null && textPresentation != null) {
      factory.seq(
        button(commentStringPresentation, false),
        button(textPresentation, true)
      )
    }
    else {
      val presentation = commentStringPresentation
                         ?: textPresentation
                         ?: error("at least one option should be not null")
      button(presentation, false)
    }

    val shortcut = KeymapUtil.getPrimaryShortcut("SelectVirtualTemplateElement")
    if (shortcut != null) {
      tooltip += LangBundle.message("inlay.rename.tooltip.tab.advertisement", KeymapUtil.getShortcutText(shortcut))
    }

    fun withBackground(bgKey: ColorKey) = factory.container(
      presentation = buttonsPresentation,
      roundedCorners = InlayPresentationFactory.RoundedCorners(3, 3),
      background = colorsScheme.getColor(bgKey),
      padding = InlayPresentationFactory.Padding(4, 0, 0, 0),
    )

    val presentation = object : SelectableInlayButton(editor,
                                                      withBackground(INLINE_REFACTORING_SETTINGS_DEFAULT),
                                                      withBackground(INLINE_REFACTORING_SETTINGS_FOCUSED),
                                                      factory.withTooltip(tooltip, withBackground(INLINE_REFACTORING_SETTINGS_HOVERED))) {
      override fun mouseClicked(event: MouseEvent, translated: Point) {
        super.mouseClicked(event, translated)
        logStatisticsOnShow(editor, event)
      }
    }

    val templateElement = object : SelectableTemplateElement(presentation) {
      override fun onSelect(templateState: TemplateState) {
        super.onSelect(templateState)
        logStatisticsOnShow(editor)
      }
    }

    var currentOptions: TextOptions = initOptions
    val panel = renamePanel(editor, initOptions) { newOptions ->
      currentOptions = newOptions
      newOptions.commentStringOccurrences?.let {
        commentStringPresentation?.state = BiStatePresentation.State(it)
      }
      newOptions.textOccurrences?.let {
        textPresentation?.state = BiStatePresentation.State(it)
      }
      optionsListener.invoke(newOptions)
    }
    return createNavigatableButtonWithPopup(templateState, offset, presentation, panel as JPanel, templateElement) {
      logStatisticsOnHide(editor, initOptions, currentOptions)
    }
  }

  private fun logStatisticsOnShow(editor: Editor, mouseEvent: MouseEvent? = null) {
    val showEvent = mouseEvent
                    ?: KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, KeyEvent.VK_TAB.toChar())
    RenameInplacePopupUsagesCollector.show.log(editor.project,
                                               EventFields.InputEvent.with(FusInputEvent(showEvent, javaClass.simpleName)))
  }

  private fun logStatisticsOnHide(editor: EditorImpl, initOptions: TextOptions, newOptions: TextOptions) {
    RenameInplacePopupUsagesCollector.hide.log(
      editor.project,
      RenameInplacePopupUsagesCollector.searchInCommentsOnHide.with(newOptions.commentStringOccurrences ?: false),
      RenameInplacePopupUsagesCollector.searchInTextOccurrencesOnHide.with(newOptions.textOccurrences ?: false)
    )
    RenameInplacePopupUsagesCollector.settingsChanged.log(
      editor.project,
      RenameInplacePopupUsagesCollector.changedOnHide.with(initOptions != newOptions)
    )
  }

  private fun renamePanel(
    editor: Editor,
    initOptions: TextOptions,
    optionsListener: (TextOptions) -> Unit,
  ): DialogPanel {
    val renameAction = ActionManager.getInstance().getAction(IdeActions.ACTION_RENAME)
    var (commentsStringsOccurrences, textOccurrences) = initOptions // model
    val panel = panel {
      row(LangBundle.message("inlay.rename.also.rename.options.title")) {
        commentsStringsOccurrences?.let {
          row {
            cell {
              checkBox(
                text = RefactoringBundle.message("comments.and.strings"),
                isSelected = it,
                actionListener = { _, cb ->
                  commentsStringsOccurrences = cb.isSelected
                  optionsListener(TextOptions(commentStringOccurrences = commentsStringsOccurrences, textOccurrences = textOccurrences))
                }
              ).focused()
              component(JLabel(AllIcons.Actions.InlayRenameInComments))
            }
          }
        }
        textOccurrences?.let {
          row {
            cell {
              val cb = checkBox(
                text = RefactoringBundle.message("text.occurrences"),
                isSelected = it,
                actionListener = { _, cb ->
                  textOccurrences = cb.isSelected
                  optionsListener(TextOptions(commentStringOccurrences = commentsStringsOccurrences, textOccurrences = textOccurrences))
                }
              )
              if (commentsStringsOccurrences == null) {
                cb.focused()
              }
              component(JLabel(AllIcons.Actions.InlayRenameInNoCodeFiles))
            }
          }
        }
      }
      row {
        cell {
          link(LangBundle.message("inlay.rename.link.label.more.options"), null) {
            doRename(editor, renameAction, null)
          }.component.isFocusable = true
          comment(KeymapUtil.getFirstKeyboardShortcutText(renameAction))
        }
      }
    }
    DumbAwareAction.create {
      doRename(editor, renameAction, it)
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_RENAME), panel)
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    return panel
  }

  private fun doRename(editor: Editor, renameAction: AnAction, anActionEvent: AnActionEvent?) {
    RenameInplacePopupUsagesCollector.openRenameDialog.log(editor.project, RenameInplacePopupUsagesCollector.linkUsed.with(anActionEvent == null))
    val event = AnActionEvent(null,
                              DataManager.getInstance().getDataContext(editor.component),
                              anActionEvent?.place ?: ActionPlaces.UNKNOWN, renameAction.templatePresentation.clone(),
                              ActionManager.getInstance(), 0)
    if (ActionUtil.lastUpdateAndCheckDumb(renameAction, event, true)) {
      ActionUtil.performActionDumbAwareWithCallbacks(renameAction, event)
    }
  }
}
