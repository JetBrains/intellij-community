package com.intellij.codeInsight.codeVision.ui.renderers.providers

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.AdditionalCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.CounterCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.RichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionRichTextPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.DefaultCodeVisionPainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.ICodeVisionEntryBasePainter
import com.intellij.codeInsight.codeVision.ui.renderers.painters.CodeVisionVisionTextPainter
import com.intellij.openapi.util.ClassExtension


class CodeVisionPainterProviders : ClassExtension<ICodeVisionEntryBasePainter<CodeVisionEntry>>("com.intellij.codeVisionPainterProvider") {
  companion object {
    val INSTANCE = CodeVisionPainterProviders()
  }

  fun <T : CodeVisionEntry> getPainter(element: T): ICodeVisionEntryBasePainter<T> {
    return INSTANCE.forClass(element.javaClass) as? ICodeVisionEntryBasePainter<T> ?: CodeVisionVisionTextPainter({
                                                                                                                    it.toString()
                                                                                                                  })
  }
}

class CounterCodeVisionEntryPainter : DefaultCodeVisionPainter<CounterCodeVisionEntry>({ _, entry, _ -> entry.icon },
                                                                                       CodeVisionVisionTextPainter(
                                                                                         { "${it.count} ${it.text}" }))

class TextCodeVisionEntryPainter : DefaultCodeVisionPainter<TextCodeVisionEntry>({ _, entry, _ -> entry.icon },
                                                                                 CodeVisionVisionTextPainter({ it.text }))

class AdditionalCodeVisionEntryPainter : DefaultCodeVisionPainter<AdditionalCodeVisionEntry>({ _, it, _ -> it.swingIcon },
                                                                                             CodeVisionVisionTextPainter({ it.text }))

class RichTextCodeVisionEntryPainter : DefaultCodeVisionPainter<RichTextCodeVisionEntry>({ _, entry, _ -> entry.icon },
                                                                                         CodeVisionRichTextPainter({ it.text }))

fun CodeVisionEntry.painter(): ICodeVisionEntryBasePainter<CodeVisionEntry> {
  return CodeVisionPainterProviders.INSTANCE.getPainter(this@painter)
}