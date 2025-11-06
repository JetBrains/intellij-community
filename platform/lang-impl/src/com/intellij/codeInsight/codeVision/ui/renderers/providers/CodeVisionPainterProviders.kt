// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers.providers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.AdditionalCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RangeCodeVisionModel
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZeroWidthCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ZombieCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionRichTextPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionVisionTextPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.DefaultCodeVisionPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.ICodeVisionEntryBasePainter
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.ClassExtension
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point

private val INSTANCE = CodeVisionPainterProviders()

private class CodeVisionPainterProviders : ClassExtension<ICodeVisionEntryBasePainter<CodeVisionEntry>>("com.intellij.codeVisionPainterProvider") {
  fun <T : CodeVisionEntry> getPainter(element: T): ICodeVisionEntryBasePainter<T> {
    return INSTANCE.forClass(element.javaClass) as? ICodeVisionEntryBasePainter<T>
           ?: CodeVisionVisionTextPainter({ it.toString() })
  }
}

private class CounterCodeVisionEntryPainter : DefaultCodeVisionPainter<CounterCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.icon },
  textPainter = CodeVisionVisionTextPainter({ "${it.count} ${it.text}" })
)

private class TextCodeVisionEntryPainter : DefaultCodeVisionPainter<TextCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.icon },
  textPainter = CodeVisionVisionTextPainter({ it.text })
)

private class AdditionalCodeVisionEntryPainter : DefaultCodeVisionPainter<AdditionalCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.swingIcon },
  textPainter = CodeVisionVisionTextPainter({ it.text })
)

private class RichTextCodeVisionEntryPainter : DefaultCodeVisionPainter<RichTextCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.icon },
  textPainter = CodeVisionRichTextPainter({ it.text })
)

private class ZombieCodeVisionEntryPainter : DefaultCodeVisionPainter<ZombieCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.icon },
  textPainter = CodeVisionVisionTextPainter({
    val result = if (it.count != null) "${it.count} ${it.text}" else if (it.shouldBeDelimited) it.text else ""
    val debug = Registry.`is`("cache.markup.debug")
    if (debug) "${result}?" else result
  })
) {
  override fun shouldBeDelimited(entry: ZombieCodeVisionEntry): Boolean {
    return entry.shouldBeDelimited
  }
}

private class ZeroWidthCodeVisionEntryPainter : ICodeVisionEntryBasePainter<ZeroWidthCodeVisionEntry> {

  override fun paint(
    editor: Editor,
    textAttributes: TextAttributes,
    g: Graphics,
    value: ZeroWidthCodeVisionEntry,
    point: Point,
    state: RangeCodeVisionModel.InlayState,
    hovered: Boolean,
    hoveredEntry: CodeVisionEntry?
  ) {
    /* no-op */
  }

  override fun size(editor: Editor, state: RangeCodeVisionModel.InlayState, value: ZeroWidthCodeVisionEntry): Dimension {
    return Dimension(0, 0)
  }

  override fun shouldBeDelimited(entry: ZeroWidthCodeVisionEntry): Boolean {
    return false
  }
}

@ApiStatus.Internal
fun CodeVisionEntry.painter(): ICodeVisionEntryBasePainter<CodeVisionEntry> {
  return INSTANCE.getPainter(this@painter)
}