// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.ide.ui.UISettings
import com.intellij.navigation.LocationPresentation
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class GotoTargetRendererNew(presentationProvider: (Any) -> TargetPresentation) : ListCellRenderer<ItemWithPresentation> {

  private val myNullRenderer = textListCellRenderer<ItemWithPresentation> { null }
  private val myActionRenderer = listCellRenderer<ItemWithPresentation> {
    val item = value.item as GotoTargetHandler.AdditionalAction
    icon(item.icon)
    text(item.text)
  }
  private val myPresentationRenderer = if (UISettings.getInstance().showIconInQuickNavigation) {
    createFullTargetPresentationRenderer(presentationProvider)
  }
  else {
    createTargetPresentationRenderer(presentationProvider)
  }


  override fun getListCellRendererComponent(list: JList<out ItemWithPresentation>?,
                                            value: ItemWithPresentation?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    return when {
      value == null -> {
        myNullRenderer.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus)
      }
      value.item is GotoTargetHandler.AdditionalAction -> {
        myActionRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
      else -> {
        myPresentationRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
    }
  }
}

private fun <T> createFullTargetPresentationRenderer(presentationProvider: (T) -> TargetPresentation): ListCellRenderer<T> {
  return listCellRenderer {
    rowHeight = null
    val presentation = presentationProvider.invoke(value)
    fillMainTargetPresentation(presentation)
    presentation.locationText?.let { locationText ->
      text(locationText) {
        align = LcrInitParams.Align.RIGHT
        foreground = greyForeground
      }
      presentation.locationIcon?.let {
        icon(it)
      }
    }
  }
}

private fun <T> createTargetPresentationRenderer(presentationProvider: (T) -> TargetPresentation): ListCellRenderer<T> {
  return listCellRenderer {
    rowHeight = null
    val presentation = presentationProvider.invoke(value)
    fillMainTargetPresentation(presentation)
  }
}

private fun <T> LcrRow<T>.fillMainTargetPresentation(presentation: TargetPresentation) {
  presentation.backgroundColor?.let {
    background = it
  }

  presentation.icon?.let {
    icon(it)
  }

  text(presentation.presentableText) {
    presentation.presentableTextAttributes?.let {
      attributes = SimpleTextAttributes.fromTextAttributes(it)
    }
    font = this@fillMainTargetPresentation.list.font
    presentation.containerText?.let {
      accessibleName += LocationPresentation.DEFAULT_LOCATION_PREFIX + it + LocationPresentation.DEFAULT_LOCATION_SUFFIX
    }
  }

  presentation.containerText?.let { containerText ->
    val containerTextAttributes = presentation.containerTextAttributes?.let {
      SimpleTextAttributes.merge(SimpleTextAttributes.fromTextAttributes(it), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    } ?: SimpleTextAttributes.GRAYED_ATTRIBUTES
    gap(LcrRow.Gap.NONE)
    text(LocationPresentation.DEFAULT_LOCATION_PREFIX) {
      attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
      font = this@fillMainTargetPresentation.list.font
      accessibleName = null
    }
    gap(LcrRow.Gap.NONE)
    text(containerText) {
      attributes = containerTextAttributes
      font = this@fillMainTargetPresentation.list.font
      accessibleName = null
    }
    gap(LcrRow.Gap.NONE)
    text(LocationPresentation.DEFAULT_LOCATION_SUFFIX) {
      attributes = SimpleTextAttributes.GRAYED_ATTRIBUTES
      font = this@fillMainTargetPresentation.list.font
      accessibleName = null
    }
  }
}
