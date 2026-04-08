// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap.model

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.IdentityHashMap

internal class MinimapStructureMarkerCollector(
  private val structureMarkerPolicy: MinimapStructureMarkerPolicy,
  private val previousByElement: IdentityHashMap<StructureViewTreeElement, MinimapStructureMarker>,
  private val reusedStructureMarkers: IdentityHashMap<MinimapStructureMarker, Boolean>,
  private val result: MutableList<MinimapStructureMarker>,
  private val document: Document,
  private val pointerManager: SmartPointerManager?,
) {
  private data class StructureElementData(
    val range: TextRange,
    val pointer: SmartPsiElementPointer<out PsiElement>?
  )

  fun visit(element: StructureViewTreeElement, includeSelf: Boolean) {
    if (includeSelf) {
      addStructureMarker(element)
    }
    for (child in element.children) {
      val childElement = child as? StructureViewTreeElement ?: continue
      visit(childElement, true)
    }
  }

  private fun addStructureMarker(element: StructureViewTreeElement) {
    val existing = previousByElement[element]
    val data = resolveElementData(element, existing) ?: return

    val marker = resolveRangeMarker(existing, data.range, data.pointer)
    markAsReused(existing, marker, data.pointer)
    result.add(MinimapStructureMarker(element, marker, data.pointer))
  }

  private fun resolveElementData(
    element: StructureViewTreeElement,
    existing: MinimapStructureMarker?
  ): StructureElementData? = ReadAction.computeBlocking<StructureElementData?, RuntimeException> {
    val value = element.value ?: return@computeBlocking null
    if (!structureMarkerPolicy.isRelevantStructureElement(element, value)) return@computeBlocking null

    val source = resolveStructureMarkerSource(element)
    val range = source.range ?: return@computeBlocking null
    val pointer = resolvePointer(existing, source.psiElement)
    if (pointerManager != null && source.psiElement != null && pointer == null) return@computeBlocking null

    StructureElementData(range, pointer)
  }

  private fun resolveRangeMarker(
    existing: MinimapStructureMarker?,
    range: TextRange,
    pointer: SmartPsiElementPointer<out PsiElement>?
  ): RangeMarker? {
    return when {
      pointer != null -> null
      existing?.rangeMarker?.isValid == true -> existing.rangeMarker
      else -> {
        val documentLength = document.textLength
        val startOffset = range.startOffset.coerceIn(0, documentLength)
        val endOffset = range.endOffset.coerceIn(startOffset, documentLength)
        document.createRangeMarker(startOffset, endOffset)
      }
    }
  }

  private fun markAsReused(
    existing: MinimapStructureMarker?,
    marker: RangeMarker?,
    pointer: SmartPsiElementPointer<out PsiElement>?
  ) {
    if (existing == null) return

    if (pointer === existing.pointer || marker === existing.rangeMarker) {
      reusedStructureMarkers[existing] = true
    }
  }

  private fun resolveStructureMarkerSource(element: StructureViewTreeElement): MinimapStructureMarkerSource {
    val value = element.value
    return when (value) {
      is PsiNameIdentifierOwner -> {
        val nameIdentifier = value.nameIdentifier
        val target = nameIdentifier ?: value
        MinimapStructureMarkerSource(target, target.textRange)
      }
      is PsiElement -> MinimapStructureMarkerSource(value, value.textRange)
      is TextRange -> MinimapStructureMarkerSource(null, value)
      else -> MinimapStructureMarkerSource(null, null)
    }
  }

  @RequiresReadLock
  private fun resolvePointer(existing: MinimapStructureMarker?, psiElement: PsiElement?): SmartPsiElementPointer<out PsiElement>? = when {
    existing?.pointer?.range != null -> existing.pointer
    pointerManager != null && psiElement != null -> createPointer(pointerManager, psiElement)
    else -> null
  }

  @RequiresReadLock
  private fun createPointer(pointerManager: SmartPointerManager,
                            element: PsiElement): SmartPsiElementPointer<out PsiElement>? {
    if (!element.isValid) return null
    return pointerManager.createSmartPsiElementPointer(element)
  }
}