// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("CreateFieldFromUsage")

package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFieldFromUsageFix
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.java.actions.toJavaClassOrNull
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.EP_NAME
import com.intellij.lang.jvm.actions.groupActionsByType
import com.intellij.psi.*
import com.intellij.psi.impl.PsiImplUtil
import com.intellij.psi.util.*
import com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly

fun generateActions(ref: PsiReferenceExpression): List<IntentionAction> {
  if (!checkReference(ref)) return emptyList()
  val fieldRequests = CreateFieldRequests(ref).collectRequests()
  val extensions = EP_NAME.extensionList
  return fieldRequests.flatMap { (clazz, request) ->
    extensions.flatMap { ext ->
      ext.createAddFieldActions(clazz, request)
    }
  }.groupActionsByType(JavaLanguage.INSTANCE)
}

private fun checkReference(ref: PsiReferenceExpression): Boolean {
  if (ref.referenceName == null) return false
  if (ref.parent is PsiMethodCallExpression) return false
  return true
}

private class CreateFieldRequests(val myRef: PsiReferenceExpression) {

  private val requests = LinkedHashMap<JvmClass, CreateFieldRequest>()

  fun collectRequests(): Map<JvmClass, CreateFieldRequest> {
    doCollectRequests()
    return requests
  }

  private fun addRequest(target: JvmClass, request: CreateFieldRequest) {
    if (target is PsiElement) {
      PsiUtilCore.ensureValid(target)
    }
    requests[target] = request
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
      val baseClass = resolveClassInClassTypeOnly(PsiImplUtil.getSwitchLabel(myRef)?.enclosingSwitchBlock?.expression?.type)
      if (baseClass != null) {
        processHierarchy(baseClass)
      }
      else {
        processOuterAndImported()
      }
    }
  }

  private fun processHierarchy(baseClass: PsiClass) {
    for (clazz in hierarchy(baseClass)) {
      processClass(clazz, false)
    }
  }

  private fun processOuterAndImported() {
    val parent = myRef.parentOfTypes(PsiMember::class, PsiAnnotation::class)
    // unresolved writable reference inside annotation: creating field will not help
    if (parent is PsiAnnotation && PsiUtil.isAccessedForWriting(myRef)) return
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
      useAnchor = ownerClass != null && PsiTreeUtil.isAncestor(target.toJavaClassOrNull(), ownerClass, false),
      isConstant = shouldCreateConstant(myRef)
    )
    addRequest(target, request)
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
private fun shouldCreateConstant(ref: PsiReferenceExpression): Boolean {
  val parent = PsiTreeUtil.skipParentsOfType(ref, PsiExpression::class.java)
  return parent is PsiNameValuePair || parent is PsiCaseLabelElementList || parent is PsiAnnotationMethod;
}