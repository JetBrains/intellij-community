// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend

import com.intellij.openapi.extensions.ExtensionPointName
import fleet.rpc.RemoteApi
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.reflect.KClass

interface RemoteApiProvider {

  data class RemoteApiDescriptor<T : RemoteApi<Unit>>(val klass: KClass<T>, val service: () -> T)

  fun getApis(): List<RemoteApiDescriptor<*>>

  companion object {

    @Internal
    val EP_NAME: ExtensionPointName<RemoteApiProvider> = ExtensionPointName.create("com.intellij.platform.rpc.backend.remoteApiProvider")
  }
}
