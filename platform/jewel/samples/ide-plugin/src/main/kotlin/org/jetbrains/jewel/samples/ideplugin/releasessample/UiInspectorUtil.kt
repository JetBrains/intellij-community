package org.jetbrains.jewel.samples.ideplugin.releasessample

import com.intellij.internal.inspector.PropertyBean
import com.intellij.internal.inspector.UiInspectorUtil
import javax.swing.JComponent

fun JComponent.registerUiInspectorInfoProvider(provider: () -> Map<String, Any?>) {
    UiInspectorUtil.registerProvider(this) {
        provider().map { (key, value) -> PropertyBean(key, value) }
    }
}
