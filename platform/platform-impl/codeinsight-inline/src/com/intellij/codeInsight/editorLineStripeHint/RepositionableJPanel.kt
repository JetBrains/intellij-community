// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorLineStripeHint

import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
abstract class RepositionableJPanel: JPanel() {
  abstract fun reposition()
}