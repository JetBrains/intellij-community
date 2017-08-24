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
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.psi.util.PsiUtil.resolveClassInClassTypeOnly
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.VisibilityUtil
import java.util.*
import kotlin.collections.LinkedHashSet

fun generateActions(ref: PsiReferenceExpression): List<IntentionAction> {
  ref.referenceName ?: return emptyList()
  val extensions = EP_NAME.extensions
  return generateRequests(ref).flatMap { (clazz, request) ->
    extensions.flatMap { ext ->
      ext.createAddFieldActions(clazz, request)
    }
  }
}

private fun generateRequests(ref: PsiReferenceExpression): List<Pair<JvmClass, CreateFieldRequest>> {
  val (instanceContext, staticContext) = collectTargets(ref) ?: return emptyList()

  val instanceFieldRequests = instanceContext.filter {
    it.classKind !in STATIC_ONLY
  }.map {
    it to generateRequest(ref, it, false)
  }
  val staticFieldRequests = staticContext.map {
    it to generateRequest(ref, it, true)
  }

  return instanceFieldRequests + staticFieldRequests
}

private val STATIC_ONLY = arrayOf(JvmClassKind.INTERFACE, JvmClassKind.ANNOTATION)

private fun generateRequest(ref: PsiReferenceExpression, target: JvmClass, static: Boolean): CreateFieldRequest {
  val modifiers = mutableSetOf<JvmModifier>()

  if (static) {
    modifiers += JvmModifier.STATIC
  }

  if (shouldCreateFinalField(ref, target)) {
    modifiers += JvmModifier.FINAL
  }

  val ownerClass = getParentOfType(ref, PsiClass::class.java)
  val visibility = computeVisibility(ref.project, ownerClass, target)
  if (visibility != null) {
    modifiers += visibility
  }

  return CreateFieldFromJavaUsageRequest(
    modifiers = modifiers,
    reference = ref,
    useAnchor = target.toJavaClassOrNull() == ownerClass,
    constant = false
  )
}

private typealias Couple<T> = Pair<T, T>

private fun collectTargets(ref: PsiReferenceExpression): Couple<List<JvmClass>>? {
  var baseClass: PsiClass? = null
  var inStaticContext = false
  val qualifier: PsiExpression? = ref.qualifierExpression

  if (qualifier == null) {
    val parent = ref.parent
    if (parent is PsiSwitchLabelStatement) {
      val switchStatement = getParentOfType(parent, PsiSwitchStatement::class.java)
      if (switchStatement != null) {
        baseClass = resolveClassInClassTypeOnly(switchStatement.expression?.type)
      }
    }
    if (baseClass == null) {
      return collectOuterAndImported(ref)
    }
  }
  else {
    baseClass = resolveClassInClassTypeOnly(qualifier.type)
    if (baseClass == null) {
      inStaticContext = true
      baseClass = (qualifier as? PsiJavaCodeReferenceElement)?.resolve() as? PsiClass
    }
  }
  baseClass ?: return null
  if (inStaticContext) {
    return Pair(emptyList(), listOf(baseClass))
  }
  val hierarchy = hierarchy(baseClass).filter { it !is PsiTypeParameter }
  return Pair(hierarchy, emptyList())
}

private fun collectOuterAndImported(place: PsiElement): Couple<List<JvmClass>> {
  val inStaticContext = place.parentOfType<PsiMember>()?.hasModifierProperty(PsiModifier.STATIC) ?: false
  val outerClasses = collectOuterClasses(place)
  val importedClasses = collectOnDemandImported(place)
  return if (inStaticContext) Pair(emptyList(), outerClasses + importedClasses) else Pair(outerClasses, importedClasses)
}

private fun collectOuterClasses(place: PsiElement): List<JvmClass> {
  val result = mutableListOf<PsiClass>()
  for (clazz in place.parentsOfType<PsiClass>()) {
    result.add(clazz)
    if (clazz.hasModifierProperty(PsiModifier.STATIC)) break
  }
  return result
}

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

private fun hierarchy(clazz: PsiClass): Collection<PsiClass> {
  val result = LinkedHashSet<PsiClass>()
  val queue = LinkedList<PsiClass>()
  queue.add(clazz)
  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    if (result.add(current)) {
      queue.addAll(current.supers)
    }
  }
  return result
}

private fun shouldCreateFinalField(ref: PsiReferenceExpression, targetClass: JvmClass): Boolean {
  val javaClass = targetClass.toJavaClassOrNull() ?: return false
  return CreateFieldFromUsageFix.shouldCreateFinalMember(ref, javaClass)
}

private fun computeVisibility(project: Project, ownerClass: PsiClass?, targetClass: JvmClass): JvmModifier? {
  if (targetClass.classKind == JvmClassKind.INTERFACE || targetClass.classKind == JvmClassKind.ANNOTATION) return JvmModifier.PUBLIC
  if (ownerClass != null) {
    targetClass.toJavaClassOrNull()?.let { javaClass ->
      if (javaClass == ownerClass || PsiTreeUtil.isAncestor(javaClass, ownerClass, false)) {
        return JvmModifier.PRIVATE
      }
    }
  }
  val setting = CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings::class.java).VISIBILITY
  if (setting == VisibilityUtil.ESCALATE_VISIBILITY) {
    return null // TODO
  }
  else if (setting == PsiModifier.PACKAGE_LOCAL) {
    return JvmModifier.PACKAGE_LOCAL
  }
  else {
    return JvmModifier.valueOf(setting.toUpperCase())
  }
}
