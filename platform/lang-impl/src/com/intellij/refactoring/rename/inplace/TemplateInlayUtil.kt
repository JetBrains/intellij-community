// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.RenameInplacePopupUsagesCollector
import com.intellij.refactoring.rename.RenamePsiElementProcessor
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
import javax.swing.LayoutFocusTraversalPolicy

@ApiStatus.Experimental
object TemplateInlayUtil {

  @JvmStatic
  fun createNavigatableButton(templateState: TemplateState,
                              inEditorOffset: Int,
                              presentation: SelectableInlayPresentation,
                              templateElement: VirtualTemplateElement): Inlay<PresentationRenderer>? {
    val renderer = PresentationRenderer(presentation)
    val inlay = templateState.editor.inlayModel.addInlineElement(inEditorOffset, true, renderer) ?: return null
    VirtualTemplateElement.installOnTemplate(templateState, templateElement)
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

  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: DialogPanel,
                                       templateElement: SelectableTemplateElement = SelectableTemplateElement(presentation),
                                       logStatisticsOnHide : () -> Unit = {}): Inlay<PresentationRenderer>? {
    val editor = templateState.editor
    val inlay = createNavigatableButton(templateState, inEditorOffset, presentation, templateElement) ?: return null
    fun showPopup() {
      try {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, inlay.visualPosition)
        panel.border = JBEmptyBorder(JBInsets.create(Insets(8, 12, 4, 12)))
        val popup = JBPopupFactory.getInstance()
          .createComponentPopupBuilder(panel, panel.preferredFocusedComponent)
          .setRequestFocus(true)
          .addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
              presentation.isSelected = false
              templateState.focusCurrentHighlighter(true)
              logStatisticsOnHide.invoke()
            }
          })
          .createPopup()
        DumbAwareAction.create {
          popup.cancel()
          templateState.nextTab()
          logStatisticsOnHide.invoke()
        }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_EDITOR_ENTER), panel)
        popup.showInBestPositionFor(editor)
      }
      finally {
        editor.putUserData(PopupFactoryImpl.ANCHOR_POPUP_POSITION, null)
      }
    }
    presentation.addSelectionListener(object : SelectableInlayPresentation.SelectionListener {
      override fun selectionChanged(isSelected: Boolean) {
        if (isSelected) showPopup()
      }
    })
    return inlay
  }

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
    return object: SelectableInlayButton(editor,
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
    val editor = templateState.editor as EditorImpl
    val processor = RenamePsiElementProcessor.forElement(elementToRename)

    val factory = PresentationFactory(editor)
    val colorsScheme = editor.colorsScheme
    fun button(iconPresentation: IconPresentation, second: Boolean = false) = factory.container(factory.container(
      presentation = iconPresentation,
      padding = InlayPresentationFactory.Padding(if (second) 0 else 4, 4, 4, 4)
    ))

    var tooltip = LangBundle.message("inlay.rename.tooltip.comments")
    val toSearchInComments = processor.isToSearchInComments(elementToRename)
    val commentsStatusIcon = if (toSearchInComments) AllIcons.Actions.InlayRenameInCommentsActive else AllIcons.Actions.InlayRenameInComments

    var buttonsPresentation = button(factory.icon(commentsStatusIcon))
    val toSearchForTextOccurrences: Boolean
    if (TextOccurrencesUtil.isSearchTextOccurrencesEnabled(elementToRename)) {
      toSearchForTextOccurrences = processor.isToSearchForTextOccurrences(elementToRename)
      val textOccurrencesStatusIcon = if (toSearchForTextOccurrences)
                                                AllIcons.Actions.InlayRenameInNoCodeFilesActive 
                                            else 
                                                AllIcons.Actions.InlayRenameInNoCodeFiles
      val inTextOccurrencesIconPresentation = factory.icon(textOccurrencesStatusIcon)
      buttonsPresentation = factory.seq(buttonsPresentation, button(inTextOccurrencesIconPresentation, true))
      tooltip += LangBundle.message("inlay.rename.tooltip.non.code")
    }
    else {
      toSearchForTextOccurrences = false
    }

    val shortcut = KeymapUtil.getPrimaryShortcut("SelectVirtualTemplateElement")
    if (shortcut != null) {
      tooltip += LangBundle.message("inlay.rename.tooltip.tab.advertisement", KeymapUtil.getShortcutText(shortcut))
    }

    fun withBackground(bgKey: ColorKey) =
      factory.container(factory.container(buttonsPresentation,
                                          roundedCorners = InlayPresentationFactory.RoundedCorners(3, 3),
                                          background = colorsScheme.getColor(bgKey)),
        padding = InlayPresentationFactory.Padding(4, 0,0, 0)
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

    val settings = Settings(toSearchInComments, toSearchForTextOccurrences)
    val panel = renamePanel(elementToRename, editor, settings, restart)
    return createNavigatableButtonWithPopup(templateState, offset, presentation, panel, templateElement) {
      logStatisticsOnHide(editor, toSearchInComments, settings.inComments, toSearchForTextOccurrences, settings.inTextOccurrences)
    }
  }

  private fun logStatisticsOnShow(editor: Editor, mouseEvent: MouseEvent? = null) {
    val showEvent = mouseEvent
                    ?: KeyEvent(editor.component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_TAB, KeyEvent.VK_TAB.toChar())
    RenameInplacePopupUsagesCollector.show.log(editor.project,
                                               EventFields.InputEvent.with(FusInputEvent(showEvent, javaClass.simpleName)))
  }

  private data class Settings (var inComments : Boolean, var inTextOccurrences : Boolean) 

  private fun logStatisticsOnHide(editor: EditorImpl,
                                  toSearchInComments: Boolean,
                                  toSearchInCommentsNew: Boolean,
                                  toSearchForTextOccurrences: Boolean,
                                  toSearchForTextOccurrencesNew: Boolean) {
    RenameInplacePopupUsagesCollector.hide.log(editor.project,
                                               RenameInplacePopupUsagesCollector.searchInCommentsOnHide.with(toSearchInCommentsNew),
                                               RenameInplacePopupUsagesCollector.searchInTextOccurrencesOnHide.with(toSearchForTextOccurrencesNew))
    RenameInplacePopupUsagesCollector.settingsChanged.log(editor.project, RenameInplacePopupUsagesCollector.changedOnHide.with(toSearchInComments != toSearchInCommentsNew || toSearchForTextOccurrences != toSearchForTextOccurrencesNew))
  }

  private fun renamePanel(elementToRename: PsiElement,
                          editor: Editor,
                          settings : Settings,
                          restart: Runnable): DialogPanel {
    val processor = RenamePsiElementProcessor.forElement(elementToRename)
    val renameAction = ActionManager.getInstance().getAction(IdeActions.ACTION_RENAME)
    val panel = panel {
      row(LangBundle.message("inlay.rename.also.rename.options.title")) {
        row {
          cell {
            checkBox(RefactoringBundle.message("comments.and.strings"),
                     processor.isToSearchInComments(elementToRename),
                     actionListener = { _, cb ->
                       settings.inComments = cb.isSelected
                       processor.setToSearchInComments(elementToRename, cb.isSelected)
                       restart.run()
                     }
            ).focused()
            component(JLabel(AllIcons.Actions.InlayRenameInComments))
          }
        }
        if (TextOccurrencesUtil.isSearchTextOccurrencesEnabled(elementToRename)) {
          row {
            cell {
              checkBox(RefactoringBundle.message("text.occurrences"),
                       processor.isToSearchForTextOccurrences(elementToRename),
                       actionListener = { _, cb ->
                         settings.inTextOccurrences = cb.isSelected
                         processor.setToSearchForTextOccurrences(elementToRename, cb.isSelected)
                         restart.run()
                       })
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
      ActionUtil.performActionDumbAware(renameAction, event)
    }
  }
}