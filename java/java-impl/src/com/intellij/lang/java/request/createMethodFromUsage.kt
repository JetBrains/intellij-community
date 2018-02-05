// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("CreateMethodFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.isValidMethodReference
import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodFromUsageFix.isMethodSignatureExists
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
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
  }.groupActionsByType()
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
