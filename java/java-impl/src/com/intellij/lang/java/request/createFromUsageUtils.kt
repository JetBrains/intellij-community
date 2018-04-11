// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.*
import com.intellij.util.VisibilityUtil
import java.util.*
import kotlin.collections.ArrayList

internal fun PsiExpression.isInStaticContext(): Boolean {
  return isWithinStaticMember() || isWithinConstructorCall()
}

internal fun PsiExpression.isWithinStaticMember(): Boolean {
  return parentOfType<PsiMember>()?.hasModifierProperty(PsiModifier.STATIC) ?: false
}

//usages inside delegating constructor call
internal fun PsiExpression.isWithinConstructorCall(): Boolean {
  val owner = parentOfType<PsiModifierListOwner>() as? PsiMethod ?: return false
  if (!owner.isConstructor) return false

  val parent = parents().firstOrNull { it !is PsiExpression } as? PsiExpressionList ?: return false
  val grandParent = parent.parent as? PsiMethodCallExpression ?: return false

  val calleText = grandParent.methodExpression.text
  return calleText == PsiKeyword.SUPER || calleText == PsiKeyword.THIS
}

internal fun computeVisibility(project: Project, ownerClass: PsiClass?, targetClass: JvmClass): JvmModifier? {
  if (targetClass.classKind == JvmClassKind.INTERFACE || targetClass.classKind == JvmClassKind.ANNOTATION) return JvmModifier.PUBLIC
  if (ownerClass != null) {
    (targetClass as? PsiClass)?.let { target ->
      if (target.isEquivalentTo(ownerClass) || PsiTreeUtil.isAncestor(target, ownerClass, false)) {
        return JvmModifier.PRIVATE
      }

      if (InheritanceUtil.isInheritorOrSelf(ownerClass, target, true)) {
        return JvmModifier.PROTECTED
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

internal fun collectOuterClasses(place: PsiElement): List<PsiClass> {
  val result = ArrayList<PsiClass>()
  for (clazz in place.parentsOfType<PsiClass>()) {
    result.add(clazz)
    if (clazz.hasModifierProperty(PsiModifier.STATIC)) break
  }
  return result
}

internal fun hierarchy(clazz: PsiClass): List<PsiClass> { // TODO implementation based on JvmClasses
  val result = LinkedHashSet<PsiClass>()
  val queue = LinkedList<PsiClass>()
  queue.add(clazz)
  while (queue.isNotEmpty()) {
    val current = queue.removeFirst()
    if (result.add(current)) {
      queue.addAll(current.supers)
    }
  }
  return result.filter { it !is PsiTypeParameter }
}
