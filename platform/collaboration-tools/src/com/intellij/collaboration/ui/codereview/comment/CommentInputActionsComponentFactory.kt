package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.collaboration.ui.util.bindChildIn
import com.intellij.collaboration.ui.util.name
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.keymap.KeymapUtil
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

  private val SUBMIT_SHORTCUT = CommonShortcuts.CTRL_ENTER
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

          installActionShortcut(this, cfg.primaryAction, SUBMIT_SHORTCUT)
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

      installActionShortcut(cs, cfg.primaryAction, SUBMIT_SHORTCUT)
      installActionShortcut(cs, cfg.cancelAction, CANCEL_SHORTCUT)
    }

  private fun JComponent.installActionShortcut(cs: CoroutineScope, action: StateFlow<Action?>, shortcut: ShortcutSet) {
    val component = this
    cs.launch {
      action.filterNotNull().collectLatest { action ->
        // installed as AnAction, bc otherwise Esc is stolen by editor
        val anAction = action.toAnAction()
        try {
          putClientProperty(UIUtil.HIDE_EDITOR_FROM_DATA_CONTEXT_PROPERTY, true)
          anAction.registerCustomShortcutSet(shortcut, component)
          awaitCancellation()
        }
        finally {
          anAction.unregisterCustomShortcutSet(component)
        }
      }
    }
  }
}