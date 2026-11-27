// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.actionSystem

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.swing.toIconDescriptor
import java.lang.reflect.Method
import java.util.function.Supplier

/**
 * Cached reflective bridge for `Presentation`'s experimental `IconDescriptor` accessors, which exist on monorepo head
 * (`platform/editor-ui-api`) but are absent in Gradle's `IU-2026.2.3` SDK.
 */
internal object PresentationIconDescriptors {
    private val getIconDescriptorMethod: Method? =
        runCatching { Presentation::class.java.getMethod("getIconDescriptor") }.getOrNull()
    private val getIconDescriptorSupplierMethod: Method? =
        runCatching { Presentation::class.java.getMethod("getIconDescriptorSupplier") }.getOrNull()
    private val setIconDescriptorMethod: Method? =
        runCatching { Presentation::class.java.getMethod("setIconDescriptor", IconDescriptor::class.java) }.getOrNull()
    private val setIconDescriptorSupplierMethod: Method? =
        runCatching { Presentation::class.java.getMethod("setIconDescriptorSupplier", Supplier::class.java) }
            .getOrNull()
    private val noIconDescriptor: Any? =
        runCatching { Presentation::class.java.getField("NO_ICON_DESCRIPTOR").get(null) }.getOrNull()

    fun Presentation.resolvedIconDescriptor(): IconDescriptor? {
        val method = getIconDescriptorMethod
        if (method != null) {
            val descriptor =
                try {
                    method.invoke(this)
                } catch (_: ReflectiveOperationException) {
                    null
                }
            if (descriptor is IconDescriptor && descriptor !== noIconDescriptor) {
                return descriptor
            }
        }
        return icon?.toIconDescriptor()
    }

    fun Presentation.setResolvedIconDescriptor(descriptor: IconDescriptor?) {
        val method = setIconDescriptorMethod ?: return
        try {
            method.invoke(this, descriptor)
        } catch (_: ReflectiveOperationException) {
            // Platform build does not expose setIconDescriptor.
        }
    }

    fun Presentation.restoreIconDescriptorFrom(template: Presentation) {
        val getter = getIconDescriptorSupplierMethod
        val setter = setIconDescriptorSupplierMethod
        if (getter != null && setter != null) {
            try {
                val templateSupplier = getter.invoke(template)
                setter.invoke(this, templateSupplier)
                return
            } catch (_: ReflectiveOperationException) {
                // Fall back to setIconDescriptor below.
            }
        }
        val descriptor =
            try {
                getIconDescriptorMethod?.invoke(template)
            } catch (_: ReflectiveOperationException) {
                null
            }
        if (descriptor !== noIconDescriptor) {
            setResolvedIconDescriptor(descriptor as? IconDescriptor)
        }
    }
}
