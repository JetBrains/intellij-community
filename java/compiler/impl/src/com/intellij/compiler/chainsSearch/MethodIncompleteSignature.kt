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
package com.intellij.compiler.chainsSearch

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceEx
import com.intellij.compiler.chainsSearch.context.ChainSearchTarget
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiUtil
import org.jetbrains.backwardRefs.LightRef
import org.jetbrains.backwardRefs.SignatureData
import java.util.function.Predicate

class MethodIncompleteSignature(val ref: LightRef.JavaLightMethodRef,
                                private val signatureData: SignatureData,
                                private val refService: CompilerReferenceServiceEx) {
  companion object {
    val CONSTRUCTOR_METHOD_NAME = "<init>"
  }

  val name: String by lazy(LazyThreadSafetyMode.NONE) {
    refService.getName(ref.name)
  }

  val ownerRef = ref.owner

  val owner: String by lazy(LazyThreadSafetyMode.NONE) {
    refService.getName(ref.owner.name)
  }

  val rawReturnType: String by lazy(LazyThreadSafetyMode.NONE) {
    refService.getName(signatureData.rawReturnType)
  }

  val parameterCount: Int
    get() = ref.parameterCount

  val isStatic: Boolean
    get() = signatureData.isStatic

  fun resolveQualifier(project: Project,
                       resolveScope: GlobalSearchScope,
                       accessValidator: Predicate<PsiMember>): PsiClass? {
    val clazz = JavaPsiFacade.getInstance(project).findClass(owner, resolveScope)
    return if (clazz != null && accessValidator.test(clazz)) clazz else null
  }

  fun resolve(project: Project,
              resolveScope: GlobalSearchScope,
              accessValidator: Predicate<PsiMember>): Array<PsiMethod> {
    if (CONSTRUCTOR_METHOD_NAME == name) {
      return PsiMethod.EMPTY_ARRAY
    }
    val aClass = resolveQualifier(project, resolveScope, accessValidator) ?: return PsiMethod.EMPTY_ARRAY
    return aClass.findMethodsByName(name, true)
      .filter { it.hasModifierProperty(PsiModifier.STATIC) == isStatic }
      .filter { !it.isDeprecated }
      .filter { accessValidator.test(it) }
      .filter {
        val returnType = it.returnType
        when (signatureData.iteratorKind) {
          SignatureData.ARRAY_ONE_DIM -> {
            when (returnType) {
              is PsiArrayType -> {
                val componentType = returnType.componentType
                componentType is PsiClassType && componentType.resolve()?.qualifiedName == rawReturnType
              }
              else -> false
            }
          }
          SignatureData.ITERATOR_ONE_DIM -> {
            val iteratorKind = ChainSearchTarget.getIteratorKind(PsiUtil.resolveClassInClassTypeOnly(returnType))
            when {
              iteratorKind != null -> PsiUtil.resolveClassInClassTypeOnly(PsiUtil.substituteTypeParameter(returnType, iteratorKind, 0, false))?.qualifiedName == rawReturnType
              else -> false
            }
          }
          SignatureData.ZERO_DIM -> returnType is PsiClassType && returnType.resolve()?.qualifiedName == rawReturnType
          else -> throw IllegalStateException("kind is unsupported ${signatureData.iteratorKind}")
        }
      }
      .sortedBy({ it.parameterList.parametersCount })
      .toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false

    other as MethodIncompleteSignature

    if (ref.owner != other.ref.owner) return false
    if (ref.name != other.ref.name) return false
    if (signatureData != other.signatureData) return false

    return true
  }

  override fun hashCode(): Int {
    var result = ref.owner.hashCode()
    result = 31 * result + ref.name.hashCode()
    result = 31 * result + signatureData.hashCode()
    return result
  }

  override fun toString(): String {
    return owner + (if (isStatic) "" else "#") + name + "(" + parameterCount + ")"
  }
}