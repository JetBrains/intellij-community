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

import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

interface CreateClassRequest {

  /**
   * Element in language from which the action is invoked.
   * Should be used for obtaining resolve scope, project, etc.
   */
  val requestorContext: PsiElement

  /**
   * Directory where the source should be generated.
   * This directory is selected by user.
   */
  val targetDirectory: PsiDirectory

  /**
   * Selected by user.
   */
  val classKind: SourceClassKind

  /**
   * Name of required class.
   */
  val className: String

  /**
   * Fully qualified name of a supertype.
   * Should be used for setting proper extends/implements type.
   */
  val superTypeFqn: String?

  /**
   * Types of arguments if invoked from unresolved constructor call.
   * Used in generating proper constructor.
   */
  val argumentTypes: List<PsiType?>

  /**
   * Type arguments.
   * Used for generating generics in required class.
   */
  val typeArguments: List<PsiType?>
}
