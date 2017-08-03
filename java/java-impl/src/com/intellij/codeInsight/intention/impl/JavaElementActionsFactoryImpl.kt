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

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.quickfix.AddConstructorFix
import com.intellij.codeInsight.daemon.impl.quickfix.ModifierFix
import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.*
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.lang.jvm.actions.MemberRequest
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.impl.beanProperties.CreateJavaBeanPropertyFix
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase

class JavaElementActionsFactoryImpl(
  private val materializer: JavaElementMaterializer,
  private val renderer: JavaElementRenderer
) : JvmElementActionsFactory() {

  override fun createChangeModifierActions(target: JvmModifiersOwner, request: MemberRequest.Modifier): List<IntentionAction> = with(
    request) {
    val declaration = target as PsiModifierListOwner
    listOf(ModifierFix(declaration.modifierList, renderer.render(modifier), shouldPresent, false))
  }

  override fun createAddConstructorActions(targetClass: JvmClass, request: MemberRequest.Constructor): List<IntentionAction> =
    with(request) {
      val targetClass = materializer.materialize(targetClass)
      val factory = JVMElementFactories.getFactory(targetClass.language, targetClass.project)!!
      listOf(AddConstructorFix(targetClass, request.parameters.mapIndexed { i, it ->
        factory.createParameter(it.name ?: "arg$i", materializer.materialize(it.type), targetClass)
      }))
    }

  override fun createAddMethodActions(targetClass: JvmClass, request: MemberRequest.Method): List<IntentionAction> =
    with(request) {
      createAddMethodAction(targetClass, request)
        ?.let { listOf(it) } ?: emptyList()
    }

  override fun createAddPropertyActions(targetClass: JvmClass, request: MemberRequest.Property): List<IntentionAction> {
    with(request) {
      val psiClass = materializer.materialize(targetClass)
      val propertyType = materializer.materialize(propertyType)
      if (getterRequired && setterRequired)
        return listOf<IntentionAction>(
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    true),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    false))
      if (getterRequired || setterRequired)
        return listOf<IntentionAction>(
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    true),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired,
                                    false),
          CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, true, true, true))

      return listOf<IntentionAction>(
        CreateJavaBeanPropertyFix(psiClass, propertyName, propertyType, getterRequired, setterRequired, true))
    }
  }


  private fun createAddMethodAction(psiClass: JvmClass, request: MemberRequest.Method): IntentionAction? {
    with(request) {
      val psiClass = materializer.materialize(psiClass)
      val signatureString = with(renderer) {
        val paramsString = parameters.mapIndexed { i, t -> "${render(t.type)} ${t.name ?: "arg$i"}" }.joinToString()
        val modifiersString = (annotations.map { render(it) } + modifiers.map { render(it) }).joinToString(" ")
        "${modifiersString} ${render(returnType)} $name($paramsString){}"
      }
      val targetClassPointer = SmartPointerManager.getInstance(psiClass.project).createSmartPsiElementPointer(psiClass)
      return object : AbstractIntentionAction() {

        private val text = targetClassPointer.element?.let { psiClass ->
          QuickFixBundle.message("create.method.from.usage.text",
                                 PsiFormatUtil.formatMethod(createMethod(psiClass), PsiSubstitutor.EMPTY,
                                                            PsiFormatUtilBase.SHOW_NAME or
                                                              PsiFormatUtilBase.SHOW_TYPE or
                                                              PsiFormatUtilBase.SHOW_PARAMETERS or
                                                              PsiFormatUtilBase.SHOW_RAW_TYPE,
                                                            PsiFormatUtilBase.SHOW_TYPE or PsiFormatUtilBase.SHOW_RAW_TYPE, 2))
        } ?: ""

        override fun getText(): String = text

        override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
          val targetClass = targetClassPointer.element ?: return
          runWriteAction {
            val addedMethod = targetClass.add(createMethod(targetClass))
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(addedMethod)
          }
        }

        private fun createMethod(targetClass: PsiClass): PsiMethod {
          val elementFactory = JVMElementFactories.getFactory(targetClass.language, targetClass.project) // it could be Groovy
                               ?: JavaPsiFacade.getElementFactory(targetClass.project)
          return elementFactory.createMethodFromText(signatureString, targetClass)
        }
      }
    }
  }

}

class JavaElementRenderer {
  companion object {
    @JvmStatic
    fun getInstance(): JavaElementRenderer {
      return ServiceManager.getService(JavaElementRenderer::class.java)
    }
  }


  fun render(visibilityModifiers: List<JvmModifier>): String =
    visibilityModifiers.joinToString(" ") { render(it) }

  fun render(jvmType: JvmType): String =
    (jvmType as PsiType).canonicalText

  fun render(jvmAnnotation: JvmAnnotation): String =
    "@" + (jvmAnnotation as PsiAnnotation).qualifiedName!!

  @PsiModifier.ModifierConstant
  fun render(modifier: JvmModifier): String = when (modifier) {
    JvmModifier.PUBLIC -> PsiModifier.PUBLIC
    JvmModifier.PROTECTED -> PsiModifier.PROTECTED
    JvmModifier.PRIVATE -> PsiModifier.PRIVATE
    JvmModifier.PACKAGE_LOCAL -> ""
    JvmModifier.STATIC -> PsiModifier.STATIC
    JvmModifier.ABSTRACT -> PsiModifier.ABSTRACT
    JvmModifier.FINAL -> PsiModifier.FINAL
    JvmModifier.DEFAULT -> PsiModifier.DEFAULT
    JvmModifier.NATIVE -> PsiModifier.NATIVE
    JvmModifier.SYNCHRONIZED -> PsiModifier.NATIVE
    JvmModifier.STRICTFP -> PsiModifier.STRICTFP
    JvmModifier.TRANSIENT -> PsiModifier.TRANSIENT
    JvmModifier.VOLATILE -> PsiModifier.VOLATILE
    JvmModifier.TRANSITIVE -> PsiModifier.TRANSITIVE
  }

}

class JavaElementMaterializer {

  companion object {
    @JvmStatic
    fun getInstance(): JavaElementMaterializer {
      return ServiceManager.getService(JavaElementMaterializer::class.java)
    }
  }


  fun materialize(jvmType: JvmType): PsiType {
    return jvmType as PsiType //TODO:probably it could be not so easy sometimes
  }

  fun materialize(jvmClass: JvmClass): PsiClass {
    return jvmClass as PsiClass //TODO:probably it could be not so easy sometimes
  }

  fun materialize(jvmParameter: JvmParameter): PsiParameter {
    return jvmParameter as PsiParameter //TODO:probably it could be not so easy sometimes
  }

  fun materialize(jvmTypeParameter: JvmTypeParameter): PsiTypeParameter {
    return jvmTypeParameter as PsiTypeParameter //TODO:probably it could be not so easy sometimes
  }

}