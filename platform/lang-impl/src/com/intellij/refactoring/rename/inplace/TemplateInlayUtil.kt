// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.inplace

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.codeInsight.hints.presentation.*
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
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
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.ui.layout.*
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JLabel
import javax.swing.LayoutFocusTraversalPolicy

@ApiStatus.Experimental
object TemplateInlayUtil {

  @JvmStatic
  fun createNavigatableButton(templateState: TemplateState,
                              inEditorOffset: Int,
                              presentation: SelectableInlayPresentation): Inlay<PresentationRenderer>? {
    val renderer = PresentationRenderer(presentation)
    val inlay = templateState.editor.inlayModel.addInlineElement(inEditorOffset, true, renderer) ?: return null
    VirtualTemplateElement.installOnTemplate(templateState, object : VirtualTemplateElement {
      override fun onSelect(templateState: TemplateState) {
        presentation.isSelected = true
        templateState.focusCurrentHighlighter(false)
      }
    })
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

  @JvmStatic
  fun createNavigatableButtonWithPopup(templateState: TemplateState,
                                       inEditorOffset: Int,
                                       presentation: SelectableInlayPresentation,
                                       panel: DialogPanel): Inlay<PresentationRenderer>? {
    val editor = templateState.editor
    val inlay = createNavigatableButton(templateState, inEditorOffset, presentation) ?: return null
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
            }
          })
          .createPopup()
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
  fun createSettingsPresentation(editor: EditorImpl): SelectableInlayPresentation {
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
    return SelectableInlayButton(
      editor,
      default = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_DEFAULT)),
      active = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_FOCUSED)),
      hovered = button(colorsScheme.getColor(INLINE_REFACTORING_SETTINGS_HOVERED))
    )
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
    val commentsStatusIcon = if (processor.isToSearchInComments(elementToRename)) AllIcons.Actions.InlayRenameInCommentsActive else AllIcons.Actions.InlayRenameInComments

    var buttonsPresentation = button(factory.icon(commentsStatusIcon))
    if (TextOccurrencesUtil.isSearchTextOccurrencesEnabled(elementToRename)) {
      val textOccurrencesStatusIcon = if (processor.isToSearchForTextOccurrences(elementToRename))
                                                AllIcons.Actions.InlayRenameInNoCodeFilesActive 
                                            else 
                                                AllIcons.Actions.InlayRenameInNoCodeFiles
      val inTextOccurrencesIconPresentation = factory.icon(textOccurrencesStatusIcon)
      buttonsPresentation = factory.seq(buttonsPresentation, button(inTextOccurrencesIconPresentation, true))
      tooltip += LangBundle.message("inlay.rename.tooltip.non.code")
    }
    tooltip += LangBundle.message("inlay.rename.tooltip.tab.advertisement")

    fun withBackground(bgKey: ColorKey) =
      factory.container(factory.container(buttonsPresentation,
                                          roundedCorners = InlayPresentationFactory.RoundedCorners(3, 3),
                                          background = colorsScheme.getColor(bgKey)),
        padding = InlayPresentationFactory.Padding(4, 0,0, 0)
      )
    
    val presentation = SelectableInlayButton(editor,
                                             withBackground(INLINE_REFACTORING_SETTINGS_DEFAULT),
                                             withBackground(INLINE_REFACTORING_SETTINGS_FOCUSED),
                                             factory.withTooltip(tooltip, withBackground(INLINE_REFACTORING_SETTINGS_HOVERED)))
    val panel = renamePanel(elementToRename, editor, restart)
    return createNavigatableButtonWithPopup(templateState, offset, presentation, panel)
  }

  private fun renamePanel(elementToRename: PsiElement,
                          editor: Editor,
                          restart: Runnable): DialogPanel {
    val processor = RenamePsiElementProcessor.forElement(elementToRename)
    val renameAction = ActionManager.getInstance().getAction(IdeActions.ACTION_RENAME)
    val panel = panel {
      row(LangBundle.message("inlay.rename.also.rename.options.title")) {
        row {
          cell {
            checkBox(RefactoringBundle.message("comments.and.strings"),
                     processor.isToSearchInComments(elementToRename),
                     actionListener = { _, cb ->  processor.setToSearchInComments(elementToRename, cb.isSelected)
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
                       actionListener = { _, cb -> processor.setToSearchForTextOccurrences(elementToRename, cb.isSelected)
                                                   restart.run()})
              component(JLabel(AllIcons.Actions.InlayRenameInNoCodeFiles))
            }
          }
        }
      }
      row {
        cell {
          link(LangBundle.message("inlay.rename.link.label.more.options"), null) {
            doRename(editor, renameAction)
          }.component.isFocusable = true
          comment(KeymapUtil.getFirstKeyboardShortcutText(renameAction))
        }
      }
    }
    DumbAwareAction.create {
      doRename(editor, renameAction)
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_RENAME), panel)
    panel.isFocusCycleRoot = true
    panel.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    return panel
  }

  private fun doRename(editor: Editor, renameAction: AnAction) {
    val event = AnActionEvent(null,
                              DataManager.getInstance().getDataContext(editor.component),
                              ActionPlaces.UNKNOWN, renameAction.templatePresentation.clone(),
                              ActionManager.getInstance(), 0)
    if (ActionUtil.lastUpdateAndCheckDumb(renameAction, event, true)) {
      ActionUtil.performActionDumbAware(renameAction, event)
    }
  }
}