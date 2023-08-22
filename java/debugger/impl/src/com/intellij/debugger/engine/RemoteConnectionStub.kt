// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine

import com.intellij.execution.configurations.RemoteConnection
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RemoteConnectionStub(useSockets: Boolean, hostName: String, address: String, serverMode: Boolean
) : RemoteConnection(useSockets, hostName, address, serverMode)
