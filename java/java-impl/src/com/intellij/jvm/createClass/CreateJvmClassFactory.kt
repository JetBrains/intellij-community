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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException

interface CreateJvmClassFactory {

  /**
   * @return respective source class kinds from which [jvmClassKinds] are generated.
   * Example: Groovy Traits are compiled into interfaces,
   * so Groovy implementation returns Trait Groovy Language Kind and Interface Groovy Language Kind when JVM interface is needed.
   */
  fun getSourceKinds(jvmClassKinds: Collection<JvmClassKind>, context: PsiElement): Collection<SourceClassKind>

  /**
   * Renders a JVM class in source of a particular language.
   *
   * @param request contains all information about needed JVM class
   * @return instance of a [PsiClass] used for binding the reference and navigation
   */
  @Throws(IncorrectOperationException::class)
  fun createClass(request: CreateClassRequest): PsiClass
}
