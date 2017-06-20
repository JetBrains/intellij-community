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

class CreateClassRequestImpl(
  override val requestorContext: PsiElement,
  override val targetDirectory: PsiDirectory,
  override val classKind: SourceClassKind,
  override val className: String,
  override val superTypeFqn: String? = null,
  override val argumentTypes: List<PsiType?> = emptyList(),
  override val typeArguments: List<PsiType?> = emptyList()
) : CreateClassRequest
