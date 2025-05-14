// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui


import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.TextChunk
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
class SerializableTextChunk(val text: @NlsSafe String, val foregroundColorId: ColorId?, val fontType: Int, val effectType: EffectType?, val effectColor: ColorId?) {

  constructor(text: String, textAttributes: TextAttributes) : this(text, textAttributes.foregroundColor?.rpcId(), textAttributes.fontType, textAttributes.effectType, textAttributes.effectColor?.rpcId())

  constructor(text: @NlsSafe String, simpleTextAttributes: SimpleTextAttributes) : this(text, simpleTextAttributes.toTextAttributes())

  constructor(text: String) : this(text, null, 0, null, null)

  fun toTextChunk(): TextChunk = TextChunk(TextAttributes(foregroundColorId?.color(), null, effectColor?.color(), effectType, fontType), text)
}