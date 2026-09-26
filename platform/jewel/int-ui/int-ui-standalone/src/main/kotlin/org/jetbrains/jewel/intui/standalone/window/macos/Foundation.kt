// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.logging.Level
import java.util.logging.Logger
import org.jetbrains.jewel.intui.standalone.window.JnaLoader
import org.jetbrains.jewel.intui.standalone.window.macos.FoundationLibrary.Companion.kCFStringEncodingUTF16LE

/**
 * Kotlin wrapper for macOS Foundation framework and Objective-C runtime functions.
 *
 * This is a direct conversion of IntelliJ's [com.intellij.ui.mac.foundation.Foundation] class.
 *
 * This object provides access to the Objective-C runtime through JNA (Java Native Access), allowing Kotlin code to
 * interact with macOS native APIs. It handles:
 * - Loading the Foundation framework via JNA
 * - Sending messages to Objective-C objects via `objc_msgSend`
 * - Creating and managing Objective-C classes and selectors
 * - Converting between Kotlin and Objective-C types
 *
 * All methods return nullable types when the underlying Foundation library fails to load, allowing graceful degradation
 * when JNA is not available or the library cannot be loaded.
 */
internal object Foundation {
    private val logger = Logger.getLogger(Foundation::class.java.simpleName)

    init {
        if (!JnaLoader.isLoaded) {
            logger.log(Level.WARNING, "JNA is not loaded")
        }
    }

    private val myFoundationLibrary: FoundationLibrary? by lazy {
        try {
            Native.load("Foundation", FoundationLibrary::class.java, Collections.singletonMap("jna.encoding", "UTF8"))
        } catch (_: Throwable) {
            null
        }
    }

    private val myObjcMsgSend: Function? by lazy {
        try {
            (Proxy.getInvocationHandler(myFoundationLibrary) as Library.Handler)
                .nativeLibrary
                .getFunction("objc_msgSend")
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Gets the Objective-C class object for the given class name.
     *
     * @param className The name of the Objective-C class (e.g., "NSString", "NSObject")
     * @return The class ID, or null if the Foundation library is not loaded or the class doesn't exist
     */
    fun getObjcClass(className: String?): ID? = myFoundationLibrary?.objc_getClass(className)

    /**
     * Gets the Objective-C protocol object for the given protocol name.
     *
     * @param name The name of the protocol
     * @return The protocol ID, or null if the Foundation library is not loaded or the protocol doesn't exist
     */
    fun getProtocol(name: String?): ID? = myFoundationLibrary?.objc_getProtocol(name)

    /**
     * Creates an Objective-C selector from the given selector string.
     *
     * @param s The selector name (e.g., "alloc", "init", "handleScrollerStyleChanged:")
     * @return A pointer to the registered selector, or null if the Foundation library is not loaded
     */
    fun createSelector(s: String?): Pointer? = myFoundationLibrary?.sel_registerName(s)

    private fun prepInvoke(id: ID?, selector: Pointer?, args: Array<out Any?>): Array<Any?> {
        val invokArgs = arrayOfNulls<Any>(args.size + 2)
        invokArgs[0] = id
        invokArgs[1] = selector
        System.arraycopy(args, 0, invokArgs, 2, args.size)
        return invokArgs
    }

    /**
     * Sends a message to an Objective-C object using `objc_msgSend`.
     *
     * This is the primary method for invoking Objective-C methods. It uses the correct calling convention for the
     * target platform (x86_64 vs ARM64).
     *
     * Note: On x86_64, varargs and non-varargs methods use the same calling convention, but on ARM64 they differ. This
     * method uses the non-varargs convention. For varargs selectors, use [invokeVarArg].
     *
     * @param id The receiver object (or class for class methods)
     * @param selector The method selector to invoke
     * @param args Arguments to pass to the method
     * @return The result as an [ID], or [ID.NIL] if the message send fails
     */
    operator fun invoke(id: ID?, selector: Pointer?, vararg args: Any?): ID =
        ID(myObjcMsgSend?.invokeLong(prepInvoke(id, selector, args)) ?: 0)

    /**
     * Invokes the given vararg selector. Expects `NSArray arrayWithObjects:(id), ...` like signature, i.e. exactly one
     * fixed argument, followed by varargs.
     *
     * @param id The receiver object (or class for class methods)
     * @param selector The method selector to invoke
     * @param args Arguments to pass to the method
     * @return The result as an [ID], or [ID.NIL] if the message send fails
     */
    fun invokeVarArg(id: ID?, selector: Pointer?, vararg args: Any?) =
        // c functions and objc methods have at least 1 fixed argument, we therefore need to
        // separate out the first argument
        myFoundationLibrary?.objc_msgSend(id, selector, args[0], *args.copyOfRange(1, args.size)) ?: ID.NIL

    /**
     * Convenience method to invoke a method on a class by name.
     *
     * @param cls The class name
     * @param selector The selector name as a string
     * @param args Arguments to pass to the method
     * @return The result as an [ID]
     */
    operator fun invoke(cls: String?, selector: String?, vararg args: Any?): ID =
        invoke(getObjcClass(cls), createSelector(selector), *args)

    /**
     * Convenience method to invoke a varargs method on a class by name.
     *
     * @param cls The class name
     * @param selector The selector name as a string
     * @param args Arguments to pass to the method (first arg is fixed, rest are varargs)
     * @return The result as an [ID]
     */
    fun invokeVarArg(cls: String?, selector: String?, vararg args: Any?): ID =
        invokeVarArg(getObjcClass(cls), createSelector(selector), *args)

    /**
     * Safely invokes a method, first checking if the receiver responds to the selector.
     *
     * @param stringCls The class name
     * @param stringSelector The selector name as a string
     * @param args Arguments to pass to the method
     * @return The result as an [ID]
     * @throws IllegalStateException if the class does not respond to the selector
     */
    fun safeInvoke(stringCls: String?, stringSelector: String?, vararg args: Any?): ID {
        val cls = getObjcClass(stringCls)
        val selector = createSelector(stringSelector)
        if (!invoke(cls, "respondsToSelector:", selector).booleanValue()) {
            error("Missing selector $stringSelector for $stringCls")
        }
        return invoke(cls, selector, *args)
    }

    /**
     * Convenience method to invoke a method on an object with a selector string.
     *
     * @param id The receiver object
     * @param selector The selector name as a string
     * @param args Arguments to pass to the method
     * @return The result as an [ID]
     */
    operator fun invoke(id: ID?, selector: String?, vararg args: Any?): ID = invoke(id, createSelector(selector), *args)

    /**
     * Allocates a new Objective-C class as a subclass of the given superclass.
     *
     * @param superCls The superclass ID
     * @param name The name for the new class
     * @return The new class ID, or null if allocation fails or the Foundation library is not loaded
     */
    fun allocateObjcClassPair(superCls: ID, name: String) =
        myFoundationLibrary?.objc_allocateClassPair(superCls, name, 0)

    /**
     * Adds a method to an Objective-C class.
     *
     * @param cls The class to add the method to
     * @param selectorName The selector for the method
     * @param impl The callback implementation for the method
     * @param types The Objective-C type encoding string (e.g., "v@" for void return, object receiver)
     * @return true if the method was added successfully, false otherwise
     */
    fun addMethod(cls: ID, selectorName: Pointer, impl: Callback, types: String): Boolean =
        myFoundationLibrary?.class_addMethod(cls, selectorName, impl, types) ?: false

    /**
     * Registers a newly created Objective-C class with the runtime.
     *
     * Must be called after allocating a class pair and adding methods, before the class can be used.
     *
     * @param cls The class to register
     */
    fun registerObjcClassPair(cls: ID) {
        myFoundationLibrary?.objc_registerClassPair(cls)
    }

    /**
     * Converts a CoreFoundation string encoding constant to an NSString encoding constant.
     *
     * @param cfEncoding The CoreFoundation encoding constant
     * @return The NSString encoding constant, or null if conversion fails or the Foundation library is not loaded
     */
    fun convertCFEncodingToNs(cfEncoding: Long) =
        myFoundationLibrary
            ?.CFStringConvertEncodingToNSStringEncoding(cfEncoding)
            ?.and(0xffffffffffL) // trim to C-type limits

    /**
     * Converts a Kotlin String to an Objective-C NSString object.
     *
     * The string is encoded as UTF-16LE and wrapped in an autoreleased NSString object.
     *
     * @param s The Kotlin string to convert
     * @return An [ID] referencing the NSString object, or [ID.NIL] if conversion fails
     */
    fun nsString(s: String?): ID {
        if (s == null || myFoundationLibrary == null) return ID.NIL

        if (s.isEmpty()) {
            return invoke(getObjcClass("NSString"), createSelector("string"))
        }

        val allocSel = createSelector("alloc")
        val initWithBytesSel = createSelector("initWithBytes:length:encoding:")
        val autoreleaseSel = createSelector("autorelease")

        val nsStringClass = getObjcClass("NSString")
        val rawObject = invoke(nsStringClass, allocSel)

        val stringBytes = s.toByteArray(Charsets.UTF_16LE)
        val initializedNsString =
            invoke(
                rawObject,
                initWithBytesSel,
                stringBytes,
                stringBytes.size,
                convertCFEncodingToNs(kCFStringEncodingUTF16LE),
            )

        return invoke(initializedNsString, autoreleaseSel)
    }

    /**
     * Creates a new Objective-C delegate class that can receive notifications or callbacks.
     *
     * This method:
     * 1. Creates a new class as a subclass of NSObject with the given name
     * 2. Adds a method with the specified selector that delegates to the provided callback
     * 3. Registers the class with the Objective-C runtime
     * 4. Creates and returns a new instance of the class
     *
     * The created delegate can be registered as an observer with NSNotificationCenter or used as a callback target for
     * other Objective-C APIs.
     *
     * @param name The name for the new delegate class (should be unique)
     * @param pointer The selector pointer for the callback method
     * @param callback The JNA Callback implementation that will be invoked
     * @return An [ID] referencing the new delegate instance, or [ID.NIL] if creation fails
     * @throws RuntimeException if the method cannot be added to the class
     */
    fun createDelegate(name: String, pointer: Pointer, callback: Callback): ID {
        val nsObjectClass = getObjcClass("NSObject") ?: return ID.NIL
        val delegateClass = allocateObjcClassPair(nsObjectClass, name)
        if (ID.NIL != delegateClass && delegateClass != null) {
            if (!addMethod(delegateClass, pointer, callback, "v@")) {
                @Suppress("detekt:TooGenericExceptionThrown") // Copied from IJP
                throw RuntimeException("Cannot add observer method")
            }
            registerObjcClassPair(delegateClass)
        }
        return invoke(name, "new")
    }

    /**
     * Wrapper for NSAutoreleasePool, used to manage autorelease memory in Objective-C.
     *
     * Create an instance at the start of a block of Objective-C operations and call [drain] when finished to release
     * any autoreleased objects created during that time.
     *
     * Example usage:
     * ```kotlin
     * val pool = Foundation.NSAutoreleasePool()
     * try {
     *     // Objective-C operations here
     * } finally {
     *     pool.drain()
     * }
     * ```
     */
    class NSAutoreleasePool {
        private val myDelegate: ID = invoke(invoke("NSAutoreleasePool", "alloc"), "init")

        /**
         * Drains the autorelease pool, releasing all autoreleased objects.
         *
         * Should be called when finished with a block of Objective-C operations to clean up memory.
         */
        fun drain() {
            invoke(myDelegate, "drain")
        }
    }
}
