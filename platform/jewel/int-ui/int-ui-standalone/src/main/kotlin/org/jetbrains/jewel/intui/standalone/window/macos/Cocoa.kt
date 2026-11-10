// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

import java.lang.invoke.MethodHandle

// Memory management

/**
 * Runs [block] inside an `NSAutoreleasePool` and returns its result.
 *
 * Objective-C objects autoreleased while [block] runs are released when the pool is drained. The pool is always
 * drained, including when [block] throws.
 *
 * This function does NOT catch exceptions, so anything thrown by [block] is rethrown to the caller after the pool has
 * been drained.
 *
 * MUST only be called on macOS, as it calls into the Objective-C runtime.
 *
 * @throws IllegalStateException if the `NSAutoreleasePool` class cannot be found.
 */
internal inline fun <T> withAutoreleasePool(block: () -> T): T {
    val pool = objcMsgSend(objcGetClass("NSAutoreleasePool"), selRegisterName("new"))

    try {
        return block()
    } finally {
        objcMsgSendVoid(pool, selRegisterName("drain"))
    }
}

// Notifications

/** `NSNotificationSuspensionBehaviorCoalesce`: while the app is suspended, deliver only the last notification. */
internal const val NS_NOTIFICATION_SUSPENSION_BEHAVIOR_COALESCE: Long = 2L

internal fun getDefaultCenter(): ObjcObject =
    objcMsgSend(objcGetClass("NSNotificationCenter"), selRegisterName("defaultCenter"))

internal fun getDistributedCenter(): ObjcObject =
    objcMsgSend(objcGetClass("NSDistributedNotificationCenter"), selRegisterName("defaultCenter"))

/**
 * Creates and registers a new `NSObject` subclass named [className], whose [sel] method (taking one `NSNotification`
 * argument) calls [callback]. If a step fails, the class is disposed.
 *
 * @throws IllegalStateException if [className] is already in use, or the method cannot be added.
 */
internal fun createObserverClass(className: String, sel: Selector, callback: MethodHandle): ObjcClass {
    val observerClass = objcAllocateClassPair(objcGetClass("NSObject"), className)
    var registered = false
    try {
        val observerMethodAdded = classAddMethod(observerClass, sel, createUpcallStub(callback), "v@:@")
        check(observerMethodAdded) { "Couldn't create the observer method." }

        objcRegisterClassPair(observerClass)
        registered = true
        return observerClass
    } finally {
        if (!registered) objcDisposeClassPair(observerClass)
    }
}

/** Returns a new instance of [cls] with a retain count of one. The caller must [release] it. */
internal fun newInstance(cls: ObjcClass): ObjcObject = objcMsgSend(cls, selRegisterName("new"))

internal fun release(obj: ObjcObject) {
    objcMsgSendVoid(obj, selRegisterName("release"))
}

internal fun addObserver(
    notificationCenter: ObjcObject,
    observer: ObjcObject,
    sel: Selector,
    notificationName: NSString,
) {
    objcMsgSendVoid(
        notificationCenter,
        selRegisterName("addObserver:selector:name:object:"),
        observer,
        sel,
        notificationName,
        null,
    )
}

internal fun addDistributedObserver(
    distributedCenter: ObjcObject,
    observer: ObjcObject,
    sel: Selector,
    notificationName: NSString,
    suspensionBehavior: Long,
) {
    objcMsgSendVoid(
        distributedCenter,
        selRegisterName("addObserver:selector:name:object:suspensionBehavior:"),
        observer,
        sel,
        notificationName,
        null,
        suspensionBehavior,
    )
}

/** Removes every registration of [observer] from [notificationCenter]. Works for both kinds of center. */
internal fun removeObserver(notificationCenter: ObjcObject, observer: ObjcObject) {
    objcMsgSendVoid(notificationCenter, selRegisterName("removeObserver:"), observer)
}

// Scrollbar preferences

internal fun isOverlayScrollerStyle(): Boolean =
    objcMsgSendLong(objcGetClass("NSScroller"), selRegisterName("preferredScrollerStyle")) == 1L

internal fun isScrollbarTrackClickBehaviorJumpToSpot(): Boolean {
    val userDefaults = objcMsgSend(objcGetClass("NSUserDefaults"), selRegisterName("standardUserDefaults"))
    objcMsgSendBool(userDefaults, selRegisterName("synchronize"))

    return objcMsgSendBoolWithPointer(
        userDefaults,
        selRegisterName("boolForKey:"),
        nsStringConstant("AppleScrollerPagingBehavior"),
    )
}

// Windows and cursor

/** Sends [selectorName] to the delegate of [windowHandle], if the delegate implements it. Does nothing for nil. */
internal fun sendToWindowDelegateIfSupported(windowHandle: ObjcObject, selectorName: String) {
    val sel = selRegisterName(selectorName)
    val delegate = objcMsgSend(windowHandle, selRegisterName("delegate"))

    if (objcMsgSendBoolWithPointer(delegate, selRegisterName("respondsToSelector:"), sel)) {
        objcMsgSendVoid(delegate, sel)
    }
}

internal fun setHiddenUntilMouseMoves() {
    objcMsgSendVoid(objcGetClass("NSCursor"), selRegisterName("setHiddenUntilMouseMoves:"), true)
}
