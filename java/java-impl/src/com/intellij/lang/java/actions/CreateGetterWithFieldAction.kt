// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleGetterPrototype
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.CreateReadOnlyPropertyActionGroup
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass

/**
 * This action renders a read-only property (field + getter) in Java class when getter is requested.
 */
internal class CreateGetterWithFieldAction(target: PsiClass, request: CreateMethodRequest) : CreatePropertyActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateReadOnlyPropertyActionGroup

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return super.isAvailable(project, editor, file) && propertyInfo.second != PropertyKind.SETTER
  }

  override fun getText(): String {
    return message("create.read.only.property.from.usage.full.text", propertyInfo.first, getNameForClass(target, false))
  }

  override fun createRenderer(project: Project) = object : PropertyRenderer(project, target, request, propertyInfo) {

    override fun fillTemplate(builder: TemplateBuilderImpl): RangeExpression? {
      val prototypeField = generatePrototypeField()
      val prototype = generateSimpleGetterPrototype(prototypeField)
      val accessor = insertAccessor(prototype) ?: return null
      val data = accessor.extractGetterTemplateData()
      return builder.setupInput(data)
    }
  }
}
