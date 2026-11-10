// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import java.lang.foreign.MemorySegment
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS

@EnabledOnOs(OS.MAC)
internal class ObjcRuntimeTest {
    @Test
    fun `objcGetClass throws for a class that does not exist`() {
        assertFailsWith<IllegalStateException> { objcGetClass("JewelClassThatDoesNotExist") }
    }

    @Test
    fun `objcGetClass finds an existing class`() {
        assertNotEquals(MemorySegment.NULL, objcGetClass("NSObject").segment)
    }

    @Test
    fun `objcMsgSend returns a new object and objcMsgSendVoid releases it`() {
        val instance = objcMsgSend(objcGetClass("NSObject"), selRegisterName("new"))

        assertNotEquals(MemorySegment.NULL, instance.segment)

        objcMsgSendVoid(instance, selRegisterName("release"))
    }

    @Test
    fun `objcMsgSendLong returns the length of an NSString`() {
        val length = withAutoreleasePool { objcMsgSendLong(nsString("Jewel"), selRegisterName("length")) }

        assertEquals(5L, length)
    }

    @Test
    fun `objcMsgSendBool returns false for a method answering NO`() {
        val instance = objcMsgSend(objcGetClass("NSObject"), selRegisterName("new"))
        try {
            assertFalse(objcMsgSendBool(instance, selRegisterName("isProxy")))
        } finally {
            objcMsgSendVoid(instance, selRegisterName("release"))
        }
    }

    @Test
    fun `objcMsgSendBoolWithPointer passes an object argument`() {
        val isEqual = withAutoreleasePool {
            objcMsgSendBoolWithPointer(nsString("Jewel"), selRegisterName("isEqualToString:"), nsString("Jewel"))
        }

        assertTrue(isEqual)
    }

    @Test
    fun `objcMsgSendBoolWithPointer passes a selector argument`() {
        val nsObject = objcGetClass("NSObject")
        val respondsToSelector = selRegisterName("respondsToSelector:")

        assertTrue(objcMsgSendBoolWithPointer(nsObject, respondsToSelector, selRegisterName("description")))
        assertFalse(objcMsgSendBoolWithPointer(nsObject, respondsToSelector, selRegisterName("jewelDoesNotExist")))
    }

    @Test
    fun `objcAllocateClassPair throws when the class name is already in use`() {
        assertFailsWith<IllegalStateException> { objcAllocateClassPair(objcGetClass("NSObject"), "NSObject") }
    }

    @Test
    fun `a registered class pair can be instantiated`() {
        val className = "JewelObjcRuntimeTest_" + UUID.randomUUID().toString().replace("-", "")
        val cls = objcAllocateClassPair(objcGetClass("NSObject"), className)
        objcRegisterClassPair(cls)

        assertEquals(cls, objcGetClass(className))

        val instance = objcMsgSend(cls, selRegisterName("new"))
        assertNotEquals(MemorySegment.NULL, instance.segment)
        objcMsgSendVoid(instance, selRegisterName("release"))
    }
}
