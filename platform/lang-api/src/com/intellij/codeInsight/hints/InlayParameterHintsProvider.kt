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

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus

object InlayParameterHintsExtension: LanguageExtension<InlayParameterHintsProvider>("com.intellij.parameterHintsProvider")

@ApiStatus.Experimental
interface InlayParameterHintsProvider {

  /**
   * Hints for params to be shown
   */
  fun getParameterHints(element: PsiElement): List<InlayInfo> = emptyList()

  /**
   * Provides fully qualified method name (e.g. "java.util.Map.put") and list of it's parameter names.
   * Used when adding method to blacklist, when user invokes alt-enter on hint 
   * and selects "Do not show for this method".
   */
  fun getMethodInfo(element: PsiElement): MethodInfo? = null

  /**
   * Language used when saving blacklist methods
   * Maybe will be moved to MethodInfo
   */
  val language: Language

  /**
   * Default list of methods for which hints should not be shown
   */
  val defaultBlackList: Set<String>
  
}

