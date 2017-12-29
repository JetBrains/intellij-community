// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JvmPsiConversionHelper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.uast.*
import com.intellij.codeInsight.intention.JvmCommonIntentionActionsFactory as UastJvmCommonIntentionActionsFactory
import com.intellij.codeInsight.intention.MethodInsertionInfo as UastMethodInsertionInfo

@Deprecated("to be removed in 2018.1")
class UastJvmElementFactory(val renderer: JavaElementRenderer) : JvmElementActionsFactory() {

  private fun getUastFactory(target: JvmModifiersOwner) = (target as? PsiElement)?.language?.let {
    UastJvmCommonIntentionActionsFactory.forLanguage(it)
  }

  override fun createChangeModifierActions(target: JvmModifiersOwner, request: MemberRequest.Modifier): List<IntentionAction> =
    with(request) {
      listOfNotNull(
        getUastFactory(target)?.createChangeModifierAction(target.asUast<UDeclaration>(), renderer.render(modifier), shouldPresent))
    }

  override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
    val project = (targetClass as? PsiElement)?.project ?: return emptyList()
    return with(request) {
      getUastFactory(targetClass)?.createAddCallableMemberActions(
        UastMethodInsertionInfo.Constructor(
          targetClass.asUast(),
          modifiers.map { renderer.render(it) },
          emptyList(),
          parametersAsUParameters(project, parameters)
        )
      ) ?: emptyList()
    }
  }

  private fun parametersAsUParameters(project: Project, parameters: ExpectedParameters): List<UParameter> {
    val helper = JvmPsiConversionHelper.getInstance(project)
    val factory = JavaPsiFacade.getElementFactory(project)
    return parameters.mapIndexed { i, pair ->
      factory.createParameter(
        pair.first.names.firstOrNull() ?: "arg$i",
        pair.second.firstOrNull()?.theType?.let(helper::convertType)
        ?: PsiType.getTypeByName("java.lang.Object", project, GlobalSearchScope.allScope(project))
      ).asUast<UParameter>()
    }
  }

  override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
    val project = (targetClass as? PsiElement)?.project ?: return emptyList()
    val helper = JvmPsiConversionHelper.getInstance(project)
    return with(request) {
      getUastFactory(targetClass)?.createAddCallableMemberActions(
        UastMethodInsertionInfo.Method(
          targetClass.asUast(),
          request.methodName,
          modifiers.map { renderer.render(it) },
          emptyList(),
          returnType.firstOrNull()?.theType?.let(helper::convertType) ?: PsiType.VOID,
          parametersAsUParameters(project, parameters),
          modifiers.contains(JvmModifier.ABSTRACT)
        )
      ) ?: emptyList()
    }
  }


  override fun createAddPropertyActions(targetClass: JvmClass, request: MemberRequest.Property): List<IntentionAction> {
    val project = (targetClass as? PsiElement)?.project ?: return emptyList()
    val helper = JvmPsiConversionHelper.getInstance(project)
    return with(request) {
      getUastFactory(targetClass)?.createAddBeanPropertyActions(targetClass.asUast<UClass>(), propertyName,
                                                                renderer.render(visibilityModifier),
                                                                helper.convertType(propertyType), setterRequired,
                                                                getterRequired) ?: emptyList()

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