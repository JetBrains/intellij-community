// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelBlockingBridge

/** [EelBlockingBridge] over [fsBlocking]. Registered in `META-INF/services`. */
internal class FsBlockingEelBlockingBridge : EelBlockingBridge {
  override fun <T> run(descriptor: EelDescriptor, body: suspend () -> T): T = descriptor.fsBlocking(body)
}
