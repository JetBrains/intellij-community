package com.intellij.notebooks.visualization.outputs

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.notebooks.visualization.SwingClientProperty
import com.intellij.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.Component

internal var EditorGutterComponentEx.hoveredCollapsingComponentRect: CollapsingComponent? by SwingClientProperty("hoveredCollapsingComponentRect")

/**
 * [component] is any component that belongs to an output inlay.
 * If the component is null or seems to be not inside an output inlay, nothing happens.
 */
fun resetOutputInlayCustomHeight(component: Component?) {
  generateSequence(component, Component::getParent)
    .filterIsInstance<CollapsingComponent>()
    .firstOrNull()
    ?.resetCustomHeight()
}