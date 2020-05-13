// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement
import com.intellij.util.KeyedLazyInstance
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

val PARAMETER_NAME_HINTS_EP = ExtensionPointName.create<KeyedLazyInstance<InlayParameterHintsProvider>>("com.intellij.codeInsight.parameterNameHints")

object InlayParameterHintsExtension : LanguageExtension<InlayParameterHintsProvider>(PARAMETER_NAME_HINTS_EP)

/**
 * If you are just implementing parameter hints, only three options are relevant to you: text, offset, isShowOnlyIfExistedBefore. The rest
 * can be used in completion hints.
 *
 * @property text hints text to show
 * @property offset offset in document where hint should be shown
 * @property isShowOnlyIfExistedBefore defines if hint should be shown only if it was present in editor before update
 *
 * @property isFilterByBlacklist allows to prevent hints from filtering by blacklist matcher (possible use in completion hints)
 * @property relatesToPrecedingText whether hint is associated with previous or following text
 * @property widthAdjustment allows resulting hint's width to match certain editor text's width, see [HintWidthAdjustment]
 */
class InlayInfo(val text: String,
                val offset: Int,
                val isShowOnlyIfExistedBefore: Boolean,
                val isFilterByBlacklist: Boolean,
                val relatesToPrecedingText: Boolean,
                val widthAdjustment: HintWidthAdjustment?) {

  constructor(text: String, offset: Int, isShowOnlyIfExistedBefore: Boolean, isFilterByBlacklist: Boolean, relatesToPrecedingText: Boolean)
    : this(text, offset, isShowOnlyIfExistedBefore, isFilterByBlacklist, relatesToPrecedingText, null)
  constructor(text: String, offset: Int, isShowOnlyIfExistedBefore: Boolean) : this(text, offset, isShowOnlyIfExistedBefore, true, false)
  constructor(text: String, offset: Int) : this(text, offset, false, true, false)

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false

    other as InlayInfo

    if (text != other.text) return false
    if (offset != other.offset) return false

    return true
  }

  override fun hashCode(): Int {
    var result = text.hashCode()
    result = 31 * result + offset
    return result
  }

}


sealed class HintInfo {

  /**
   * @language in case you want to put this method into blacklist of another language
   */
  open class MethodInfo(val fullyQualifiedName: String, val paramNames: List<String>, val language: Language?) : HintInfo() {
    constructor(fullyQualifiedName: String, paramNames: List<String>) : this(fullyQualifiedName, paramNames, null)

    open fun getMethodName(): String {
      val start = fullyQualifiedName.lastIndexOf('.') + 1
      return fullyQualifiedName.substring(start)
    }
  }

  open class OptionInfo(protected val option: Option) : HintInfo() {

    open fun disable(): Unit = alternate()
    open fun enable(): Unit = alternate()

    private fun alternate() {
      val current = option.get()
      option.set(!current)
    }

    open val optionName: String = option.name

    fun isOptionEnabled(): Boolean = option.isEnabled()

  }

  open fun isOwnedByPsiElement(elem: PsiElement, editor: Editor): Boolean {
    val textRange = elem.textRange
    if (textRange == null) return false
    val start = if (textRange.isEmpty) textRange.startOffset else textRange.startOffset + 1
    return editor.inlayModel.hasInlineElementsInRange(start, textRange.endOffset)
  }
}

data class Option(@NonNls val id: String,
                  private val nameSupplier: Supplier<String>,
                  val defaultValue: Boolean) {

  @Deprecated("Use default constructor")
  constructor(@NonNls id: String, @Nls name: String, defaultValue: Boolean) : this(id, Supplier { name }, defaultValue)

  val name: String
    get() = nameSupplier.get()

  var extendedDescriptionSupplier: Supplier<String>? = null

  fun isEnabled(): Boolean = get()

  fun get(): Boolean {
    return ParameterNameHintsSettings.getInstance().getOption(id) ?: defaultValue
  }

  fun set(newValue: Boolean) {
    val settings = ParameterNameHintsSettings.getInstance()
    if (newValue == defaultValue) {
      settings.setOption(id, null)
    }
    else {
      settings.setOption(id, newValue)
    }
  }

}

