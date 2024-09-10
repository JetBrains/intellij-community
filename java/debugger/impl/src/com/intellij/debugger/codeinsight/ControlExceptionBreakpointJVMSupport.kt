// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.codeinsight

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.ui.breakpoints.JavaExceptionBreakpointType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.parents
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.codeinsight.ControlExceptionBreakpointSupport
import com.intellij.xdebugger.codeinsight.ControlExceptionBreakpointSupport.ExceptionReference
import org.jetbrains.uast.*

open class ControlExceptionBreakpointJVMSupport : ControlExceptionBreakpointSupport {

  protected fun findClassReference(psiElement: PsiElement): PsiClass? {
    // Note that we use "PSI parents" instead of "UAST parents"
    // because the latter might skip some elements between UIndentifier and UCallExpression in Kotlin,
    // leading to missing UReferenceExpressions. See KTIJ-31217.
    psiElement.parents(true).forEach { parent ->
      val uastParent = parent.toUElement() ?: return@forEach
      when (uastParent) {
        is UReferenceExpression -> {
          // E.g., catch (Runtime<caret>Exception e)
          val resolved = uastParent.resolve()
          return when (resolved) {
            is PsiClass -> resolved
            is PsiMethod -> if (resolved.isConstructor) resolved.containingClass else null
            else -> null
          }
        }
        is UClass -> {
          // E.g., public class Runtime<caret>Exception extends Exception {
          val clazz = uastParent.javaPsi
          // If the initial element is not the class name identifier, ignore it.
          // Note that direct comparison of UElements is not working correctly.
          val uastIdentifier = psiElement.toUElement() as? UIdentifier
          if (uastIdentifier == null || uastIdentifier.name != clazz.name) return null
          val clazzIdentifier = uastParent.uastAnchor?.sourcePsi?.takeIf { it.text == clazz.name }
          if (clazzIdentifier != null && psiElement != clazzIdentifier) return null

          return clazz
        }
        is UThrowExpression -> {
          // E.g., throw<caret> new RuntimeException()

          // It would be better to check that psiElement is a throw keyword, but I was unable to implement this.
          // It means that intention would also be given in the following case: throw foo(<caret>)

          val thrownType = uastParent.thrownExpression.getExpressionType() as? PsiClassType
          return thrownType?.resolve()
        }
      }
    }
    return null
  }

  override fun findExceptionReference(project: Project, element: PsiElement): ExceptionReference? {
    val clazz = findClassReference(element) ?: return null
    if (!InheritanceUtil.isInheritor(clazz, CommonClassNames.JAVA_LANG_THROWABLE)) return null
    val qualifiedName = clazz.qualifiedName ?: return null
    val displayName = clazz.name ?: qualifiedName
    return JVMExceptionReference(qualifiedName, displayName)
  }

  open class JVMExceptionReference(
    private val qualifiedName: String,
    override val displayName: String,
  ) : ExceptionReference {

    override fun findExistingBreakpoint(project: Project): XBreakpoint<*>? =
      XDebuggerManager.getInstance(project)
        .breakpointManager
        .getBreakpoints(JavaExceptionBreakpointType::class.java)
        .firstOrNull { it.properties.myQualifiedName == qualifiedName }

    override fun createBreakpoint(project: Project): XBreakpoint<*>? =
      DebuggerManagerEx.getInstanceEx(project)
        .breakpointManager
        .addExceptionBreakpoint(qualifiedName)
        ?.xBreakpoint
  }
}