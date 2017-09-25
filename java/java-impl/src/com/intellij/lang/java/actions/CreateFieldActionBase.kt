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
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.createSmartPointer

abstract class CreateFieldActionBase(targetClass: PsiClass, protected val myRequest: CreateFieldRequest) : BaseIntentionAction() {

  protected val myTargetClass = targetClass.createSmartPointer()

  override fun getFamilyName(): String = message("create.field.from.usage.family")

  override fun getElementToMakeWritable(currentFile: PsiFile): PsiElement? = myTargetClass.element

  override fun startInWriteAction(): Boolean = true
}
