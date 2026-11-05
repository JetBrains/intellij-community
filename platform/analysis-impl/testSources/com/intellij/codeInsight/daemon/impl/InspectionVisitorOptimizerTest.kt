// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.HintedPsiElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
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

  @Test
  fun `selects matching buckets without rescanning other elements`() {
    val first = ElementWithInterface()
    val second = OtherElementWithInterface()
    val elements = CountingElements(List(1000) { index ->
      when (index) {
        200, 900 -> first
        700 -> second
        else -> ElementWithoutInterfaces()
      }
    })
    val optimizer = InspectionVisitorOptimizer(elements)
    elements.reads = 0

    repeat(3) {
      val filtered = optimizer.filterElements(listOf(DirectInterface::class.java, ElementWithInterface::class.java))
      assertEquals(listOf(first, second, first), filtered)
      assertThrows(UnsupportedOperationException::class.java) { (filtered as MutableList<PsiElement>).clear() }
    }

    assertEquals(9, elements.reads)
  }

  @Test
  fun `selects one matching class without reading other elements`() {
    val first = ElementWithInterface()
    val second = ElementWithInterface()
    val elements = CountingElements(listOf(ElementWithoutInterfaces(), first, ElementWithoutInterfaces(), second))
    val optimizer = InspectionVisitorOptimizer(elements)
    elements.reads = 0

    val filtered = optimizer.filterElements(setOf(OtherElementWithInterface::class.java, ElementWithInterface::class.java))

    assertEquals(listOf(first, second), filtered)
    assertEquals(2, elements.reads)
    assertThrows(UnsupportedOperationException::class.java) { (filtered as MutableList<PsiElement>).clear() }

    elements.reads = 0
    assertTrue(optimizer.filterElements(setOf(OtherElementWithInterface::class.java)).isEmpty())
    assertEquals(0, elements.reads)
  }

  @Test
  fun `indexes inside and outside elements separately`() {
    val first = ElementWithInterface()
    val second = OtherElementWithInterface()
    val inside = listOf(ElementWithoutInterfaces(), first)
    val outside = listOf(second, ElementWithoutInterfaces(), first, second)
    val insideOptimizer = InspectionVisitorOptimizer(inside)
    val outsideOptimizer = InspectionVisitorOptimizer(outside)
    val acceptingClasses = InspectionVisitorOptimizer.getVisitorAcceptClasses(
      listOf(DirectInterface::class.java), insideOptimizer, outsideOptimizer)

    assertEquals(listOf(first), insideOptimizer.filterElements(acceptingClasses))
    assertEquals(listOf(second, first, second), outsideOptimizer.filterElements(acceptingClasses))
    assertTrue(insideOptimizer.filterElements(listOf(OtherElementWithInterface::class.java)).isEmpty())
  }

  @Test
  fun `resolves types once for both element lists`() {
    val first = ElementWithInterface()
    val second = OtherElementWithInterface()
    val inside = CountingElements(listOf(ElementWithoutInterfaces(), first))
    val outside = CountingElements(listOf(second, ElementWithoutInterfaces()))
    val insideOptimizer = InspectionVisitorOptimizer(inside)
    val outsideOptimizer = InspectionVisitorOptimizer(outside)
    val types = listOf(DirectInterface::class.java, ElementWithInterface::class.java)
    var typeReads = 0
    val acceptingTypes = object : AbstractList<Class<out PsiElement>>() {
      override val size: Int get() = types.size

      override fun get(index: Int): Class<out PsiElement> {
        typeReads++
        return types[index]
      }
    }
    inside.reads = 0
    outside.reads = 0

    val acceptingClasses = InspectionVisitorOptimizer.getVisitorAcceptClasses(acceptingTypes, insideOptimizer, outsideOptimizer)

    assertEquals(setOf(ElementWithInterface::class.java, OtherElementWithInterface::class.java), acceptingClasses)
    assertEquals(listOf(first), insideOptimizer.filterElements(acceptingClasses))
    assertEquals(listOf(second), outsideOptimizer.filterElements(acceptingClasses))
    assertEquals(types.size, typeReads)
    assertEquals(1, inside.reads)
    assertEquals(1, outside.reads)
  }

  @Test
  fun `keeps the input list when every element matches`() {
    val elements = listOf(ElementWithInterface(), OtherElementWithInterface())
    val optimizer = InspectionVisitorOptimizer(elements)

    assertSame(elements, optimizer.filterElements(listOf(DirectInterface::class.java)))
    val allTypes = InspectionVisitorOptimizer.getAcceptingPsiTypes(object : PsiElementVisitor() {})
    assertSame(elements, optimizer.filterElements(allTypes))
    assertTrue(optimizer.filterElements(emptyList()).isEmpty())

    val sameClassElements = listOf(ElementWithInterface(), ElementWithInterface())
    val sameClassOptimizer = InspectionVisitorOptimizer(sameClassElements)
    assertSame(sameClassElements, sameClassOptimizer.filterElements(setOf(ElementWithInterface::class.java)))

    val outside = listOf(ElementWithoutInterfaces())
    val outsideOptimizer = InspectionVisitorOptimizer(outside)
    val allClasses = InspectionVisitorOptimizer.getVisitorAcceptClasses(allTypes, optimizer, outsideOptimizer)
    assertSame(elements, optimizer.filterElements(allClasses))
    assertSame(outside, outsideOptimizer.filterElements(allClasses))

    val noClasses = InspectionVisitorOptimizer.getVisitorAcceptClasses(emptyList(), optimizer, outsideOptimizer)
    assertTrue(optimizer.filterElements(noClasses).isEmpty())
    assertTrue(outsideOptimizer.filterElements(noClasses).isEmpty())
  }

  @Test
  fun `accepts elements in input order`() {
    val first = ElementWithInterface()
    val second = OtherElementWithInterface()
    val elements = listOf(first, ElementWithoutInterfaces(), first, second)
    val optimizer = InspectionVisitorOptimizer(elements.reversed())
    val visited = ArrayList<PsiElement>()

    optimizer.acceptElements(object : PsiElementVisitor(), HintedPsiElementVisitor {
      override fun getHintPsiElements(): List<Class<*>> = listOf(DirectInterface::class.java)

      override fun visitElement(element: PsiElement) {
        visited.add(element)
      }
    })

    assertEquals(listOf(second, first, first), visited)
  }

  @Test
  fun `cancellation stops merging matching buckets`() {
    val elements = CountingElements(listOf(ElementWithInterface(), ElementWithoutInterfaces(), OtherElementWithInterface()))
    val optimizer = InspectionVisitorOptimizer(elements)
    val indicator = ProgressIndicatorBase()
    elements.onRead = { indicator.cancel() }

    assertThrows(ProcessCanceledException::class.java) {
      ProgressManager.getInstance().runProcess({ optimizer.filterElements(listOf(DirectInterface::class.java)) }, indicator)
    }
  }

  @Test
  fun `cancellation stops reading one matching class`() {
    val elements = CountingElements(listOf(ElementWithInterface(), ElementWithoutInterfaces(), ElementWithInterface()))
    val optimizer = InspectionVisitorOptimizer(elements)
    val indicator = ProgressIndicatorBase()
    elements.onRead = { indicator.cancel() }

    assertThrows(ProcessCanceledException::class.java) {
      ProgressManager.getInstance().runProcess({ optimizer.filterElements(setOf(ElementWithInterface::class.java)) }, indicator)
    }
  }

  @Test
  @Suppress("SuspiciousPackagePrivateAccess")
  fun `filtering before the fertility sort preserves visitation order`() {
    val first = ElementWithInterface()
    val second = OtherElementWithInterface()
    val third = ElementWithInterface()
    val rejected = ElementWithoutInterfaces()
    val elements = listOf(first, rejected, second, third, second)
    val optimizer = InspectionVisitorOptimizer(elements)
    val highlights: Map<PsiElement, List<HighlightInfo>> = mapOf(
      rejected to listOf(info(HighlightSeverity.ERROR)),
      second to listOf(info(HighlightSeverity.WARNING)),
      third to listOf(info(HighlightSeverity.WARNING)),
    )
    val acceptingTypes = listOf(DirectInterface::class.java)

    val sorted = HighlightInfoUpdaterImpl.sortByPsiElementFertility(elements, highlights)
    val expected = sorted.filterIsInstance<DirectInterface>()
    val actual = HighlightInfoUpdaterImpl.sortByPsiElementFertility(optimizer.filterElements(acceptingTypes), highlights)

    assertEquals(listOf(second, third, second, first), actual)
    assertEquals(expected, actual)
  }

  private fun info(severity: HighlightSeverity): HighlightInfo =
    HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(0, 1).severity(severity).createUnconditionally()

  private fun assertAccepts(element: PsiElement, acceptingType: Class<out PsiElement>) {
    val elements = listOf(element)
    val visited = ArrayList<PsiElement>()
    InspectionVisitorOptimizer.acceptElements(InspectionVisitorOptimizer(elements).filterElements(listOf(acceptingType))) { visited.add(it) }
    assertEquals(elements, visited)
  }

  private open class BaseElement : FakePsiElement() {
    override fun getParent(): PsiElement? = null
    override fun getName(): String = "test"
  }

  private open class MiddleElement : BaseElement()
  private interface DirectInterface : PsiElement
  private class ElementWithoutInterfaces : MiddleElement()
  private class ElementWithInterface : MiddleElement(), DirectInterface
  private class OtherElementWithInterface : MiddleElement(), DirectInterface

  private class CountingElements(private val elements: List<PsiElement>) : AbstractList<PsiElement>() {
    var reads = 0
    var onRead: () -> Unit = {}
    override val size: Int get() = elements.size

    override fun get(index: Int): PsiElement {
      reads++
      onRead()
      return elements[index]
    }
  }
}
