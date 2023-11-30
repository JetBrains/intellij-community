// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers.providers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.AdditionalCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionRichTextPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionVisionTextPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.DefaultCodeVisionPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.ICodeVisionEntryBasePainter
import com.intellij.openapi.util.ClassExtension

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

private class TextCodeVisionEntryPainter : DefaultCodeVisionPainter<TextCodeVisionEntry>({ _, entry, _ -> entry.icon },
                                                                                 CodeVisionVisionTextPainter({ it.text }))

private class AdditionalCodeVisionEntryPainter : DefaultCodeVisionPainter<AdditionalCodeVisionEntry>(
  iconProvider = { _, it, _ -> it.swingIcon },
  textPainter = CodeVisionVisionTextPainter({ it.text })
)

private class RichTextCodeVisionEntryPainter : DefaultCodeVisionPainter<RichTextCodeVisionEntry>(
  iconProvider = { _, entry, _ -> entry.icon },
  textPainter = CodeVisionRichTextPainter({ it.text })
)

internal fun CodeVisionEntry.painter(): ICodeVisionEntryBasePainter<CodeVisionEntry> {
  return INSTANCE.getPainter(this@painter)
}