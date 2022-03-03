// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.util.function.Supplier

val PARAMETER_NAME_HINTS_EP = ExtensionPointName.create<LanguageExtensionPoint<InlayParameterHintsProvider>>("com.intellij.codeInsight.parameterNameHints")

object InlayParameterHintsExtension : LanguageExtension<InlayParameterHintsProvider>(PARAMETER_NAME_HINTS_EP)

/**
 * If you are just implementing parameter hints, only three options are relevant to you: text, offset, isShowOnlyIfExistedBefore. The rest
 * can be used in completion hints.
 *
 * @property text hints text to show
 * @property offset offset in document where hint should be shown
 * @property isShowOnlyIfExistedBefore defines if hint should be shown only if it was present in editor before update
 *
 * @property isFilterByExcludeList allows to prevent hints from filtering by blacklist matcher (possible use in completion hints)
 * @property relatesToPrecedingText whether hint is associated with previous or following text
 * @property widthAdjustment allows resulting hint's width to match certain editor text's width, see [HintWidthAdjustment]
 */
class InlayInfo(val text: String,
                val offset: Int,
                val isShowOnlyIfExistedBefore: Boolean,
                val isFilterByExcludeList: Boolean,
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
   * Provides fully qualified method name (e.g. "java.util.Map.put") and list of its parameter names.
   * Used to match method with exclude list, and to add method into exclude list.
   * @language in case you want to put this method into blacklist of another language.
   */
  open class MethodInfo(val fullyQualifiedName: String, val paramNames: List<String>, val language: Language?) : HintInfo() {
    constructor(fullyQualifiedName: String, paramNames: List<String>) : this(fullyQualifiedName, paramNames, null)

    /**
     * Presentable method name which will be shown to the user when adding it to exclude list.
     */
    open fun getMethodName(): String {
      val start = fullyQualifiedName.lastIndexOf('.') + 1
      return fullyQualifiedName.substring(start)
    }

    /**
     * Presentable text which will be shown in the inlay hints popup menu.
     */
    @NlsActions.ActionText
    open fun getDisableHintText(): String {
      return CodeInsightBundle.message("inlay.hints.show.settings", getMethodName())
    }
  }

  /**
   * Provides option to disable/enable by alt-enter.
   */
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

/**
 * @param id global unique identifier of this option
 * @param nameSupplier user visible name supplier
 */
data class Option(@NonNls val id: String,
                  private val nameSupplier: Supplier<@NlsContexts.DetailedDescription String>,
                  val defaultValue: Boolean) {

  @Deprecated("Use default constructor")
  constructor(@NonNls id: String, @Nls name: String, defaultValue: Boolean) : this(id, Supplier { name }, defaultValue)

  @get:NlsContexts.DetailedDescription
  val name: String
    get() = nameSupplier.get()

  var extendedDescriptionSupplier: Supplier<@NlsContexts.DetailedDescription String>? = null

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

