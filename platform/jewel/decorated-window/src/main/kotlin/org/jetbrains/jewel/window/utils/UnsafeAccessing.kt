package org.jetbrains.jewel.window.utils

import java.lang.reflect.AccessibleObject
import java.util.logging.Level
import java.util.logging.Logger
import sun.misc.Unsafe

internal object UnsafeAccessing {
    private val logger = Logger.getLogger(UnsafeAccessing::class.java.simpleName)

    private val unsafe: Any? by lazy {
        try {
            val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
            theUnsafe.isAccessible = true
            theUnsafe.get(null) as Unsafe
        } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
            logger.log(Level.WARNING, "Unsafe accessing initializing failed.", error)
            null
        }
    }

    val desktopModule by lazy { ModuleLayer.boot().findModule("java.desktop").get() }

    val ownerModule: Module by lazy { this.javaClass.module }

    private val isAccessibleFieldOffset: Long? by lazy {
        try {
            (unsafe as? Unsafe)?.objectFieldOffset(Parent::class.java.getDeclaredField("first"))
        } catch (_: Throwable) {
            null
        }
    }

    private val implAddOpens by lazy {
        try {
            Module::class.java.getDeclaredMethod("implAddOpens", String::class.java, Module::class.java).accessible()
        } catch (_: Throwable) {
            null
        }
    }

    fun assignAccessibility(obj: AccessibleObject) {
        try {
            val theUnsafe = unsafe as? Unsafe ?: return
            val offset = isAccessibleFieldOffset ?: return
            theUnsafe.putBooleanVolatile(obj, offset, true)
        } catch (_: Throwable) {
            // ignore
        }
    }

    fun assignAccessibility(module: Module, packages: List<String>) {
        try {
            packages.forEach { implAddOpens?.invoke(module, it, ownerModule) }
        } catch (_: Throwable) {
            // ignore
        }
    }

    private class Parent {
        var first = false

        @Volatile var second: Any? = null
    }
}

internal fun <T : AccessibleObject> T.accessible(): T = apply { UnsafeAccessing.assignAccessibility(this) }
