// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.IconManager
import icons.DvcsImplIcons
import javax.swing.Icon

object DvcsImplIconsExt {
  @JvmStatic
  val incomingOutgoingIcon: Icon
    get() = if (ExperimentalUI.isNewUI()) IconManager.getInstance().createRowIcon(DvcsImplIcons.Incoming, DvcsImplIcons.Outgoing)
    else DvcsImplIcons.IncomingOutgoing
}