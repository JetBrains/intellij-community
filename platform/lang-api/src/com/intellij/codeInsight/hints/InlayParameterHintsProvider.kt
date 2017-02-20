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
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

object InlayParameterHintsExtension: LanguageExtension<InlayParameterHintsProvider>("com.intellij.codeInsight.parameterNameHints")

@ApiStatus.Experimental
interface InlayParameterHintsProvider {

  /**
   * Hints for params to be shown
   */
  fun getParameterHints(element: PsiElement): List<InlayInfo>

  /**
   * Provides hint info, for alt-enter action (can be MethodInfo or OptionInfo)
   *
   * MethodInfo: provides fully qualified method name (e.g. "java.util.Map.put") and list of it's parameter names.
   * Used to match method with blacklist, and to add method into blacklist
   * 
   * OptionInfo: provides option to disable/enable by alt-enter
   */
  fun getHintInfo(element: PsiElement): HintInfo?
  
  /**
   * Default list of patterns for which hints should not be shown
   */
  val defaultBlackList: Set<String>

  /**
   * Returns language which blacklist will be appended to the resulting one
   * E.g. to prevent possible Groovy and Kotlin extensions from showing hints for blacklisted java methods. 
   */
  fun getBlackListDependencyLanguage(): Language? = null

  /**
   * List of supported options, shown in settings dialog
   */
  fun getSupportedOptions(): List<Option> = emptyList()

  /**
   * If false no blacklist panel will be shown in "Parameter Name Hints Settings"
   */
  fun isBlackListSupported() = true

}


data class InlayInfo(val text: String, val offset: Int)


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
  }

}

data class Option(val id: String,
                  val name: String,
                  val defaultValue: Boolean) {

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

