// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle.message
import com.intellij.codeInsight.generation.GenerateMembersUtil.generateSimpleSetterPrototype
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.CreateWriteOnlyPropertyActionGroup
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.presentation.java.ClassPresentationUtil.getNameForClass

/**
 * This action renders a write-only property (field + setter) in Java class when setter is requested.
 */
internal class CreateSetterWithFieldAction(target: PsiClass, request: CreateMethodRequest) : CreatePropertyActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateWriteOnlyPropertyActionGroup

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return super.isAvailable(project, editor, file) && propertyInfo.second == PropertyKind.SETTER
  }

  override fun getText(): String {
    return message("create.write.only.property.from.usage.full.text", propertyInfo.first, getNameForClass(target, false))
  }

  override fun createRenderer(project: Project) = object : PropertyRenderer(project, target, request, propertyInfo) {

    override fun fillTemplate(builder: TemplateBuilderImpl): RangeExpression? {
      val prototypeField = generatePrototypeField()
      val prototype = generateSimpleSetterPrototype(prototypeField, target)
      val accessor = insertAccessor(prototype) ?: return null
      val data = accessor.extractSetterTemplateData()
      val typeExpression = builder.setupInput(data)
      builder.setupSetterParameter(data)
      return typeExpression
    }
  }
}
