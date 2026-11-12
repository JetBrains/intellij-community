// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_LONG
import java.lang.invoke.MethodHandle

// Typed wrappers around raw Objective-C pointers. They only ever get unwrapped (`.segment`) in this file.

/** Anything that can receive an Objective-C message: a class or an object (Objective-C's `id`). */
internal sealed interface ObjcId {
    val segment: MemorySegment
}

@JvmInline internal value class ObjcClass(override val segment: MemorySegment) : ObjcId

@JvmInline internal value class ObjcObject(override val segment: MemorySegment) : ObjcId

@JvmInline internal value class NSString(override val segment: MemorySegment) : ObjcId

@JvmInline internal value class Selector(val segment: MemorySegment)

@JvmInline internal value class UpcallStub(val segment: MemorySegment)

// Classes and selectors

private val objcGetClassHandle: MethodHandle by lazy { link("objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS)) }

private val selRegisterNameHandle: MethodHandle by lazy {
    link("sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS))
}

/** @throws IllegalStateException if no class named [name] is registered with the Objective-C runtime. */
internal fun objcGetClass(name: String): ObjcClass {
    val result = callWithCString(objcGetClassHandle, name)

    check(result != MemorySegment.NULL) { "Cannot find the Objective-C class: $name" }

    return ObjcClass(result)
}

internal fun selRegisterName(selector: String): Selector = Selector(callWithCString(selRegisterNameHandle, selector))

// objc_msgSend, one handle per method signature. Each function is named after the method's return type.

private val msgSendIdHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS))
}

private val msgSendIdWithPointerHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS))
}

private val msgSendLongHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS))
}

private val msgSendBoolHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS))
}

private val msgSendBoolWithPointerHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS))
}

private val msgSendVoidHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS))
}

private val msgSendVoidWithBoolHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_BYTE))
}

private val msgSendVoidWithTwoPointersHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS))
}

private val msgSendVoidWithFourPointersHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
}

private val msgSendVoidWithFourPointersAndLongHandle: MethodHandle by lazy {
    link("objc_msgSend", FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG))
}

internal fun objcMsgSend(receiver: ObjcId, sel: Selector): ObjcObject =
    ObjcObject(msgSendIdHandle.invokeExact(receiver.segment, sel.segment) as MemorySegment)

private fun objcMsgSend(receiver: ObjcId, sel: Selector, arg: MemorySegment): ObjcObject =
    ObjcObject(msgSendIdWithPointerHandle.invokeExact(receiver.segment, sel.segment, arg) as MemorySegment)

internal fun objcMsgSendLong(receiver: ObjcId, sel: Selector): Long =
    msgSendLongHandle.invokeExact(receiver.segment, sel.segment) as Long

internal fun objcMsgSendBool(receiver: ObjcId, sel: Selector): Boolean =
    (msgSendBoolHandle.invokeExact(receiver.segment, sel.segment) as Byte).toObjcBool()

internal fun objcMsgSendBoolWithPointer(receiver: ObjcId, sel: Selector, arg: ObjcId): Boolean =
    (msgSendBoolWithPointerHandle.invokeExact(receiver.segment, sel.segment, arg.segment) as Byte).toObjcBool()

internal fun objcMsgSendBoolWithPointer(receiver: ObjcId, sel: Selector, arg: Selector): Boolean =
    (msgSendBoolWithPointerHandle.invokeExact(receiver.segment, sel.segment, arg.segment) as Byte).toObjcBool()

internal fun objcMsgSendVoid(receiver: ObjcId, sel: Selector) {
    msgSendVoidHandle.invokeExact(receiver.segment, sel.segment)
}

internal fun objcMsgSendVoid(receiver: ObjcId, sel: Selector, arg: Boolean) {
    msgSendVoidWithBoolHandle.invokeExact(receiver.segment, sel.segment, if (arg) 1.toByte() else 0.toByte())
}

internal fun objcMsgSendVoid(receiver: ObjcId, sel: Selector, firstArg: ObjcId, secondArg: ObjcId?) {
    msgSendVoidWithTwoPointersHandle.invokeExact(
        receiver.segment,
        sel.segment,
        firstArg.segment,
        secondArg.segmentOrNil(),
    )
}

internal fun objcMsgSendVoid(
    receiver: ObjcId,
    sel: Selector,
    observer: ObjcId,
    observerSel: Selector,
    name: NSString,
    anObject: ObjcId?,
) {
    msgSendVoidWithFourPointersHandle.invokeExact(
        receiver.segment,
        sel.segment,
        observer.segment,
        observerSel.segment,
        name.segment,
        anObject.segmentOrNil(),
    )
}

internal fun objcMsgSendVoid(
    receiver: ObjcId,
    sel: Selector,
    observer: ObjcId,
    observerSel: Selector,
    name: NSString,
    anObject: ObjcId?,
    longArg: Long,
) {
    msgSendVoidWithFourPointersAndLongHandle.invokeExact(
        receiver.segment,
        sel.segment,
        observer.segment,
        observerSel.segment,
        name.segment,
        anObject.segmentOrNil(),
        longArg,
    )
}

// Creating classes at runtime

private val objcAllocateClassPairHandle: MethodHandle by lazy {
    link("objc_allocateClassPair", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_LONG))
}

private val classAddMethodHandle: MethodHandle by lazy {
    link("class_addMethod", FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS))
}

private val objcRegisterClassPairHandle: MethodHandle by lazy {
    link("objc_registerClassPair", FunctionDescriptor.ofVoid(ADDRESS))
}

/** @throws IllegalStateException if the class cannot be allocated, typically because [name] is already in use. */
internal fun objcAllocateClassPair(superclass: ObjcClass, name: String): ObjcClass {
    Arena.ofConfined().use { arena ->
        val allocatedClass =
            objcAllocateClassPairHandle.invokeExact(superclass.segment, arena.allocateFrom(name), 0L) as MemorySegment

        check(allocatedClass != MemorySegment.NULL) { "Cannot allocate Objective-C class $name (name already in use?)" }

        return ObjcClass(allocatedClass)
    }
}

internal fun classAddMethod(cls: ObjcClass, sel: Selector, imp: UpcallStub, types: String): Boolean {
    Arena.ofConfined().use { arena ->
        val added =
            classAddMethodHandle.invokeExact(cls.segment, sel.segment, imp.segment, arena.allocateFrom(types)) as Byte
        return added.toObjcBool()
    }
}

internal fun objcRegisterClassPair(cls: ObjcClass) {
    objcRegisterClassPairHandle.invokeExact(cls.segment)
}

/**
 * Turns [handle] into a C function pointer usable as an Objective-C method implementation (`IMP`) with the signature
 * `void (id self, SEL _cmd, id arg)`.
 *
 * The stub lives in the global arena, so it is never freed.
 */
internal fun createUpcallStub(handle: MethodHandle): UpcallStub =
    UpcallStub(linker.upcallStub(handle, FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS), Arena.global()))

// Strings

/**
 * Creates an autoreleased `NSString` with the contents of [string].
 *
 * The string is released when the enclosing [withAutoreleasePool] drains, so callers that need it to outlive the pool
 * must retain it.
 */
internal fun nsString(string: String): NSString {
    Arena.ofConfined().use { arena ->
        val allocated = objcMsgSend(objcGetClass("NSString"), selRegisterName("alloc"))
        val initialized = objcMsgSend(allocated, selRegisterName("initWithUTF8String:"), arena.allocateFrom(string))

        return NSString(objcMsgSend(initialized, selRegisterName("autorelease")).segment)
    }
}

// Linking

private val linker = Linker.nativeLinker()

private val symbolLookup by lazy {
    SymbolLookup.libraryLookup("/System/Library/Frameworks/AppKit.framework/AppKit", Arena.global())
        .or(SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global()))
}

private fun link(symbolName: String, functionDescriptor: FunctionDescriptor): MethodHandle =
    linker.downcallHandle(symbolLookup.find(symbolName).orElseThrow(), functionDescriptor)

private fun callWithCString(handle: MethodHandle, value: String): MemorySegment =
    Arena.ofConfined().use { arena -> handle.invokeExact(arena.allocateFrom(value)) as MemorySegment }

private fun Byte.toObjcBool(): Boolean = this != 0.toByte()

private fun ObjcId?.segmentOrNil(): MemorySegment = this?.segment ?: MemorySegment.NULL
