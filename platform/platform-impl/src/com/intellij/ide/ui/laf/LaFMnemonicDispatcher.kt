// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import java.awt.AWTEvent
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.lang.ref.WeakReference
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.milliseconds

private class RepaintMnemonicRequest(@JvmField val focusOwnerRef: WeakReference<Component>, @JvmField val pressed: Boolean)

private class LaFMnemonicDispatcher : IdeEventQueue.NonLockedEventDispatcher {
  override fun dispatch(e: AWTEvent): Boolean {
    if (e !is KeyEvent || e.keyCode != KeyEvent.VK_ALT) {
      return false
    }

    LookAndFeelThemeAdapter.isAltPressed = e.getID() == KeyEvent.KEY_PRESSED
    val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
    check(service<MnemonicListenerService>().repaintRequests.tryEmit(focusOwner?.let {
      RepaintMnemonicRequest(focusOwnerRef = WeakReference(focusOwner), pressed = LookAndFeelThemeAdapter.isAltPressed)
    }))
    return false
  }
}

@OptIn(FlowPreview::class)
@Service
private class MnemonicListenerService(coroutineScope: CoroutineScope) {
  // null as "cancel all"
  @JvmField
  val repaintRequests = MutableSharedFlow<RepaintMnemonicRequest?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    coroutineScope.launch(CoroutineName("LaF Mnemonic Support")) {
      val repaintDispatcher = Dispatchers.EDT + ModalityState.any().asContextElement()

      repaintRequests
        .debounce(10.milliseconds)
        .collectLatest {
          it?.let {
            withContext(repaintDispatcher) {
              repaintMnemonics(focusOwner = it.focusOwnerRef.get() ?: return@withContext, pressed = it.pressed)
            }
          }
        }
    }
  }

  private fun repaintMnemonics(focusOwner: Component, pressed: Boolean) {
    if (pressed != LookAndFeelThemeAdapter.isAltPressed) {
      return
    }

    val window = SwingUtilities.windowForComponent(focusOwner) ?: return
    for (component in window.components) {
      if (component is JComponent) {
        for (c in ComponentUtil.findComponentsOfType(component, JComponent::class.java)) {
          if (c is JLabel && c.displayedMnemonicIndex != -1 || c is AbstractButton && c.displayedMnemonicIndex != -1) {
            c.repaint()
          }
        }
      }
    }
  }
}