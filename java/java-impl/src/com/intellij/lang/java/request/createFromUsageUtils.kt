// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.request

import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.*
import java.util.*

internal fun PsiExpression.isInStaticContext(): Boolean {
  return isWithinStaticMember() || isWithinConstructorCall()
}

internal fun PsiExpression.isWithinStaticMemberOf(clazz: PsiClass): Boolean {
  var currentPlace: PsiElement = this
  while (true) {
    val enclosingMember = currentPlace.parentOfType<PsiMember>() ?: return false
    val enclosingClass = enclosingMember.containingClass ?: return false
    if (enclosingClass == clazz) {
      return enclosingMember.hasModifierProperty(PsiModifier.STATIC)
    }
    else {
      currentPlace = enclosingClass.parent ?: return false
    }
  }
}

internal fun PsiExpression.isWithinStaticMember(): Boolean {
  return parentOfType<PsiMember>()?.hasModifierProperty(PsiModifier.STATIC) ?: false
}

//usages inside delegating constructor call
internal fun PsiExpression.isWithinConstructorCall(): Boolean {
  val owner = parentOfType<PsiModifierListOwner>() as? PsiMethod ?: return false
  if (!owner.isConstructor) return false

  val parent = parents(true).firstOrNull { it !is PsiExpression } as? PsiExpressionList ?: return false
  val grandParent = parent.parent as? PsiMethodCallExpression ?: return false

  val calleText = grandParent.methodExpression.text
  return calleText == JavaKeywords.SUPER || calleText == JavaKeywords.THIS
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
  return when (CodeStyleSettingsManager.getSettings(project).getCustomSettings(JavaCodeStyleSettings::class.java).VISIBILITY) {
    PsiModifier.PUBLIC -> JvmModifier.PUBLIC
    PsiModifier.PROTECTED -> JvmModifier.PROTECTED
    PsiModifier.PACKAGE_LOCAL -> JvmModifier.PACKAGE_LOCAL
    PsiModifier.PRIVATE -> JvmModifier.PRIVATE
    else -> null // TODO escalate visibility
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
