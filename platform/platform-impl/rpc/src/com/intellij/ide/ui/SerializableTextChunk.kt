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
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

@ApiStatus.Internal
fun TextChunk.toSerializableTextChunk(): SerializableTextChunk = SerializableTextChunk(this)

@ApiStatus.Internal
fun SerializableTextChunk.textChunk(): TextChunk  = this.toTextChunk()

@ApiStatus.Internal
@Serializable
class SerializableTextChunk(val text: @NlsSafe String, val foregroundColorId: ColorId? = null, val fontType: Int = Font.PLAIN, val effectType: EffectType? = EffectType.BOXED, val effectColor: ColorId? = null, @Transient private val textChunk: TextChunk? = null) {

  constructor(textChunk: TextChunk) : this(textChunk.text, textChunk.attributes.foregroundColor?.rpcId(), textChunk.attributes.fontType, textChunk.attributes.effectType, textChunk.attributes.effectColor?.rpcId(), textChunk)

  constructor(text: String, textAttributes: TextAttributes) : this(text, textAttributes.foregroundColor?.rpcId(), textAttributes.fontType, textAttributes.effectType, textAttributes.effectColor?.rpcId())

  constructor(text: @NlsSafe String, simpleTextAttributes: SimpleTextAttributes) : this(text, simpleTextAttributes.toTextAttributes())

  internal fun toTextChunk(): TextChunk = textChunk ?:
    TextChunk(TextAttributes(foregroundColorId?.color(), null, effectColor?.color(), effectType, fontType), text)
}