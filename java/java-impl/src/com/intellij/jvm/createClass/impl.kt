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
package com.intellij.jvm.createClass

import com.intellij.jvm.JvmClassKind
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

private val ep = ExtensionPointName.create<CreateJvmClassFactory>("com.intellij.jvm.createClass")

fun getFactories(kinds: Collection<JvmClassKind>, context: PsiElement): Map<SourceClassKind, CreateJvmClassFactory> {
  val languageKindToFactory = mutableMapOf<SourceClassKind, CreateJvmClassFactory>()
  for (factory in ep.extensions) {
    for (languageKind in factory.getSourceKinds(kinds, context)) {
      languageKindToFactory[languageKind] = factory
    }
  }
  return languageKindToFactory
}
