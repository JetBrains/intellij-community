@file:Suppress("SwallowedException", "TooGenericExceptionCaught")
package org.jetbrains.jewel.themes.expui.desktop.util

import sun.misc.Unsafe
import java.lang.reflect.AccessibleObject

internal object UnsafeAccessing {

    private val unsafe: Any? by lazy {
        try {
            val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            theUnsafe.get(null) as Unsafe
        } catch (e: Throwable) {
            null
        }
    }

    val desktopModule by lazy {
        ModuleLayer.boot().findModule("java.desktop").get()
    }

    val ownerModule by lazy {
        this.javaClass.module
    }

    private val isAccessibleFieldOffset: Long? by lazy {
        try {
            (unsafe as? Unsafe)?.objectFieldOffset(Parent::class.java.getDeclaredField("first"))
        } catch (e: Throwable) {
            null
        }
    }

    private val implAddOpens by lazy {
        try {
            Module::class.java.getDeclaredMethod(
                "implAddOpens", String::class.java, Module::class.java
            ).accessible()
        } catch (e: Throwable) {
            null
        }
    }

    fun assignAccessibility(obj: AccessibleObject) {
        try {
            val theUnsafe = unsafe as? Unsafe ?: return
            val offset = isAccessibleFieldOffset ?: return
            theUnsafe.putBooleanVolatile(obj, offset, true)
        } catch (e: Throwable) {
            // ignore
        }
    }

    fun assignAccessibility(module: Module, packages: List<String>) {
        try {
            packages.forEach {
                implAddOpens?.invoke(module, it, ownerModule)
            }
        } catch (e: Throwable) {
            // ignore
        }
    }

    private class Parent {

        var first = false

        @Volatile
        var second: Any? = null
    }
}

internal fun <T : AccessibleObject> T.accessible(): T {
    return apply {
        UnsafeAccessing.assignAccessibility(this)
    }
}
