// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.component

import java.lang.reflect.Method
import org.jetbrains.jewel.ui.component.PopupRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for [JBPopupRenderer]: the shape of its [PopupRenderer] implementation, not its behaviour. Behaviour
 * needs a running IDE and a display, because it builds a real `JBPopup` through `JBPopupFactory`; that is what the
 * `jewel-bridge-e2e-tests` lane covers.
 */
internal class JBPopupRendererTest {
    @Test
    fun `overrides every InputMode-aware Popup overload`() {
        assertEquals(
            "PopupRenderer is expected to declare exactly two InputMode-aware Popup overloads",
            2,
            inputModeAwareOverloads.size,
        )

        val inherited = inputModeAwareOverloads.filterNot { it.isOverriddenByBridge() }

        assertTrue(
            "JBPopupRenderer must declare its own override of every InputMode-aware PopupRenderer.Popup overload. An " +
                "inherited one falls through to the deprecated overloads, which drop the InputMode and end in an " +
                "error. Inherited: " +
                inherited.joinToString { it.toGenericString() },
            inherited.isEmpty(),
        )
    }

    @Test
    fun `implements the windowShape-aware overload`() {
        val (withoutShape, windowShapeAware) = inputModeAwareOverloads.sortedBy { it.parameterCount }

        assertEquals(
            "The windowShape-aware overload is expected to take exactly one parameter more than the one without it",
            withoutShape.parameterCount + 1,
            windowShapeAware.parameterCount,
        )
        assertTrue(
            "The extra parameter is expected to be the IntSize -> Shape factory, but the overload is: " +
                windowShapeAware.toGenericString(),
            windowShapeAware.toGenericString().contains("IntSize") &&
                windowShapeAware.toGenericString().contains("java.awt.Shape"),
        )
        assertTrue(
            "JBPopupRenderer must implement the windowShape-aware overload, even though it ignores the shape: " +
                "JBPopup does not expose native window shaping",
            windowShapeAware.isOverriddenByBridge(),
        )
    }

    /**
     * The overloads declared by [PopupRenderer] itself whose `onDismissRequest` reports the dismissal `InputMode`,
     * minus the synthetic bridge the Compose compiler emits to carry the `windowShape` default value. The deprecated
     * overloads without an `InputMode` only exist for renderers written before it, which the bridge is not.
     */
    private val inputModeAwareOverloads: List<Method>
        get() =
            PopupRenderer::class.java.declaredMethods.filter {
                it.name == "Popup" && !it.isSynthetic && it.genericParameterTypes[2].typeName.contains("InputMode")
            }

    /**
     * Whether [JBPopupRenderer] declares this overload itself. A compiler-generated bridge does not count: depending on
     * the JVM default-method mode, Kotlin emits one in the implementing class for every inherited interface method, and
     * it would make an override that is missing from the source look present.
     */
    private fun Method.isOverriddenByBridge(): Boolean =
        runCatching { JBPopupRenderer::class.java.getDeclaredMethod(name, *parameterTypes) }
            .getOrNull()
            ?.let { !it.isBridge && !it.isSynthetic } == true
}
