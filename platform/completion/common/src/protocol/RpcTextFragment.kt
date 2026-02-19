// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupElementPresentation.TextFragment
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.rpcId
import kotlinx.serialization.Serializable

@Serializable
data class RpcTextFragment(
  val text: String,
  val grayed: Boolean = false,
  val italic: Boolean = false,
  val fgColor: ColorId? = null,
) {
  override fun toString(): String = buildToString("RpcTextFragment") {
    field("text", text)
    fieldWithDefault("grayed", grayed, false)
    fieldWithDefault("italic", italic, false)
    fieldWithNullDefault("fgColor", fgColor)
  }
}

fun TextFragment.toRpc(): RpcTextFragment = RpcTextFragment(
  text = text,
  grayed = isGrayed,
  italic = isItalic,
  fgColor = foregroundColor?.rpcId()
)
