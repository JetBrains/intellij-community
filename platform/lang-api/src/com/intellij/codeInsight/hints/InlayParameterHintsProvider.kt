/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.lang.LanguageExtension


object InlayParameterHintsExtension: LanguageExtension<InlayParameterHintsProvider>("com.intellij.codeInsight.parameterNameHints")


class InlayInfo(val text: String, val offset: Int, val isShowOnlyIfExistedBefore: Boolean) {
  
  constructor(text: String, offset: Int): this(text, offset, false)
  
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

  open class MethodInfo(val fullyQualifiedName: String, val paramNames: List<String>) : HintInfo() {
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

