// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Regression test for JEWEL-1387: the reflective lookups used to resolve the native window must search the whole class
 * hierarchy, not just the runtime class.
 *
 * On JBR 25 the AWT window peer is `sun.lwawt.macosx.LWCWindowPeer`, a subclass of `sun.lwawt.LWWindowPeer` that
 * inherits (but does not declare) `getPlatformWindow()`. A `getDeclaredMethod` call on the runtime class alone throws
 * [NoSuchMethodException] and silently breaks native window chrome updates on macOS.
 */
internal class MacPlatformServicesReflectionTest {
    @Suppress("UnusedPrivateMember", "FunctionOnlyReturningConstant")
    private open class BasePeer {
        @JvmField internal val ptr: Long = 42L

        fun getPlatformWindow(): Any = this
    }

    // Mimics JBR 25's LWCWindowPeer: inherits getPlatformWindow() and ptr without declaring them
    private class SubclassPeer : BasePeer()

    @Test
    fun `findMethodInHierarchy finds method declared on the class itself`() {
        val method = MacPlatformServicesDefaultImpl.findMethodInHierarchy(BasePeer::class.java, "getPlatformWindow")

        assertNotNull(method, "Should find a method declared directly on the class")
    }

    @Test
    fun `findMethodInHierarchy finds method inherited from a superclass`() {
        val method = MacPlatformServicesDefaultImpl.findMethodInHierarchy(SubclassPeer::class.java, "getPlatformWindow")

        assertNotNull(method, "Should find a method declared on a superclass (JBR 25 LWCWindowPeer scenario)")
        assertEquals(BasePeer::class.java, method.declaringClass, "Method should be resolved from the superclass")
    }

    @Test
    fun `findMethodInHierarchy returns null for a missing method`() {
        val method = MacPlatformServicesDefaultImpl.findMethodInHierarchy(SubclassPeer::class.java, "doesNotExist")

        assertNull(method, "Should return null instead of throwing for missing methods")
    }

    @Test
    fun `findFieldInHierarchy finds field declared on the class itself`() {
        val field = MacPlatformServicesDefaultImpl.findFieldInHierarchy(BasePeer::class.java, "ptr")

        assertNotNull(field, "Should find a field declared directly on the class")
    }

    @Test
    fun `findFieldInHierarchy finds field inherited from a superclass`() {
        val field = MacPlatformServicesDefaultImpl.findFieldInHierarchy(SubclassPeer::class.java, "ptr")

        assertNotNull(field, "Should find a field declared on a superclass")
        assertEquals(BasePeer::class.java, field.declaringClass, "Field should be resolved from the superclass")
    }

    @Test
    fun `findFieldInHierarchy returns null for a missing field`() {
        val field = MacPlatformServicesDefaultImpl.findFieldInHierarchy(SubclassPeer::class.java, "doesNotExist")

        assertNull(field, "Should return null instead of throwing for missing fields")
    }
}
