// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.compose

import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorCustomComponentChildProvider
import com.intellij.internal.inspector.UiInspectorCustomComponentProvider
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.ui.UIUtil
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.swing.SkiaSwingLayer
import java.awt.Rectangle
import javax.accessibility.AccessibleComponent
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.event.AncestorEvent

@Suppress("FunctionName")
@OptIn(ExperimentalSkikoApi::class)
internal fun ComposeUiInspector(composePanel: JComponent) {
  val skiaComponent = findSkiaLayer(composePanel)

  if (skiaComponent == null) {
    composePanel.addAncestorListener(object : AncestorListenerAdapter() {
      override fun ancestorAdded(event: AncestorEvent) {
        findSkiaLayer(composePanel)?.also {
          composePanel.removeAncestorListener(this)
          configureSkiaLayer(it)
        }
      }
    })
  }
  else {
    configureSkiaLayer(skiaComponent)
  }
}

@OptIn(ExperimentalSkikoApi::class)
private fun findSkiaLayer(composePanel: JComponent): SkiaSwingLayer? {
  return UIUtil.findComponentOfType<SkiaSwingLayer>(composePanel, SkiaSwingLayer::class.java)
}

private fun configureSkiaLayer(skiaComponent: JComponent) {
  skiaComponent.putClientProperty(UiInspectorCustomComponentProvider.KEY, ComposeUiInspectorProvider(skiaComponent))
}

private class ComposeUiInspectorProvider(private val composePanel: JComponent) : UiInspectorCustomComponentProvider {
  override fun getChildren(): List<UiInspectorCustomComponentChildProvider> {
    val context = composePanel.accessibleContext ?: return emptyList()
    val count = context.accessibleChildrenCount
    val composeChildren = mutableListOf<UiInspectorCustomComponentChildProvider>()

    for (i in 0..count - 1) {
      val component = context.getAccessibleChild(i).accessibleContext
      if (component != null) {
        composeChildren.add(InspectorObject(component))
      }
    }

    return composeChildren
  }
}

private val PROPERTIES = listOf<String>("isEnabled", "isVisible", "isShowing", "getLocationOnScreen", "getLocation",
                                        "getBounds", "getSize", "isFocusTraversable", "requestFocus")

private class InspectorObject(private val component: AccessibleContext) : UiInspectorCustomComponentChildProvider {
  override fun getTreeName(): String {
    val name = component.accessibleName ?: component.accessibleDescription
    if (name.isNullOrBlank()) {
      return component.toString()
    }
    return name
  }

  override fun getChildren(): List<UiInspectorCustomComponentChildProvider> {
    val count = component.accessibleChildrenCount
    val composeChildren = mutableListOf<UiInspectorCustomComponentChildProvider>()

    for (i in 0..count - 1) {
      val childComponent = component.getAccessibleChild(i).accessibleContext
      if (childComponent != null) {
        composeChildren.add(InspectorObject(childComponent))
      }
    }

    return composeChildren
  }

  override fun getObjectForProperties() = component

  override fun getPropertiesMethodList() = PROPERTIES

  override fun getUiInspectorContext() = emptyList<PropertyBean>()

  override fun getHighlightingBounds(): Rectangle? {
    if (component is AccessibleComponent) {
      return component.bounds
    }
    return null
  }
}