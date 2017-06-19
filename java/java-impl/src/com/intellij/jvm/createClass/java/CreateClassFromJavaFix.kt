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

import com.intellij.jvm.createClass.api.CreateClassRequest
import com.intellij.jvm.createClass.api.JvmClassKind
import com.intellij.jvm.createClass.impl.CreateJvmClassFix
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiUtil

class CreateClassFromJavaFix(ref: PsiJavaCodeReferenceElement)
  : CreateJvmClassFix<PsiJavaCodeReferenceElement>(ref.project, ref.referenceName!!) {

  private val myRefPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(ref)

  override val reference: PsiJavaCodeReferenceElement? get() = myRefPointer.element

  override fun createRequests(reference: PsiJavaCodeReferenceElement): List<CreateClassRequest> {
    val level5OrHigher = PsiUtil.isLanguageLevel5OrHigher(reference)
    val parent = reference.parent
    if (parent is PsiAnnotation) {
      return if (level5OrHigher) listOf(CreateClassFromJavaRequest(JvmClassKind.ANNOTATION, reference)) else emptyList()
    }
//    if (parent is PsiReferenceList) {
//      if (myKind == CreateClassKind.ENUM) return false
//      if (parent.getParent() is PsiClass) {
//        val psiClass = parent.getParent() as PsiClass
//        if (psiClass.extendsList === parent) {
//          if (myKind == CreateClassKind.CLASS && !psiClass.isInterface) return true
//          if (myKind == CreateClassKind.INTERFACE && psiClass.isInterface) return true
//        }
//        if (psiClass.implementsList === parent && myKind == CreateClassKind.INTERFACE) return true
//      }
//      else if (parent.getParent() is PsiMethod) {
//        val method = parent.getParent() as PsiMethod
//        if (method.throwsList === parent && myKind == CreateClassKind.CLASS) return true
//      }
//    }

    val requests = mutableListOf<CreateClassRequest>()
    requests += CreateClassFromJavaRequest(JvmClassKind.INTERFACE, reference)
    if (level5OrHigher) {
      requests += CreateClassFromJavaRequest(JvmClassKind.ENUM, reference)
      requests += CreateClassFromJavaRequest(JvmClassKind.ANNOTATION, reference)
    }
    return requests
  }
}