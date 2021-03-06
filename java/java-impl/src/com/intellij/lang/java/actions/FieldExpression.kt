// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupFocusDegree
import com.intellij.codeInsight.template.Expression
import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.codeInsight.template.Result
import com.intellij.codeInsight.template.TextResult
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.util.createSmartPointer
import com.intellij.ui.LayeredIcon

private val newFieldIcon by lazy { LayeredIcon.create(AllIcons.Nodes.Field, AllIcons.Actions.New) }

internal class FieldExpression(
  project: Project,
  target: PsiClass,
  private val fieldName: String,
  private val typeText: () -> String
) : Expression() {
  private val myClassPointer = target.createSmartPointer(project)
  private val myFactory = JavaPsiFacade.getElementFactory(project)

  override fun calculateResult(context: ExpressionContext): Result? = TextResult(fieldName)

  override fun calculateLookupItems(context: ExpressionContext): Array<LookupElement> {
    val psiClass = myClassPointer.element ?: return LookupElement.EMPTY_ARRAY

    val userType = myFactory.createTypeFromText(typeText(), psiClass)
    val result = LinkedHashSet<LookupElement>()
    if (psiClass.findFieldByName(fieldName, false) == null) {
      result += LookupElementBuilder.create(fieldName).withIcon(newFieldIcon).withTypeText(userType.presentableText)
    }
    for (field in psiClass.fields) {
      val fieldType = field.type
      if (userType == fieldType) {
        result += JavaLookupElementBuilder.forField(field).withTypeText(fieldType.presentableText)
      }
    }

    return if (result.size < 2) LookupElement.EMPTY_ARRAY else result.toTypedArray()
  }

  override fun getLookupFocusDegree(): LookupFocusDegree {
    return LookupFocusDegree.UNFOCUSED
  }
}
