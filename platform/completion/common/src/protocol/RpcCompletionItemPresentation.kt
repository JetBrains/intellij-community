// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.completion.common.protocol

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.ide.rpc.util.textRange
import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import kotlinx.serialization.Serializable

/**
 * Represents [com.intellij.codeInsight.lookup.LookupElementPresentation]
 */
@Serializable
data class RpcCompletionItemPresentation(
  val icon: IconId? = null,
  val typeIcon: IconId? = null,
  val typeIconRightAligned: Boolean = false,
  val itemText: String = "",
  val typeText: String = "",
  val strikeout: Boolean = false,
  val itemTextForeground: ColorId,
  val itemTextBold: Boolean = false,
  val itemTextUnderlined: Boolean = false,
  val itemTextItalic: Boolean = false,
  val itemNameDecorations: List<RpcDecoratedTextRange> = emptyList(),
  val itemTailDecorations: List<RpcDecoratedTextRange> = emptyList(),
  val typeGrayed: Boolean = false,
  val tail: List<RpcTextFragment> = emptyList(),
) {
  override fun toString(): String = buildToString("RpcCompletionItemPresentation") {
    fieldWithNullDefault("icon", icon)
    fieldWithNullDefault("typeIcon", typeIcon)
    fieldWithDefault("typeIconRightAligned", typeIconRightAligned, false)
    fieldWithDefault("itemText", itemText, "")
    fieldWithDefault("typeText", typeText, "")
    fieldWithDefault("strikeout", strikeout, false)
    field("itemTextForeground", itemTextForeground)
    fieldWithDefault("itemTextBold", itemTextBold, false)
    fieldWithDefault("itemTextUnderlined", itemTextUnderlined, false)
    fieldWithDefault("itemTextItalic", itemTextItalic, false)
    fieldWithEmptyDefault("itemNameDecorations", itemNameDecorations)
    fieldWithEmptyDefault("itemTailDecorations", itemTailDecorations)
    fieldWithDefault("typeGrayed", typeGrayed, false)
    fieldWithEmptyDefault("tail", tail)
  }
}

fun RpcCompletionItemPresentation.render(presentation: LookupElementPresentation) {
  val rpc = this

  presentation.icon = rpc.icon?.icon()
  presentation.setTypeText(rpc.typeText, rpc.typeIcon?.icon())
  presentation.isTypeIconRightAligned = rpc.typeIconRightAligned
  presentation.itemText = rpc.itemText
  presentation.typeText = rpc.typeText
  presentation.isStrikeout = rpc.strikeout
  presentation.itemTextForeground = rpc.itemTextForeground.color()
  presentation.isItemTextBold = rpc.itemTextBold
  presentation.isItemTextUnderlined = rpc.itemTextUnderlined
  presentation.isItemTextItalic = rpc.itemTextItalic
  presentation.isTypeGrayed = rpc.typeGrayed

  rpc.itemNameDecorations.forEach {
    presentation.decorateItemTextRange(it.textRange.textRange(), it.decoration)
  }
  rpc.itemTailDecorations.forEach {
    presentation.decorateTailItemTextRange(it.textRange.textRange(), it.decoration)
  }
  tail.forEach {
    // todo this is a bit weird, need to test
    if (it.fgColor != null) {
      presentation.setTailText(it.text, it.fgColor.color())
    }
    else if (it.italic) {
      presentation.appendTailTextItalic(it.text, it.grayed)
    }
    else {
      presentation.appendTailText(it.text, it.grayed)
    }
  }
}

fun LookupElementPresentation.toRpc(): RpcCompletionItemPresentation {
  return RpcCompletionItemPresentation(
    icon = icon?.rpcId(),
    typeIcon = typeIcon?.rpcId(),
    typeIconRightAligned = isTypeIconRightAligned,
    itemText = itemText ?: "",
    typeText = typeText ?: "",
    strikeout = isStrikeout,
    itemTextForeground = itemTextForeground.rpcId(),
    itemTextBold = isItemTextBold,
    itemTextUnderlined = isItemTextUnderlined,
    itemTextItalic = isItemTextItalic,
    itemNameDecorations = itemNameDecorations.map { it.toRpc() },
    itemTailDecorations = itemTailDecorations.map { it.toRpc() },
    typeGrayed = isTypeGrayed,
    tail = tailFragments.map { it.toRpc() }
  )
}



