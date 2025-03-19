// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.util.SystemInfoRt
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

internal object MnemonicUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("ui.mnemonic", 2)
  private val MNEMONIC_USED = GROUP.registerEvent("mnemonic.used",
                                                  EventFields.String("type", listOf("mac.alt.based", "regular", "mac.regular")))

  @JvmStatic
  fun logMnemonicUsed(ke: KeyEvent?) {
    if (ke == null) return
    val code = ke.keyCode
    if (KeyEvent.VK_0 <= code && code <= KeyEvent.VK_Z) {
      val modifiers = ke.modifiersEx
      var type: String? = null
      if (modifiers == InputEvent.ALT_DOWN_MASK) {
        if (IdeKeyEventDispatcher.hasMnemonicInWindow(ke.component, ke)) {
          type = if (SystemInfoRt.isMac) "mac.alt.based" else "regular"
        }
      }
      else if (SystemInfoRt.isMac && modifiers == InputEvent.ALT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK) {
        if (IdeKeyEventDispatcher.hasMnemonicInWindow(ke.component, ke)) {
          type = "mac.regular"
        }
      }
      if (type != null) {
        MNEMONIC_USED.log(type)
      }
    }
  }
}