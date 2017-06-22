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
package com.intellij.jvm.createMember.java

import com.intellij.icons.AllIcons
import com.intellij.jvm.JvmClass
import com.intellij.jvm.createMember.CreateJvmMemberFactory
import com.intellij.jvm.createMember.CreateJvmMethodRequest
import com.intellij.jvm.createMember.CreateMemberAction
import com.intellij.jvm.createMember.CreateMemberRequest
import com.intellij.lang.java.JavaLanguage
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.impl.compiled.ClsClassImpl
import javax.swing.Icon

class CreateJavaMethodFactory : CreateJvmMemberFactory {

  override fun getActions(target: JvmClass, request: CreateMemberRequest, context: PsiElement): Collection<CreateMemberAction> {
    if (request !is CreateJvmMethodRequest) return emptyList()
    val psi = target.psiElement as? PsiClass ?: return emptyList()
    if (psi.language != JavaLanguage.INSTANCE || psi is ClsClassImpl) return emptyList()

    val action = object : CreateJavaMethodAction(request.methodName) {
      override fun renderMember(): PsiElement {
        val factory = JavaPsiFacade.getElementFactory(context.project)
        val method = factory.createMethodFromText(
          "public ${request.returnType ?: "Object"} ${request.methodName} () {}",
          null, LanguageLevel.HIGHEST
        )
        return psi.add(method) as PsiMethod
      }
    }
    return listOf(action)
  }

  abstract class CreateJavaMethodAction(private val methodName: String) : CreateMemberAction {
    override fun getIcon(): Icon? = AllIcons.Nodes.Method
    override fun getTitle(): String = "Create method '${methodName}'"
  }
}