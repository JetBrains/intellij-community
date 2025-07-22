// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.bridge

import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorCustomComponentChildProvider
import com.intellij.internal.inspector.UiInspectorCustomComponentProvider
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.ui.UIUtil
import java.awt.Rectangle
import javax.accessibility.AccessibleComponent
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.event.AncestorEvent
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.swing.SkiaSwingLayer

@Suppress("FunctionName")
@OptIn(ExperimentalSkikoApi::class)
internal fun ComposeUiInspector(composePanel: JComponent) {
    val skiaComponent = findSkiaLayer(composePanel)

    if (skiaComponent == null) {
        composePanel.addAncestorListener(
            object : AncestorListenerAdapter() {
                override fun ancestorAdded(event: AncestorEvent) {
                    findSkiaLayer(composePanel)?.also {
                        composePanel.removeAncestorListener(this)
                        configureSkiaLayer(it)
                    }
                }
            }
        )
    } else {
        configureSkiaLayer(skiaComponent)
    }
}

@OptIn(ExperimentalSkikoApi::class)
private fun findSkiaLayer(composePanel: JComponent): SkiaSwingLayer? =
    UIUtil.findComponentOfType(composePanel, SkiaSwingLayer::class.java)

private fun configureSkiaLayer(skiaComponent: JComponent) {
    skiaComponent.putClientProperty(UiInspectorCustomComponentProvider.KEY, ComposeUiInspectorProvider(skiaComponent))
}

private class ComposeUiInspectorProvider(private val composePanel: JComponent) : UiInspectorCustomComponentProvider {
    override fun getChildren(): List<UiInspectorCustomComponentChildProvider> =
        getChildren(composePanel.accessibleContext)
}

private val PROPERTIES =
    listOf(
        "isEnabled",
        "isVisible",
        "isShowing",
        "getLocationOnScreen",
        "getLocation",
        "getBounds",
        "getSize",
        "isFocusTraversable",
        "requestFocus",
    )

private class InspectorObject(private val component: AccessibleContext) : UiInspectorCustomComponentChildProvider {
    override fun getTreeName(): String {
        val name = component.accessibleName ?: component.accessibleDescription
        if (name.isNullOrBlank()) {
            return component.toString()
        }
        return name
    }

    override fun getChildren(): List<UiInspectorCustomComponentChildProvider> = getChildren(component)

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

private fun getChildren(context: AccessibleContext?): List<UiInspectorCustomComponentChildProvider> {
    if (context == null) return emptyList()

    val count = context.accessibleChildrenCount
    return buildList(capacity = count) {
        for (i in 0 until count) {
            val childComponent = context.getAccessibleChild(i)?.accessibleContext
            if (childComponent != null) {
                add(InspectorObject(childComponent))
            }
        }
    }
}
