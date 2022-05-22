// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import org.intellij.lang.annotations.JdkConstants

class JBThinOverlappingScrollBar(@JdkConstants.AdjustableOrientation orientation: Int) : JBScrollBar(orientation) {
  override fun isThin(): Boolean = true
}