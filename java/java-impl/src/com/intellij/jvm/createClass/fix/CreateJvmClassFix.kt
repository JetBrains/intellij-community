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
package com.intellij.jvm.createClass.fix

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.jvm.JvmClassKind
import com.intellij.jvm.createClass.CreateJvmClassFactory
import com.intellij.jvm.createClass.SourceClassKind
import com.intellij.jvm.createClass.getFactories
import com.intellij.psi.PsiReference

abstract class CreateJvmClassFix<R : PsiReference> : BaseCreateJvmClassFix<R>(), LowPriorityAction {

  override fun getText(): String = message("create.class.from.usage.another.language")

  override fun getFactories(reference: R) = getLanguageFactories(reference, false)

  protected abstract fun getJvmKinds(reference: R): Collection<JvmClassKind>

  fun register(reference: R, registrar: QuickFixActionRegistrar) {
    for (languageFix in generateLanguageFixes(reference)) {
      registrar.register(languageFix)
    }
    registrar.register(this)
  }

  private fun generateLanguageFixes(reference: R): List<IntentionAction> {
    val ownFactories = getLanguageFactories(reference, true)
    return ownFactories.map {
      CreateParticularJvmClassFix(it.key, it.value, this)
    }
  }

  private fun getLanguageFactories(reference: R, own: Boolean): Map<SourceClassKind, CreateJvmClassFactory> {
    val element = reference.element
    val ownLanguage = element.language
    val allFactories = getFactories(getJvmKinds(reference), element)
    return allFactories.filterKeys { it.language == ownLanguage == own }
  }
}
