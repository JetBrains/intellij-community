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
@file:JvmName("CreateFieldFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.java.actions.toJavaClassOrNull
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly
import com.intellij.psi.util.parentOfType

fun generateActions(ref: PsiReferenceExpression): List<IntentionAction> {
  if (ref.referenceName == null) return emptyList()
  val fieldRequests = CreateFieldRequests(ref).collectRequests()
  val extensions = EP_NAME.extensions
  return fieldRequests.flatMap { (clazz, request) ->
    extensions.flatMap { ext ->
      ext.createAddFieldActions(clazz, request)
    }
  }
}

private class CreateFieldRequests(val myRef: PsiReferenceExpression) {

  private val requests = LinkedHashMap<JvmClass, CreateFieldRequest>()

  fun collectRequests(): Map<JvmClass, CreateFieldRequest> {
    doCollectRequests()
    return requests
  }

  private fun doCollectRequests() {
    val qualifier = myRef.qualifierExpression

    if (qualifier != null) {
      val instanceClass = resolveClassInClassTypeOnly(qualifier.type)
      if (instanceClass != null) {
        processHierarchy(instanceClass)
      }
      else {
        val staticClass = (qualifier as? PsiJavaCodeReferenceElement)?.resolve() as? PsiClass
        if (staticClass != null) {
          processClass(staticClass, true)
        }
      }
    }
    else {
      val baseClass = extractBaseClassFromSwitchStatement()
      if (baseClass != null) {
        processHierarchy(baseClass)
      }
      else {
        processOuterAndImported()
      }
    }
  }

  private fun extractBaseClassFromSwitchStatement(): PsiClass? {
    val parent = myRef.parent as? PsiSwitchLabelStatement ?: return null
    val switchStatement = parent.parentOfType<PsiSwitchStatement>() ?: return null
    return resolveClassInClassTypeOnly(switchStatement.expression?.type)
  }

  private fun processHierarchy(baseClass: PsiClass) {
    for (clazz in hierarchy(baseClass)) {
      processClass(clazz, false)
    }
  }

  private fun processOuterAndImported() {
    val inStaticContext = myRef.isInStaticContext()
    for (outerClass in collectOuterClasses(myRef)) {
      processClass(outerClass, inStaticContext)
    }
    for (imported in collectOnDemandImported(myRef)) {
      processClass(imported, true)
    }
  }

  private fun processClass(target: JvmClass, staticContext: Boolean) {
    if (!staticContext && target.classKind in STATIC_ONLY) return
    val modifiers = mutableSetOf<JvmModifier>()

    if (staticContext) {
      modifiers += JvmModifier.STATIC
    }

    if (shouldCreateFinalField(myRef, target)) {
      modifiers += JvmModifier.FINAL
    }

    val ownerClass = myRef.parentOfType<PsiClass>()
    val visibility = computeVisibility(myRef.project, ownerClass, target)
    if (visibility != null) {
      modifiers += visibility
    }

    val request = CreateFieldFromJavaUsageRequest(
      modifiers = modifiers,
      reference = myRef,
      useAnchor = target.toJavaClassOrNull() == ownerClass,
      constant = false
    )
    requests[target] = request
  }
}

private val STATIC_ONLY = arrayOf(JvmClassKind.INTERFACE, JvmClassKind.ANNOTATION)

/**
 * Given unresolved unqualified reference,
 * this reference could be resolved into static member if some class which has it's members imported.
 *
 * @return list of classes from static on demand imports.
 */
private fun collectOnDemandImported(place: PsiElement): List<JvmClass> {
  val containingFile = place.containingFile as? PsiJavaFile ?: return emptyList()
  val importList = containingFile.importList ?: return emptyList()

  val onDemandImports = importList.importStaticStatements.filter { it.isOnDemand }
  if (onDemandImports.isEmpty()) return emptyList()
  return onDemandImports.mapNotNull { it.resolveTargetClass() }
}

private fun shouldCreateFinalField(ref: PsiReferenceExpression, targetClass: JvmClass): Boolean {
  val javaClass = targetClass.toJavaClassOrNull() ?: return false
  return CreateFieldFromUsageFix.shouldCreateFinalMember(ref, javaClass)
}
