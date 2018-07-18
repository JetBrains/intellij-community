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
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext
import com.intellij.compiler.chainsSearch.context.ChainSearchTarget
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.util.PsiUtil
import org.jetbrains.jps.backwardRefs.LightRef
import org.jetbrains.jps.backwardRefs.SignatureData

sealed class RefChainOperation {
  abstract val qualifierRawName: String

  abstract val qualifierDef: LightRef.LightClassHierarchyElementDef

  abstract val lightRef: LightRef
}

class TypeCast(override val lightRef: LightRef.LightClassHierarchyElementDef,
               val castTypeRef: LightRef.LightClassHierarchyElementDef,
               refService: CompilerReferenceServiceEx): RefChainOperation() {
  override val qualifierRawName: String
    get() = operandName.value
  override val qualifierDef: LightRef.LightClassHierarchyElementDef
    get() = lightRef

  private val operandName = lazy(LazyThreadSafetyMode.NONE) {
    refService.getName(lightRef.name)
  }
}

class MethodCall(override val lightRef: LightRef.JavaLightMethodRef,
                 private val signatureData: SignatureData,
                 private val context: ChainCompletionContext): RefChainOperation() {

  private companion object {
    val CONSTRUCTOR_METHOD_NAME = "<init>"
  }

  override val qualifierRawName: String
    get() = owner.value
  override val qualifierDef: LightRef.LightClassHierarchyElementDef
    get() = lightRef.owner

  private val name = lazy(LazyThreadSafetyMode.NONE) {
    context.refService.getName(lightRef.name)
  }

  private val owner = lazy(LazyThreadSafetyMode.NONE) {
    context.refService.getName(lightRef.owner.name)
  }

  private val rawReturnType = lazy(LazyThreadSafetyMode.NONE) {
    context.refService.getName(signatureData.rawReturnType)
  }

  val isStatic: Boolean
    get() = signatureData.isStatic

  fun resolve(): Array<PsiMethod> {
    if (CONSTRUCTOR_METHOD_NAME == name.value) {
      return PsiMethod.EMPTY_ARRAY
    }
    val aClass = context.resolvePsiClass(qualifierDef) ?: return PsiMethod.EMPTY_ARRAY
    return aClass.findMethodsByName(name.value, true)
      .filter { it.hasModifierProperty(PsiModifier.STATIC) == isStatic }
      .filter { !it.isDeprecated }
      .filter { context.accessValidator().test(it) }
      .filter {
        val returnType = it.returnType
        when (signatureData.iteratorKind) {
          SignatureData.ARRAY_ONE_DIM -> {
            when (returnType) {
              is PsiArrayType -> {
                val componentType = returnType.componentType
                componentType is PsiClassType && componentType.resolve()?.qualifiedName == rawReturnType.value
              }
              else -> false
            }
          }
          SignatureData.ITERATOR_ONE_DIM -> {
            val iteratorKind = ChainSearchTarget.getIteratorKind(PsiUtil.resolveClassInClassTypeOnly(returnType))
            when {
              iteratorKind != null -> PsiUtil.resolveClassInClassTypeOnly(PsiUtil.substituteTypeParameter(returnType, iteratorKind, 0, false))?.qualifiedName == rawReturnType.value
              else -> false
            }
          }
          SignatureData.ZERO_DIM -> returnType is PsiClassType && returnType.resolve()?.qualifiedName == rawReturnType.value
          else -> throw IllegalStateException("kind is unsupported ${signatureData.iteratorKind}")
        }
      }
      .sortedBy({ it.parameterList.parametersCount })
      .toTypedArray()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other?.javaClass != javaClass) return false

    other as MethodCall

    if (lightRef.owner != other.lightRef.owner) return false
    if (lightRef.name != other.lightRef.name) return false
    if (signatureData != other.signatureData) return false

    return true
  }

  override fun hashCode(): Int {
    var result = lightRef.owner.hashCode()
    result = 31 * result + lightRef.name.hashCode()
    result = 31 * result + signatureData.hashCode()
    return result
  }

  override fun toString(): String {
    return qualifierRawName + (if (isStatic) "." else "#") + name + "(" + lightRef.parameterCount + ")"
  }
}
