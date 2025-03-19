// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.channels

import com.intellij.platform.eel.EelResult
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CheckReturnValue
import java.nio.ByteBuffer

// Channel might send the whole buffer by itself without external loop
@Internal
interface EelSendChannelCustomSendWholeBuffer<ERR : Any> : EelSendChannel<ERR> {
  @CheckReturnValue
  suspend fun sendWholeBufferCustom(src: ByteBuffer): EelResult<Unit, ERR>
}