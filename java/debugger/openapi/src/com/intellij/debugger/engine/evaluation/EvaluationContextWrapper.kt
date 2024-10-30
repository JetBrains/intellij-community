// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.sun.jdi.Value
import org.jetbrains.annotations.ApiStatus

/**
 * Language-specific wrapper that can add variables into an evaluation context.
 */
@ApiStatus.Internal
interface EvaluationContextWrapper {
  fun wrapContext(project: Project, context: PsiElement?, additionalElements: List<AdditionalContextElement>): PsiElement?
}

/**
 * Provides additional variables that should be accessible during evaluation.
 */
@ApiStatus.Internal
interface AdditionalContextProvider {
 fun getAdditionalContextElements(project: Project, context: PsiElement?): List<AdditionalContextElement>

 companion object {
   private val EP_NAME = ExtensionPointName.create<AdditionalContextProvider>("com.intellij.debugger.additionalContextProvider")

   @JvmStatic
   fun getAllAdditionalContextElements(project: Project, context: PsiElement?): List<AdditionalContextElement> {
     val result = mutableListOf<AdditionalContextElement>()
     EP_NAME.forEachExtensionSafe {
       result.addAll(it.getAdditionalContextElements(project, context))
     }
     return result
   }
 }
}

/**
 * Represents an additional context element used in the debugger engine evaluation.
 *
 * @property name The name of the context element.
 * @property jvmSignature Object type signature, for example `Ljava/lang/Object;`.
 * @property jvmTypeName Object type name, for example `java.lang.Object`.
 * @property value A lambda function returning a [Value] used for evaluation.
 */
@ApiStatus.Internal
data class AdditionalContextElement(val name: String, val jvmSignature: String, val jvmTypeName: String, val value: (EvaluationContext) -> Value)
