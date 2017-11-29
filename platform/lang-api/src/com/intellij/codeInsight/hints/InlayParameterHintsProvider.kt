/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension


object InlayParameterHintsExtension : LanguageExtension<InlayParameterHintsProvider>("com.intellij.codeInsight.parameterNameHints")

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
 */
class InlayInfo(val text: String,
                val offset: Int,
                val isShowOnlyIfExistedBefore: Boolean,
                val isFilterByBlacklist: Boolean,
                val relatesToPrecedingText: Boolean) {

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

    open fun disable() = alternate()
    open fun enable() = alternate()

    private fun alternate() {
      val current = option.get()
      option.set(!current)
    }

    open val optionName = option.name

    fun isOptionEnabled(): Boolean = option.isEnabled()

  }

}

data class Option(val id: String,
                  val name: String,
                  val defaultValue: Boolean) {

  fun isEnabled() = get()

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

