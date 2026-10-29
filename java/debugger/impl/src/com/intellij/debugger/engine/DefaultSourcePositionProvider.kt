// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.ui.impl.watch.ArgumentValueDescriptorImpl
import com.intellij.debugger.ui.impl.watch.CurrentMethodReturnValueDescriptorImpl
import com.intellij.debugger.ui.impl.watch.FieldDescriptorImpl
import com.intellij.debugger.ui.impl.watch.MethodReturnValueDescriptorImpl
import com.intellij.debugger.ui.tree.FieldDescriptor
import com.intellij.debugger.ui.tree.LocalVariableDescriptor
import com.intellij.debugger.ui.tree.NodeDescriptor
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ClassNotPreparedException

class DefaultSourcePositionProvider : SourcePositionProvider() {
  override fun computeSourcePosition(
    descriptor: NodeDescriptor,
    project: Project,
    context: DebuggerContextImpl,
    nearest: Boolean,
  ): SourcePosition? {
    if (context.frameProxy == null) return null

    return when (descriptor) {
      is FieldDescriptor -> getSourcePositionForField(descriptor, project, context, nearest)
      is LocalVariableDescriptor -> {
        val descriptorName = descriptor.name
        getSourcePositionForLocalVariable(descriptorName, project, context, nearest)
      }
      is ArgumentValueDescriptorImpl -> {
        val descriptorName = descriptor.variable.matchedNames.firstOrNull() ?: return null
        getSourcePositionForLocalVariable(descriptorName, project, context, nearest)
      }
      is CurrentMethodReturnValueDescriptorImpl -> {
        if (context.suspendContext?.currentMethodExitEvent != null) context.sourcePosition else null
      }
      is MethodReturnValueDescriptorImpl -> {
        context.debugProcess?.positionManager?.getSourcePosition(descriptor.method.location())
      }
      else -> null
    }
  }

  private fun getSourcePositionForField(
    descriptor: FieldDescriptor,
    project: Project,
    context: DebuggerContextImpl,
    nearest: Boolean,
  ): SourcePosition? {
    val type = descriptor.field.declaringType()
    val fieldName = descriptor.field.name()
    if (fieldName.startsWith(FieldDescriptorImpl.OUTER_LOCAL_VAR_FIELD_PREFIX)) {
      // this field actually mirrors a local variable in the outer class
      val varName = fieldName.substringAfterLast('$')
      val element = context.contextElement ?: return null
      val aClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false) ?: return null
      val navigationClass = aClass.navigationElement as? PsiClass ?: return null
      val psiVariable = JavaPsiFacade.getInstance(project).resolveHelper.resolveReferencedVariable(varName, navigationClass)
      if (psiVariable == null) return null
      if (nearest) {
        return DebuggerContextUtil.findNearest(context, psiVariable, navigationClass.containingFile)
      }
      return SourcePosition.createFromElement(psiVariable)
    }

    var aClass: PsiClass? = null
    val debugProcess = context.debugProcess
    if (debugProcess != null) {
      try {
        // important: use the last location to be sure the position will be within the anonymous class
        // and do not use type.allLineLocations as it fetches line tables for all methods
        for (method in type.methods().asReversed()) {
          val locations = method.allLineLocations()
          if (locations.isNotEmpty()) {
            aClass = JVMNameUtil.getClassAt(debugProcess.positionManager.getSourcePosition(locations.last()))
            break
          }
        }
      }
      catch (_: AbsentInformationException) {
      }
      catch (_: ClassNotPreparedException) {
      }
    }

    if (aClass == null) { // fallback
      val scope = context.debuggerSession?.searchScope ?: GlobalSearchScope.allScope(project)
      aClass = DebuggerUtils.findClass(type.name(), project, scope)
    }
    if (aClass == null) return null

    val field = aClass.findFieldByName(fieldName, false) ?: return null
    if (nearest) {
      return DebuggerContextUtil.findNearest(context, aClass.containingFile) { searchScope ->
        findThisFieldUsages(field, searchScope)
      }
    }
    return SourcePosition.createFromElement(field)
  }

  private fun findThisFieldUsages(field: PsiField, searchScope: PsiElement): Collection<TextRange> {
    val ranges = ArrayList<TextRange>()
    for (reference in ReferencesSearch.search(field, LocalSearchScope(searchScope)).findAll()) {
      val expression = PsiTreeUtil.getParentOfType(reference.element, PsiReferenceExpression::class.java, false) ?: continue
      val qualifier = expression.qualifierExpression
      if (qualifier == null || qualifier is PsiThisExpression || qualifier is PsiSuperExpression) {
        HighlightUsagesHandler.collectHighlightRanges(reference, ranges)
      }
    }
    return ranges
  }

  private fun getSourcePositionForLocalVariable(
    name: String,
    project: Project,
    context: DebuggerContextImpl,
    nearest: Boolean,
  ): SourcePosition? {
    val place = context.contextElement ?: return null
    val psiVariable = JavaPsiFacade.getInstance(project).resolveHelper.resolveReferencedVariable(name, place) ?: return null
    val containingFile = psiVariable.containingFile ?: return null
    try {
      if (nearest) {
        return DebuggerContextUtil.findNearest(context, psiVariable, containingFile)
      }
    }
    catch (_: IndexNotReadyException) {
    }
    return SourcePosition.createFromElement(psiVariable)
  }
}