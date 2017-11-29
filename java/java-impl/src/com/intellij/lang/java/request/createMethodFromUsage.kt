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
@file:JvmName("CreateMethodFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.isValidMethodReference
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.isMethodSignatureExists
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly
import com.intellij.psi.util.parentOfType

fun generateActions(call: PsiMethodCallExpression): List<IntentionAction> {
  if (!checkCall(call)) return emptyList()
  val methodRequests = CreateMethodRequests(call).collectRequests()
  val extensions = EP_NAME.extensions
  return methodRequests.flatMap { (clazz, request) ->
    extensions.flatMap { ext ->
      ext.createAddMethodActions(clazz, request)
    }
  }
}

private fun checkCall(call: PsiMethodCallExpression): Boolean {
  val ref = call.methodExpression
  if (ref.referenceName == null) return false
  if (isValidMethodReference(ref, call)) return false
  return true
}

private class CreateMethodRequests(val myCall: PsiMethodCallExpression) {

  private val myRequests = LinkedHashMap<JvmClass, CreateMethodRequest>()

  fun collectRequests(): Map<JvmClass, CreateMethodRequest> {
    doCollectRequests()
    return myRequests
  }

  private fun doCollectRequests() {
    val qualifier = myCall.methodExpression.qualifierExpression
    if (qualifier != null) {
      val instanceClass = resolveClassInClassTypeOnly(qualifier.type)
      if (instanceClass != null) {
        for (clazz in hierarchy(instanceClass)) {
          processClass(clazz, false)
        }
      }
      else {
        val staticClass = (qualifier as? PsiJavaCodeReferenceElement)?.resolve() as? PsiClass
        if (staticClass != null) {
          processClass(staticClass, true)
        }
      }
    }
    else {
      val inStaticContext = myCall.isInStaticContext()
      for (outerClass in collectOuterClasses(myCall)) {
        processClass(outerClass, inStaticContext)
      }
    }
  }

  private fun processClass(clazz: PsiClass, staticContext: Boolean) {
    if (isMethodSignatureExists(myCall, clazz)) return // TODO generic check
    val visibility = computeVisibility(myCall.project, myCall.parentOfType(), clazz)
    val modifiers = mutableSetOf<JvmModifier>()
    if (staticContext) modifiers += JvmModifier.STATIC
    if (visibility != null) modifiers += visibility
    myRequests[clazz] = CreateMethodFromJavaUsageRequest(myCall, modifiers)
  }
}

