// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractSortPropertiesSession<TObj : PsiElement, TProp : PsiElement>(
  protected val context: ActionContext,
  protected val file: PsiFile
) {

  protected val selection: TextRange = context.selection

  protected val rootObj: TObj? = findRootObject()
  protected val objects: Set<TObj> = rootObj?.let { collectObjects(it) } ?: emptySet()

  val rootElement: PsiElement?
    get() = rootObj

  protected abstract fun findRootObject(): TObj?
  protected abstract fun collectObjects(rootObj: TObj): Set<TObj>
  protected abstract fun getProperties(obj: TObj): MutableList<TProp>
  protected abstract fun getPropertyName(prop: TProp): String?
  protected abstract fun getParentObject(obj: TObj): TObj?
  protected abstract fun traverseObjects(root: TObj, visitor: (TObj) -> Unit)

  protected fun adjustToSelectionContainer(initObj: TObj?): TObj? {
    val hasSelection = selection.startOffset != selection.endOffset
    if (initObj == null || !hasSelection) return initObj
    var obj: TObj = initObj
    while (obj.textRange?.containsRange(selection.startOffset, selection.endOffset) == false) {
      obj = getParentObject(obj) ?: break
    }
    return obj
  }

  protected fun collectIntersectingObjects(rootObj: TObj): Set<TObj> {
    val result = LinkedHashSet<TObj>()
    val hasSelection = selection.startOffset != selection.endOffset
    if (hasSelection) {
      traverseObjects(rootObj) { o ->
        if (o.textRange?.intersects(selection.startOffset, selection.endOffset) == true) {
          result.add(o)
        }
      }
    }
    result.add(rootObj)
    return result
  }

  fun hasUnsortedObjects(): Boolean = objects.any { !isSorted(it) }

  fun sort() {
    objects.forEach { obj ->
      if (!isSorted(obj)) {
        cycleSortProperties(obj)
      }
    }
  }

  // Shared implementation
  private fun isSorted(obj: TObj): Boolean {
    return getProperties(obj).asSequence()
      .map { getPropertyName(it) }
      .zipWithNext()
      .all { (l, r) -> l.orEmpty() <= r.orEmpty() }
  }

  // cycle-sort performs the minimal amount of modifications, which keeps PSI patches small
  private fun cycleSortProperties(obj: TObj) {
    val properties: MutableList<TProp> = getProperties(obj)
    val size = properties.size
    for (cycleStart in 0 until size) {
      val item = properties[cycleStart]
      var pos = advance(properties, size, cycleStart, item)
      if (pos == -1) continue
      if (pos != cycleStart) {
        exchange(properties, pos, cycleStart)
      }
      while (pos != cycleStart) {
        pos = advance(properties, size, cycleStart, properties[cycleStart])
        if (pos == -1) break
        if (pos != cycleStart) {
          exchange(properties, pos, cycleStart)
        }
      }
    }
  }

  private fun advance(properties: List<TProp>, size: Int, cycleStart: Int, item: TProp): Int {
    var pos = cycleStart
    val itemName = getPropertyName(item).orEmpty()
    for (i in cycleStart + 1 until size) {
      if (getPropertyName(properties[i]).orEmpty() < itemName) pos++
    }
    if (pos == cycleStart) return -1
    while (itemName == getPropertyName(properties[pos]).orEmpty()) pos++
    return pos
  }

  @Suppress("UNCHECKED_CAST")
  private fun exchange(properties: MutableList<TProp>, pos: Int, item: Int) {
    val propertyAtPos = properties[pos]
    val itemProperty = properties[item]
    val propertyAtPosParent = (propertyAtPos.parent as PsiElement)
    val itemPropertyParent = (itemProperty.parent as PsiElement)
    properties[pos] = propertyAtPosParent.addBefore(itemProperty, propertyAtPos) as TProp
    properties[item] = itemPropertyParent.addBefore(propertyAtPos, itemProperty) as TProp
    propertyAtPos.delete()
    itemProperty.delete()
  }
}