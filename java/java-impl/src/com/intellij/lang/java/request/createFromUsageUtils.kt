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
package com.intellij.lang.java.request

import com.intellij.lang.java.actions.toJavaClassOrNull
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmClassKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import com.intellij.psi.util.parentsOfType
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
