// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle
import java.util.UUID

/**
 * Shared JDK FFM helpers for testing macOS Objective-C / AppKit integrations in `ide-laf-bridge` without depending on
 * `Foundation` or JNA.
 *
 * Callers must guard execution with `assumeTrue(SystemInfoRt.isMac)` before referencing this object so AppKit and
 * `libobjc` are only loaded on macOS.
 */
internal object ObjCTestHelper {
    private val linker: Linker = Linker.nativeLinker()
    private val library: SymbolLookup =
        SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", Arena.global())
            .or(SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global()))
    private val message: MemorySegment = library.findOrThrow("objc_msgSend")

    private val getClass: MethodHandle =
        linker.downcallHandle(library.findOrThrow("objc_getClass"), FunctionDescriptor.of(ADDRESS, ADDRESS))
    private val objectGetClass: MethodHandle =
        linker.downcallHandle(library.findOrThrow("object_getClass"), FunctionDescriptor.of(ADDRESS, ADDRESS))
    private val registerSelector: MethodHandle =
        linker.downcallHandle(library.findOrThrow("sel_registerName"), FunctionDescriptor.of(ADDRESS, ADDRESS))
    private val allocateClass: MethodHandle =
        linker.downcallHandle(
            library.findOrThrow("objc_allocateClassPair"),
            FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
        )
    private val addMethod: MethodHandle =
        linker.downcallHandle(
            library.findOrThrow("class_addMethod"),
            FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS),
        )
    private val registerClass: MethodHandle =
        linker.downcallHandle(library.findOrThrow("objc_registerClassPair"), FunctionDescriptor.ofVoid(ADDRESS))
    private val disposeClass: MethodHandle =
        linker.downcallHandle(library.findOrThrow("objc_disposeClassPair"), FunctionDescriptor.ofVoid(ADDRESS))

    private val sendPointer: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
    private val sendPointer1: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS))
    private val sendPointerBool: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_BYTE))
    private val sendLong: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS))
    private val sendBool: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS))
    private val sendBool1: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS))
    private val sendVoid: MethodHandle = linker.downcallHandle(message, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
    private val sendVoid1: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS))
    private val sendVoid2: MethodHandle =
        linker.downcallHandle(message, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS))

    private val nsObjectClass: MemorySegment = objcClass("NSObject")
    private val nsAutoreleasePoolClass: MemorySegment = objcClass("NSAutoreleasePool")
    private val nsStringClass: MemorySegment = objcClass("NSString")
    private val nsUserDefaultsClass: MemorySegment = objcClass("NSUserDefaults")
    private val nsNotificationCenterClass: MemorySegment = objcClass("NSNotificationCenter")
    private val nsNumberClass: MemorySegment = objcClass("NSNumber")

    private val selAlloc: MemorySegment = selector("alloc")
    private val selInitWithUtf8String: MemorySegment = selector("initWithUTF8String:")
    private val selNew: MemorySegment = selector("new")
    private val selDrain: MemorySegment = selector("drain")
    private val selRetain: MemorySegment = selector("retain")
    private val selRelease: MemorySegment = selector("release")
    private val selMutableCopy: MemorySegment = selector("mutableCopy")
    private val selStandardUserDefaults: MemorySegment = selector("standardUserDefaults")
    private val selSynchronize: MemorySegment = selector("synchronize")
    private val selBoolForKey: MemorySegment = selector("boolForKey:")
    private val selVolatileDomainForName: MemorySegment = selector("volatileDomainForName:")
    private val selSetVolatileDomainForName: MemorySegment = selector("setVolatileDomain:forName:")
    private val selSetObjectForKey: MemorySegment = selector("setObject:forKey:")
    private val selNumberWithBool: MemorySegment = selector("numberWithBool:")
    private val selDefaultCenter: MemorySegment = selector("defaultCenter")
    private val selPostNotificationNameObject: MemorySegment = selector("postNotificationName:object:")

    private val strArgumentDomain: MemorySegment = nsString("NSArgumentDomain")

    internal inline fun <T> withAutoreleasePool(action: () -> T): T {
        val pool = createAutoreleasePool()
        try {
            return action()
        } finally {
            drainAutoreleasePool(pool)
        }
    }

    internal fun createAutoreleasePool(): MemorySegment =
        sendPointer.invokeExact(nsAutoreleasePoolClass, selNew) as MemorySegment

    internal fun drainAutoreleasePool(pool: MemorySegment) {
        sendVoid.invokeExact(pool, selDrain)
    }

    internal fun objcClass(name: String): MemorySegment =
        Arena.ofConfined().use { arena ->
            val cls = getClass.invokeExact(arena.allocateFrom(name)) as MemorySegment
            check(cls.address() != 0L) { "Cannot find Objective-C class: $name" }
            cls
        }

    internal fun selector(name: String): MemorySegment =
        Arena.ofConfined().use { arena -> registerSelector.invokeExact(arena.allocateFrom(name)) as MemorySegment }

    internal fun nsString(value: String): MemorySegment =
        Arena.ofConfined().use { arena ->
            val allocated = sendPointer.invokeExact(nsStringClass, selAlloc) as MemorySegment
            sendPointer1.invokeExact(allocated, selInitWithUtf8String, arena.allocateFrom(value)) as MemorySegment
        }

    internal fun msgSendLong(receiver: MemorySegment, selector: MemorySegment): Long =
        sendLong.invokeExact(receiver, selector) as Long

    internal fun msgSendVoid(receiver: MemorySegment, selector: MemorySegment, arg: MemorySegment) {
        withAutoreleasePool<Unit> { sendVoid1.invokeExact(receiver, selector, arg) }
    }

    internal fun postNotification(name: MemorySegment, sender: MemorySegment = MemorySegment.NULL) {
        withAutoreleasePool<Unit> {
            val center = sendPointer.invokeExact(nsNotificationCenterClass, selDefaultCenter) as MemorySegment
            sendVoid2.invokeExact(center, selPostNotificationNameObject, name, sender)
        }
    }

    internal fun readUserDefaultsBool(key: MemorySegment): Boolean {
        val defaults = sendPointer.invokeExact(nsUserDefaultsClass, selStandardUserDefaults) as MemorySegment
        sendBool.invokeExact(defaults, selSynchronize) as Byte
        val raw = sendBool1.invokeExact(defaults, selBoolForKey, key) as Byte
        return raw.toInt() != 0
    }

    internal fun withArgumentDomainBool(key: MemorySegment, value: Boolean, action: () -> Unit) {
        withAutoreleasePool {
            val defaults = sendPointer.invokeExact(nsUserDefaultsClass, selStandardUserDefaults) as MemorySegment
            val original =
                sendPointer1.invokeExact(defaults, selVolatileDomainForName, strArgumentDomain) as MemorySegment
            sendPointer.invokeExact(original, selRetain) as MemorySegment
            val replacement = sendPointer.invokeExact(original, selMutableCopy) as MemorySegment
            try {
                val boolByte: Byte = if (value) 1 else 0
                val nsNumber = sendPointerBool.invokeExact(nsNumberClass, selNumberWithBool, boolByte) as MemorySegment
                sendVoid2.invokeExact(replacement, selSetObjectForKey, nsNumber, key)
                sendVoid2.invokeExact(defaults, selSetVolatileDomainForName, replacement, strArgumentDomain)
                action()
            } finally {
                sendVoid2.invokeExact(defaults, selSetVolatileDomainForName, original, strArgumentDomain)
                sendVoid.invokeExact(replacement, selRelease)
                sendVoid.invokeExact(original, selRelease)
            }
        }
    }

    /**
     * Allocates and registers a unique `NSObject` subclass whose metaclass implements [selector] with signature `(id,
     * SEL) -> NSInteger` (`"q@:"`), backed by a global FFM upcall stub to [callback].
     */
    internal fun registerClassWithLongClassMethod(
        classNamePrefix: String,
        selector: MemorySegment,
        callback: MethodHandle,
    ): MemorySegment =
        Arena.ofConfined().use { arena ->
            val name = arena.allocateFrom("${classNamePrefix}_" + UUID.randomUUID().toString().replace("-", ""))
            val cls = allocateClass.invokeExact(nsObjectClass, name, 0L) as MemorySegment
            check(cls.address() != 0L) { "Cannot allocate test Objective-C class: $classNamePrefix" }
            var registered = false
            try {
                val metaclass = objectGetClass.invokeExact(cls) as MemorySegment
                val stub =
                    linker.upcallStub(callback, FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS), Arena.global())
                val added = addMethod.invokeExact(metaclass, selector, stub, arena.allocateFrom("q@:")) as Byte
                check(added.toInt() != 0) { "Cannot add class method to test metaclass: $classNamePrefix" }
                registerClass.invokeExact(cls)
                registered = true
                cls
            } finally {
                if (!registered) disposeClass.invokeExact(cls)
            }
        }
}
