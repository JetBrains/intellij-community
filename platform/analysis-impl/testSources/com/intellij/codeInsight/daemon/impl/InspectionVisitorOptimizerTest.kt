// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = "ide.optimize.inspection.visitors", value = "true")
internal class InspectionVisitorOptimizerTest {
  @Test
  fun `empty visitor accepts no elements`() {
    assertTrue(InspectionVisitorOptimizer.getAcceptingPsiTypes(PsiElementVisitor.EMPTY_VISITOR).isEmpty())
  }

  @Test
  fun `accepts an interface inherited by a class without direct interfaces`() {
    assertAccepts(ElementWithoutInterfaces(), PsiNamedElement::class.java)
  }

  @Test
  fun `accepts a superclass beyond the immediate parent`() {
    assertAccepts(ElementWithInterface(), BaseElement::class.java)
  }

  private fun assertAccepts(element: PsiElement, acceptingType: Class<*>) {
    val elements = listOf(element)
    val visited = ArrayList<PsiElement>()
    InspectionVisitorOptimizer(elements).acceptElements(elements, listOf(acceptingType)) { visited.add(it) }
    assertEquals(elements, visited)
  }

  private open class BaseElement : FakePsiElement() {
    override fun getParent(): PsiElement? = null
    override fun getName(): String = "test"
  }

  private open class MiddleElement : BaseElement()
  private interface DirectInterface
  private class ElementWithoutInterfaces : MiddleElement()
  private class ElementWithInterface : MiddleElement(), DirectInterface
}
