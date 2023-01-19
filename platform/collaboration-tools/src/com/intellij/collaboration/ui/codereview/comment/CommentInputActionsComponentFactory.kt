package com.intellij.collaboration.ui.codereview.comment

import com.intellij.collaboration.ui.CollaborationToolsUIUtil.isDefault
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.ui.components.JBOptionButton
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.*

object CommentInputActionsComponentFactory {

  fun create(cfg: Config): JComponent {
    val scopeProvider = ActivatableCoroutineScopeProvider()

    val panel = JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC().insets("0").gridGap("12", "0").fill().noGrid())

      add(createHintsComponent(scopeProvider, cfg.hintInfo),
          CC().minWidth("0").shrinkPrio(10).alignX("right"))
      add(createActionButtonsComponent(scopeProvider, cfg),
          CC().shrinkPrio(0).alignX("right"))
    }.also {
      scopeProvider.activateWith(it)
    }

    return panel
  }

  private fun createHintsComponent(scopeProvider: ActivatableCoroutineScopeProvider, hintInfoState: StateFlow<HintInfo>): JComponent {
    val panel = HorizontalListPanel(12)

    scopeProvider.launchInScope {
      fun createHintLabel(text: @Nls String) = JLabel(text).apply {
        foreground = UIUtil.getContextHelpForeground()
        font = JBFont.small()
        minimumSize = Dimension(0,0)
      }

      hintInfoState.collectLatest { hintInfo ->
        with(panel) {
          add(createHintLabel(hintInfo.submitHint))
          add(createHintLabel(hintInfo.newLineHint))
          validate()
          repaint()

          try {
            awaitCancellation()
          }
          finally {
            removeAll()
            revalidate()
            repaint()
          }
        }
      }
    }
    return panel
  }

  private fun createActionButtonsComponent(scopeProvider: ActivatableCoroutineScopeProvider, cfg: Config): JComponent {
    return HorizontalListPanel(8).apply {
      add(createAdditionalButtons(scopeProvider, cfg.additionalActions))
      add(createMainButton(scopeProvider, cfg.primaryAction, cfg.secondaryActions))
    }
  }

  private fun createAdditionalButtons(scopeProvider: ActivatableCoroutineScopeProvider, actionsState: StateFlow<List<Action>>): JComponent {
    val panel = HorizontalListPanel(8)
    scopeProvider.launchInScope {
      actionsState.collectLatest { actions ->
        with(panel) {
          actions.forEach {
            add(JButton(it).apply {
              isOpaque = false
            })
          }
          validate()
          repaint()

          try {
            awaitCancellation()
          }
          finally {
            removeAll()
            revalidate()
            repaint()
          }
        }
      }
    }
    return panel
  }

  private fun createMainButton(scopeProvider: ActivatableCoroutineScopeProvider,
                               primaryActionState: StateFlow<Action>,
                               secondaryActionsState: StateFlow<List<Action>>): JComponent {
    val btn = JBOptionButton(null, null).apply {
      isDefault = true
    }

    scopeProvider.launchInScope {
      primaryActionState.collectLatest {
        btn.action = it
        try {
          awaitCancellation()
        }
        finally {
          btn.action = null
        }
      }
    }
    scopeProvider.launchInScope {
      secondaryActionsState.collectLatest {
        btn.options = it.toTypedArray()
        try {
          awaitCancellation()
        }
        finally {
          btn.options = null
        }
      }
    }

    return btn
  }

  data class Config(
    val primaryAction: StateFlow<Action>,
    val secondaryActions: StateFlow<List<Action>> = MutableStateFlow(listOf()),
    val additionalActions: StateFlow<List<Action>> = MutableStateFlow(listOf()),
    val hintInfo: StateFlow<HintInfo>
  )

  data class HintInfo(val submitHint: @Nls String, val newLineHint: @Nls String)

  fun attachActions(component: JComponent, cfg: Config): JComponent =
    VerticalListPanel().apply {
      add(component)
      add(create(cfg))
    }
}