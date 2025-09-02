package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.*

object CommentInputActionsComponentFactory {

  val submitShortcutText: @NlsSafe String
    get() = KeymapUtil.getFirstKeyboardShortcutText(SUBMIT_SHORTCUT)
  val newLineShortcutText: @NlsSafe String
    get() = KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.ENTER)

  private val SUBMIT_SHORTCUT
    get() = CommonShortcuts.getCtrlEnter()
  private val CANCEL_SHORTCUT = CommonShortcuts.ESCAPE

  fun create(cs: CoroutineScope, cfg: Config): JComponent {
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().insets("0").gridGap("12", "0").fill().noGrid())

      add(createHintsComponent(cs, cfg.submitHint),
          CC().minWidth("0").shrinkPrio(10).alignX("right"))
      add(createActionButtonsComponent(cs, cfg),
          CC().shrinkPrio(0).alignX("right"))
    }
  }

  private fun createHintsComponent(cs: CoroutineScope, submitHintState: StateFlow<@Nls String>): JComponent {
    fun createHintLabel(text: @Nls String) = JLabel(text).apply {
      foreground = UIUtil.getContextHelpForeground()
      font = JBFont.small()
      minimumSize = Dimension(0, 0)
    }

    return HorizontalListPanel(12).apply {
      add(createHintLabel(CollaborationToolsBundle.message("review.comment.new.line.hint", newLineShortcutText)))

      bindChildIn(cs, submitHintState, index = 0, componentFactory = { hint ->
        createHintLabel(hint)
      })
    }
  }

  private fun createActionButtonsComponent(cs: CoroutineScope, cfg: Config): JComponent {
    val panel = HorizontalListPanel(8)
    val buttonsFlow = combine(cfg.primaryAction,
                              cfg.secondaryActions,
                              cfg.additionalActions,
                              cfg.cancelAction) { primary, secondary, additional, cancel ->
      val buttons = mutableListOf<JComponent>()
      if (cancel != null && cancel.name.orEmpty().isNotEmpty()) {
        buttons.add(JButton(cancel).apply {
          isOpaque = false
        })
      }

      for (additionalAction in additional) {
        buttons.add(JButton(additionalAction).apply {
          isOpaque = false
        })
      }

      JBOptionButton(primary, secondary.toTypedArray()).apply {
        isDefault = true
      }.also {
        buttons.add(it)
      }
      buttons.toList()
    }
    cs.launch {
      buttonsFlow.collect { buttons ->
        with(panel) {
          removeAll()
          for (button in buttons) {
            add(button)
          }
          revalidate()
          repaint()
        }
      }
    }

    return panel
  }

  class Config(
    val primaryAction: StateFlow<Action?>,
    val secondaryActions: StateFlow<List<Action>> = MutableStateFlow(listOf()),
    val additionalActions: StateFlow<List<Action>> = MutableStateFlow(listOf()),
    val cancelAction: StateFlow<Action?> = MutableStateFlow(null),
    val submitHint: StateFlow<@Nls String>
  )

  @Deprecated("Use a version with CoroutineScope")
  fun attachActions(component: JComponent, cfg: Config): JComponent {
    return VerticalListPanel().apply {
      add(component)
    }.apply {
      ActivatableCoroutineScopeProvider().apply {
        launchInScope {
          val actionsPanel = create(this, cfg)
          add(actionsPanel)
          validate()
          repaint()

          installActionShortcut(this, cfg.primaryAction, SUBMIT_SHORTCUT, true)
          installActionShortcut(this, cfg.cancelAction, CANCEL_SHORTCUT)

          try {
            awaitCancellation()
          }
          finally {
            remove(actionsPanel)
            revalidate()
            repaint()
          }
        }
      }.activateWith(this)
    }
  }

  fun attachActions(cs: CoroutineScope, component: JComponent, cfg: Config): JComponent =
    VerticalListPanel().apply {
      add(component)
      add(create(cs, cfg))

      // override action bound to Ctrl+Enter because convenience wins
      installActionShortcut(cs, cfg.primaryAction, SUBMIT_SHORTCUT, true)
      installActionShortcut(cs, cfg.cancelAction, CANCEL_SHORTCUT)
    }

  private fun JComponent.installActionShortcut(cs: CoroutineScope,
                                               action: StateFlow<Action?>,
                                               shortcut: ShortcutSet,
                                               overrideEditorAction: Boolean = false) {
    val component = this
    cs.launch {
      action.filterNotNull().collectLatest { action ->
        // installed as AnAction, bc otherwise Esc is stolen by editor
        val anAction = if (overrideEditorAction) action.toAnAction() else action.toAnActionWithEditorPromotion()
        try {
          anAction.registerCustomShortcutSet(shortcut, component)
          awaitCancellation()
        }
        finally {
          anAction.unregisterCustomShortcutSet(component)
        }
      }
    }
  }

  /**
   * This is required for Exc handler to work properly (close popup or clear selection)
   * Otherwise our Esc handler will fire first
   */
  private fun Action.toAnActionWithEditorPromotion(): AnAction {
    val action = this
    return object : DumbAwareAction(action.name.orEmpty()), ActionPromoter {
      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = action.isEnabled
      }

      override fun actionPerformed(event: AnActionEvent) = performAction(event)

      override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction> =
        actions.filterIsInstance<EditorAction>()
    }
  }
}