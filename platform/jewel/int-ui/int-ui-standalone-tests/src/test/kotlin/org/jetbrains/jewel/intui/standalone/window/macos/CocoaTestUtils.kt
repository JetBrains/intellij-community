// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone.window.macos

/** Posts [notificationName] to [notificationCenter] synchronously, with no sender object. */
internal fun postNotification(notificationCenter: ObjcObject, notificationName: NSString) {
    objcMsgSendVoid(notificationCenter, selRegisterName("postNotificationName:object:"), notificationName, null)
}
