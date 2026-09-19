// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.component

import androidx.compose.runtime.Composer
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
    fun `overrides every PopupRenderer Popup overload`() {
        val inherited = popupOverloads.filterNot { it.isOverriddenByBridge() }

        assertTrue(
            "JBPopupRenderer must declare its own override of every PopupRenderer.Popup overload, or the interface " +
                "default and the bridge's deprecated overload will delegate to each other forever. Inherited: " +
                inherited.joinToString { it.toGenericString() },
            inherited.isEmpty(),
        )
    }

    @Test
    fun `implements the windowShape-aware overload`() {
        assertEquals("PopupRenderer is expected to declare exactly two Popup overloads", 2, popupOverloads.size)

        val (deprecated, windowShapeAware) = popupOverloads.sortedBy { it.parameterCount }

        assertEquals(
            "The windowShape-aware overload is expected to take exactly one parameter more than the deprecated one",
            deprecated.parameterCount + 1,
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

    @Test
    fun `keeps both overloads composable`() {
        val notComposable =
            JBPopupRenderer::class
                .java
                .declaredMethods
                .filter { it.name == "Popup" && !it.isSynthetic }
                .filterNot { method -> method.parameterTypes.any { it == Composer::class.java } }

        assertTrue(
            "Every JBPopupRenderer.Popup override must stay @Composable, or it cannot render a popup at all. " +
                "Not composable: " +
                notComposable.joinToString { it.toGenericString() },
            notComposable.isEmpty(),
        )
    }

    /**
     * The overloads declared by [PopupRenderer] itself, minus the synthetic bridge the Compose compiler emits to carry
     * the `windowShape` default value.
     */
    private val popupOverloads: List<Method>
        get() = PopupRenderer::class.java.declaredMethods.filter { it.name == "Popup" && !it.isSynthetic }

    private fun Method.isOverriddenByBridge(): Boolean =
        runCatching { JBPopupRenderer::class.java.getDeclaredMethod(name, *parameterTypes) }.isSuccess
}
