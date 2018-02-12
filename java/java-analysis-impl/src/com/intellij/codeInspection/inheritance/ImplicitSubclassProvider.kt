/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection.inheritance

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.annotations.Nls

import org.jetbrains.annotations.Nls.Capitalization.Sentence

/**
 * Provides information about classes/interfaces that will be implicitly subclassed/implemented at runtime,
 * e.g. by some framework (CGLIB Proxy in Spring).
 *
 * @author Nicolay Mitropolsky
 *
 * @since 2017.2
 */
abstract class ImplicitSubclassProvider {

  /**
   * Checks if this provider could probably provide a subclass for passed psiClass.
   * **Note:** this check is expected to be cheap. If it requires long computations then it is better just to return true.

   * @param psiClass a class to check for possible subclass.
   *
   * @return `false` if definitely no subclass will be created for the psiClass and all further checks could be skipped;
   * `true` if a subclass for the psiClass will probably be created,
   * and then you should call [getSubclassingInfo] to get concrete information about created subclass.
   */
  abstract fun isApplicableTo(psiClass: PsiClass): Boolean


  /**
   * **Note:** assumes that you have called [isApplicableTo] and will not check it again.
   * So you can get wrong results if you haven't check.

   * @param psiClass a class to check for possible subclass.
   *
   * @return  an info about implicitly created subclass, or `null` if given class will not be subclassed.
   */
  abstract fun getSubclassingInfo(psiClass: PsiClass): SubclassingInfo?

  /**
   * Information about implicitly overridden methods.
   * @property description an explanation why this method was overridden.
   * @property isAbstract is overridden method abstract.
   */
  class OverridingInfo @JvmOverloads constructor(@Nls(capitalization = Sentence)
                                                 val description: String,
                                                 val isAbstract: Boolean = false)

  /**
   * Information about implicitly created subclass.
   * @property description an explanation why this class was subclassed.
   * @property isAbstract is created subclass abstract.
   * @property methodsInfo map of methods overridden in class and corresponding [OverridingInfo]s,
   * or `null` if no method-level info is provided
   */
  class SubclassingInfo @JvmOverloads constructor(@Nls(capitalization = Sentence)
                                                  val description: String,
                                                  val methodsInfo: Map<PsiMethod, OverridingInfo>? = null,
                                                  val isAbstract: Boolean = false)


  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<ImplicitSubclassProvider>("com.intellij.codeInsight.implicitSubclassProvider")

    @Deprecated("To be removed in 2018.2", ReplaceWith("ImplicitSubclassProvider.EP_NAME"))
    fun getEP_NAME() = EP_NAME
  }

}
