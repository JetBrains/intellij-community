// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
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
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

@ApiStatus.Experimental
object TemplateInlayUtil {

  @JvmStatic
  fun createNavigatableButton(editor: Editor,
                              offset: Int,
                              presentation: SelectableInlayPresentation,
                              templateElement: VirtualTemplateElement): Inlay<PresentationRenderer>? {
    return createInlayButton(editor, offset, presentation)?.also { inlay ->
      DumbAwareAction
        .create {
          val templateState = TemplateManagerImpl.getTemplateState(editor)
          if (templateState != null) {
            templateElement.onSelect(templateState)
          }
        }
        .registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("SelectVirtualTemplateElement"), editor.component, inlay)
    }
  }

  @JvmStatic
  fun createNavigatableButton(templateState: TemplateState,
                              inEditorOffset: Int,
                              presentation: InlayPresentation): Inlay<PresentationRenderer>? {
    return createInlayButton(templateState.editor, inEditorOffset, presentation)?.also { inlay ->
      Disposer.register(templateState, inlay)
    }
  }

  @JvmStatic
  fun createInlayButton(editor: Editor, offset: Int, presentation: InlayPresentation): Inlay<PresentationRenderer>? {
    val renderer = PresentationRenderer(presentation)
    val inlay = editor.inlayModel.addInlineElement(offset, true, renderer) ?: return null
    presentation.addListener(object : PresentationListener {
      override fun contentChanged(area: Rectangle) {
        inlay.repaint()
      }

      override fun sizeChanged(previous: Dimension, current: Dimension) {
        inlay.repaint()
      }
    })
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
  @ApiStatus.ScheduledForRemoval
  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: DialogPanel,
                                       templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
                                       logStatisticsOnHide: () -> Unit = {}): Inlay<PresentationRenderer>? {
    return createNavigatableButtonWithPopup(templateState, inEditorOffset, presentation, panel as JPanel, templateElement,
                                            isPopupAbove = false, logStatisticsOnHide)
  }
  
  @JvmOverloads
  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: JPanel,
                                       templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
                                       isPopupAbove: Boolean,
                                       logStatisticsOnHide: () -> Unit = {}): Inlay<PresentationRenderer>? {
    val inlay = createNavigatableButtonWithPopup(templateState.editor, inEditorOffset, presentation, panel, templateElement,
                                                 isPopupAbove = isPopupAbove) ?: return null
    Disposer.register(templateState, inlay)
    presentation.addSelectionListener { isSelected ->
      if (!isSelected) logStatisticsOnHide.invoke()
    }
    return inlay
  }

  @JvmOverloads
  @JvmStatic
  fun createNavigatableButtonWithPopup(
    editor: Editor,
    offset: Int,
    presentation: SelectableInlayPresentation,
    panel: JPanel,
    templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
    isPopupAbove: Boolean,
  ): Inlay<PresentationRenderer>? {
    val inlay = createNavigatableButton(editor, offset, presentation, templateElement) ?: return null
    fun showPopup() {
      panel.border = JBEmptyBorder(JBInsets.create(Insets(8, 12, 4, 12)))
      val focusedComponent = if (panel is DialogPanel) panel.preferredFocusedComponent else panel
      val popup = JBPopupFactory.getInstance()
        .createComponentPopupBuilder(panel, focusedComponent)
        .setRequestFocus(true)
        .addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            presentation.isSelected = false
          }
        })
        .createPopup()

      Disposer.register(inlay, popup)

      DumbAwareAction
        .create {
          popup.cancel()
          val templateState = TemplateManagerImpl.getTemplateState(editor)
          if (templateState != null) {
            CommandProcessor.getInstance().executeCommand(templateState.project, templateState::nextTab, null, null)
          }
        }
        .registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_ENTER), panel, popup)
      try {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, inlay.visualPosition)
        if (isPopupAbove) {
          val popupFactory = JBPopupFactory.getInstance()
          val target = popupFactory.guessBestPopupLocation(editor)
          val screenPoint = target.getScreenPoint()
          popup.show(RelativePoint(Point(screenPoint.x, screenPoint.y - editor.lineHeight - panel.preferredHeight)))
        }
        else {
          popup.showInBestPositionFor(editor)
        }
      }
      finally {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null)
      }
    }
    presentation.addSelectionListener { isSelected ->
      if (isSelected) showPopup()
      TemplateManagerImpl.getTemplateState(editor)?.focusCurrentHighlighter(!isSelected)
    }
    return inlay
  }

  @JvmOverloads
  @JvmStatic
  fun createSettingsPresentation(editor: EditorImpl, onClick: (MouseEvent) -> Unit = {}): SelectableInlayPresentation {
    val factory = PresentationFactory(editor)
    fun button(background: Color?): InlayPresentation {
      val scaledFactory = ScaleAwarePresentationFactory(editor, factory)
      val icon = scaledFactory.icon(AllIcons.Actions.InlayGear)
      val inset = (editor.lineHeight - icon.height) / 2
      val button = scaledFactory.container(
        presentation = factory.inset(icon, left = inset, right = inset, top = inset, down = inset),
        roundedCorners = InlayPresentationFactory.RoundedCorners(6, 6),
        background = background
      )
      return scaledFactory.inset(button, left = 3, right = 6)
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
    val scaledFactory = ScaleAwarePresentationFactory(editor, factory)
    val colorsScheme = editor.colorsScheme
    fun button(presentation: InlayPresentation, second: Boolean): InlayPresentation {
      val inset = (editor.lineHeight - presentation.height) / 2
      val padding = InlayPresentationFactory.Padding((if (second) 0 else inset), inset, inset, inset)
      return factory.container(presentation = presentation, padding = padding)
    }

    var tooltip = LangBundle.message("inlay.rename.tooltip.header")
    val commentStringPresentation = initOptions.commentStringOccurrences?.let { commentStringOccurrences ->
      tooltip += LangBundle.message("inlay.rename.tooltip.comments.strings")
      BiStatePresentation(
        first = { scaledFactory.icon(AllIcons.Actions.InlayRenameInCommentsActive) },
        second = { scaledFactory.icon(AllIcons.Actions.InlayRenameInComments) },
        initiallyFirstEnabled = commentStringOccurrences,
      )
    }
    val textPresentation = initOptions.textOccurrences?.let { textOccurrences ->
      tooltip += LangBundle.message("inlay.rename.tooltip.non.code")
      BiStatePresentation(
        first = { scaledFactory.icon(AllIcons.Actions.InlayRenameInNoCodeFilesActive) },
        second = { scaledFactory.icon(AllIcons.Actions.InlayRenameInNoCodeFiles) },
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

    fun withBackground(bgKey: ColorKey) = scaledFactory.container(
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
    return createNavigatableButtonWithPopup(templateState, offset, presentation, panel as JPanel, templateElement, isPopupAbove = false) {
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
      buttonsGroup(LangBundle.message("inlay.rename.also.rename.options.title")) {
        commentsStringsOccurrences?.let {
          row {
            checkBox(RefactoringBundle.message("comments.and.strings"))
              .selected(it)
              .applyToComponent {
                addActionListener {
                  commentsStringsOccurrences = isSelected
                  optionsListener(TextOptions(commentStringOccurrences = commentsStringsOccurrences, textOccurrences = textOccurrences))
                }
              }.gap(RightGap.SMALL)
              .focused()
            icon(AllIcons.Actions.InlayRenameInComments)
          }
        }
        textOccurrences?.let {
          row {
            val cb = checkBox(RefactoringBundle.message("text.occurrences"))
              .selected(it)
              .applyToComponent {
                addActionListener {
                  textOccurrences = isSelected
                  optionsListener(TextOptions(commentStringOccurrences = commentsStringsOccurrences, textOccurrences = textOccurrences))
                }
              }.gap(RightGap.SMALL)
            if (commentsStringsOccurrences == null) {
              cb.focused()
            }
            icon(AllIcons.Actions.InlayRenameInNoCodeFiles)
          }
        }
      }
      row {
        link(LangBundle.message("inlay.rename.link.label.more.options")) {
          doRename(editor, renameAction, null)
        }.gap(RightGap.SMALL)
        comment(KeymapUtil.getFirstKeyboardShortcutText(renameAction))
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
    ActionUtil.performAction(renameAction, event)
  }
}
