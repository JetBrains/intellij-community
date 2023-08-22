// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.util

import com.intellij.openapi.vcs.changes.issueLinks.LinkMouseListenerBase
import com.intellij.ui.components.JBList
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.ListCellRenderer

interface ClickableCellRenderer<T> : ListCellRenderer<T> {
  fun getTagAt(point: Point): Any?
}

class LinkMouseListener<T>(private val renderer: ClickableCellRenderer<T>) : LinkMouseListenerBase<Any?>() {
  override fun getTagAt(e: MouseEvent): Any? {
    @Suppress("UNCHECKED_CAST")
    val list = e.source as JBList<T>
    val model = list.model

    val row = list.locationToIndex(e.point)
    if (row < 0 || row >= model.size) return null

    renderer.getListCellRendererComponent(list, model.getElementAt(row), row, false, false)
    val rowBounds = list.getCellBounds(row, row)
    val rowPoint = Point(e.point.x - rowBounds.x, e.point.y - rowBounds.y)

    return renderer.getTagAt(rowPoint)
  }
}