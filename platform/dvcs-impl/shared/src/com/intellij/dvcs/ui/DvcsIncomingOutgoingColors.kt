// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.ui

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus

/**
 * The colors of the incoming and outgoing commit counters, for example in the branches popup and in the push dialog.
 */
@ApiStatus.Internal
object DvcsIncomingOutgoingColors {
  @JvmField
  val INCOMING_FOREGROUND: JBColor = JBColor(ColorUtil.fromHex("#3574F0"), ColorUtil.fromHex("#548AF7"))

  @JvmField
  val OUTGOING_FOREGROUND: JBColor = JBColor(ColorUtil.fromHex("#369650"), ColorUtil.fromHex("#57965C"))
}
