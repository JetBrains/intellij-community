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
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.Language
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.lang.jvm.actions.JvmElementActionsFactoryFallback
import com.intellij.lang.jvm.actions.MemberRequest
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import org.jetbrains.uast.*
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory as UastJvmCommonIntentionActionsFactory
import com.intellij.codeInsight.intention.MethodInsertionInfo as UastMethodInsertionInfo

@Deprecated("to be removed in 2017.3", ReplaceWith("use com.intellij.lang.jvm.actions.JvmElementActionsFactory"))
class ElementActionsFactoryUastFallback(
  val renderer: JavaJvmElementRenderer,
  val materializer: JavaJvmElementMaterializer
) : JvmElementActionsFactoryFallback {
  override fun forLanguage(lang: Language): JvmElementActionsFactory? {
    val factory = UastJvmCommonIntentionActionsFactory.forLanguage(lang) ?: return null
    return object : JvmElementActionsFactory() {

      override fun createChangeJvmModifierAction(declaration: JvmModifiersOwner,
                                                 modifier: JvmModifier,
                                                 shouldPresent: Boolean): IntentionAction? =
        factory.createChangeModifierAction(declaration.asUast<UDeclaration>(), renderer.render(modifier), shouldPresent)


      override fun createAddCallableMemberActions(info: MemberRequest): List<IntentionAction> {
        val uInfo = when (info) {
          is MemberRequest.Method ->
            UastMethodInsertionInfo.Method(
              info.targetClass.asUast(),
              info.name,
              info.modifiers.map { renderer.render(it) },
              info.typeParameters.map { materializer.materialize(it) },
              materializer.materialize(info.returnType),
              info.parameters.map { it.asUast<UParameter>() },
              info.isAbstract
            )
          is MemberRequest.Constructor -> UastMethodInsertionInfo.Constructor(
            info.targetClass.asUast(),
            info.modifiers.map { renderer.render(it) },
            info.typeParameters.map { materializer.materialize(it) },
            info.parameters.map { it.asUast<UParameter>() }
          )
        }
        return factory.createAddCallableMemberActions(uInfo)
      }

      override fun createAddJvmPropertyActions(psiClass: JvmClass,
                                               propertyName: String,
                                               visibilityModifier: JvmModifier,
                                               propertyType: JvmType,
                                               setterRequired: Boolean,
                                               getterRequired: Boolean): List<IntentionAction> =
        factory.createAddBeanPropertyActions(psiClass.asUast<UClass>(), propertyName,
                                             renderer.render(visibilityModifier),
                                             materializer.materialize(propertyType), setterRequired, getterRequired)

    }
  }
}

@Deprecated("remove after kotlin plugin will be ported")
private inline fun <reified T : UElement> JvmModifiersOwner.asUast(): T = when (this) {
  is T -> this
  is PsiElement -> this.let {
    ServiceManager.getService(project, UastContext::class.java)
      .convertElement(this, null, T::class.java) as T?
  }
                   ?: throw UnsupportedOperationException("cant convert $this to ${T::class}")
  else -> throw UnsupportedOperationException("cant convert $this to ${T::class}")
}