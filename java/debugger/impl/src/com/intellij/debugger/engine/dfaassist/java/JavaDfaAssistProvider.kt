// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist.java

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.TypeConstraints
import com.intellij.codeInspection.dataFlow.jvm.SpecialField
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.dfaassist.DebuggerDfaListener
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider
import com.intellij.debugger.engine.dfaassist.DfaAssistProvider.Companion.wrap
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.expression.CaptureTraverser
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.openapi.application.readAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import com.sun.jdi.*

private class JavaDfaAssistProvider : DfaAssistProvider {
  override suspend fun locationMatches(element: PsiElement, location: Location): Boolean {
    val method = location.method()
    val methodName = method.name()
    val methodArgumentsSize = method.argumentTypeNames().size
    val context = readAction { DebuggerUtilsEx.getContainingMethod(element) }
    if (context is PsiMethod) {
      return readAction {
        val name = if (context.isConstructor()) "<init>" else context.getName()
        name == methodName && context.getParameterList().getParametersCount() == methodArgumentsSize
      }
    }
    if (context is PsiLambdaExpression) {
      return DebuggerUtilsEx.isLambda(method) && readAction { methodArgumentsSize >= context.getParameterList().getParametersCount() }
    }
    if (context is PsiClassInitializer) {
      val expectedMethod = readAction { if (context.hasModifierProperty(PsiModifier.STATIC)) "<clinit>" else "<init>" }
      return methodName == expectedMethod
    }
    return false
  }

  override fun getAnchor(element: PsiElement): PsiElement? {
    var element = element
    while (element is PsiWhiteSpace || element is PsiComment) {
      element = element.getNextSibling()
    }
    while (element !is PsiStatement) {
      val parent = element.getParent()
      if (parent !is PsiStatement && (parent == null || element.textRangeInParent.startOffset > 0)) {
        if (parent is PsiCodeBlock && parent.getRBrace() === element) {
          val grandParent = parent.getParent()
          if (grandParent is PsiBlockStatement) {
            return PsiTreeUtil.getNextSiblingOfType(grandParent, PsiStatement::class.java)
          }
        }
        if (parent is PsiPolyadicExpression) {
          // If we are inside the expression we can position only at locations where the stack is empty
          // currently only && and || chains inside if/return/yield are allowed
          val tokenType = parent.getOperationTokenType()
          if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
            val grandParent = parent.getParent()
            if (grandParent is PsiIfStatement || grandParent is PsiYieldStatement ||
                grandParent is PsiReturnStatement
            ) {
              if (element is PsiExpression) {
                return element
              }
              return PsiTreeUtil.getNextSiblingOfType(element, PsiExpression::class.java)
            }
          }
        }
        return null
      }
      element = parent
    }
    return element
  }

  override fun getCodeBlock(anchor: PsiElement): PsiElement? {
    if (anchor is PsiSwitchLabelStatementBase) {
      return null // unsupported yet
    }
    var e: PsiElement? = anchor
    while (e != null && (e !is PsiClass) && (e !is PsiFileSystemItem)) {
      e = e.getParent()
      if (e is PsiCodeBlock) {
        val parent = e.getParent()
        if (parent is PsiMethod || parent is PsiLambdaExpression || parent is PsiClassInitializer ||
            // We cannot properly restore context if we started from finally, so let's analyze just finally block
            parent is PsiTryStatement && parent.getFinallyBlock() === e || parent is PsiBlockStatement &&
            (parent.getParent() is PsiSwitchLabeledRuleStatement &&
             (parent.getParent() as PsiSwitchLabeledRuleStatement).getEnclosingSwitchBlock() is PsiSwitchExpression)) {
          return e
        }
      }
    }
    return null
  }

  @Throws(EvaluateException::class)
  override suspend fun getJdiValueForDfaVariable(
    proxy: StackFrameProxyEx,
    dfaVar: DfaVariableValue,
    anchor: PsiElement,
  ): Value? {
    val qualifier = dfaVar.qualifier
    if (qualifier != null) {
      val descriptor = dfaVar.descriptor
      if (descriptor is SpecialField) {
        // Special fields facts are applied from qualifiers
        return null
      }
      val qualifierValue = getJdiValueForDfaVariable(proxy, qualifier, anchor)
      if (qualifierValue == null) return null
      val element = readAction { descriptor.psiElement }
      if (element is PsiField && qualifierValue is ObjectReference) {
        val type = qualifierValue.referenceType()
        val psiClass = readAction { element.getContainingClass() }
        if (psiClass != null && type.name() == readAction { JVMNameUtil.getClassVMName(psiClass) }) {
          val field = DebuggerUtils.findField(type, readAction { element.getName() })
          if (field != null) {
            return wrap(qualifierValue.getValue(field))
          }
        }
      }
      if (descriptor is ArrayElementDescriptor && qualifierValue is ArrayReference) {
        val index = descriptor.index
        val length = qualifierValue.length()
        if (index in 0..<length) {
          return wrap(qualifierValue.getValue(index))
        }
      }
      return null
    }
    val psi = readAction { dfaVar.psiVariable }
    if (psi is PsiClass) {
      // this; probably qualified
      val captureTraverser = readAction {
        val currentClass = PsiTreeUtil.getParentOfType(anchor, PsiClass::class.java)
        CaptureTraverser.create(psi, currentClass, true)
      }
      return captureTraverser.traverse(proxy.thisObject())
    }
    if (psi is PsiLocalVariable || psi is PsiParameter) {
      val varName: String = readAction { psi.getName()!! }
      val resolveVariable = readAction { PsiResolveHelper.getInstance(psi.getProject()).resolveReferencedVariable(varName, anchor) }
      if (resolveVariable !== psi) {
        // Another variable with the same name could be tracked by DFA in different code branch but not visible at current code location
        return null
      }
      val variable = proxy.visibleVariableByName(varName)
      if (variable != null) {
        return wrap(proxy.getVariableValue(variable))
      }
      val captureTraverser = readAction {
        val currentClass = PsiTreeUtil.getParentOfType(anchor, PsiClass::class.java)
        val varClass = PsiTreeUtil.getParentOfType(psi, PsiClass::class.java)
        CaptureTraverser.create(varClass, currentClass, false).oneLevelLess()
      }
      val thisRef = captureTraverser.traverse(proxy.thisObject())
      if (thisRef != null) {
        val type = thisRef.referenceType()
        if (type is ClassType && type.isPrepared) {
          val field = DebuggerUtils.findField(type, "val$$varName")
          if (field != null) {
            return wrap(thisRef.getValue(field))
          }
        }
      }
    }
    if (psi is PsiField && readAction { psi.hasModifierProperty(PsiModifier.STATIC) }) {
      val psiClass = readAction { psi.getContainingClass() }
      if (psiClass != null) {
        val name = readAction { psiClass.getQualifiedName() }
        if (name != null) {
          val type = ContainerUtil.getOnlyItem(proxy.getVirtualMachine().classesByName(name))
          if (type != null && type.isPrepared) {
            val field = DebuggerUtils.findField(type, psi.getName())
            if (field != null && field.isStatic) {
              return wrap(type.getValue(field))
            }
          }
        }
      }
    }
    return null
  }

  override fun createListener(): DebuggerDfaListener {
    return JavaDebuggerDfaListener()
  }

  override fun constraintFromJvmClassName(anchor: PsiElement, jvmClassName: String): TypeConstraint {
    val aClass = DebuggerUtils.findClass(jvmClassName.replace('/', '.'), anchor.getProject(), anchor.getResolveScope())
    return if (aClass != null) TypeConstraints.exactClass(aClass) else TypeConstraints.TOP
  }
}
