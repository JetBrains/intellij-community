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
package com.intellij.jvm.createClass.java

import com.intellij.jvm.JvmClassKind
import com.intellij.jvm.createClass.CreateClassRequest
import com.intellij.jvm.createClass.CreateClassRequestImpl
import com.intellij.jvm.createClass.fix.CreateJvmClassFix
import com.intellij.jvm.createClass.ui.CreateClassUserInfo
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiUtil.isLanguageLevel5OrHigher
import java.util.*

class CreateClassFromJavaFix(ref: PsiJavaCodeReferenceElement) : CreateJvmClassFix<PsiJavaCodeReferenceElement>() {

  private val myRefPointer = SmartPointerManager.getInstance(ref.project).createSmartPsiElementPointer(ref)

  override val reference: PsiJavaCodeReferenceElement? get() = myRefPointer.element

  override fun getClassName(reference: PsiJavaCodeReferenceElement): String? = reference.referenceName

  override fun getJvmKinds(reference: PsiJavaCodeReferenceElement): Set<JvmClassKind> {
    val level5OrHigher = isLanguageLevel5OrHigher(reference)
    val parent = reference.parent
    if (parent is PsiAnnotation) {
      return if (level5OrHigher) setOf(JvmClassKind.ANNOTATION) else emptySet()
    }
    val set = EnumSet.allOf(JvmClassKind::class.java)
    if (!level5OrHigher) {
      set.remove(JvmClassKind.ENUM)
      set.remove(JvmClassKind.ANNOTATION)
    }
    return set
  }

  override fun createRequest(reference: PsiJavaCodeReferenceElement, userInfo: CreateClassUserInfo): CreateClassRequest {
    return CreateClassRequestImpl(
      reference,
      userInfo.targetDirectory,
      userInfo.classKind,
      userInfo.className
    )
  }
}