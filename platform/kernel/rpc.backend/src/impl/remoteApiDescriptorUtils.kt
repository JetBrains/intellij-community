// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import fleet.rpc.RemoteApiDescriptor

// Must stay in sync with:
// com.jetbrains.fleet.rpc.plugin.FqnKt.getRemoteApiDescriptorImplClassName
private const val GENERATED_REMOTE_API_DESCRIPTOR_NAME: String = "_generated_RemoteApiDescriptor"

/**
 * Returns the [RemoteApiDescriptor] for an [Rpc][fleet.rpc.Rpc] interface.
 */
internal fun remoteApiDescriptorOf(apiInterface: Class<*>): RemoteApiDescriptor<*> {
  val descriptorClassName = apiInterface.name + "$" + GENERATED_REMOTE_API_DESCRIPTOR_NAME

  val descriptorClass = try {
    apiInterface.classLoader.loadClass(descriptorClassName)
  }
  catch (e: ClassNotFoundException) {
    throw IllegalStateException(
      "Couldn't get remoteApiDescriptor for ${apiInterface.name}: generated descriptor $descriptorClassName not found. " +
      "Is ${apiInterface.name} annotated with @Rpc and compiled with the rpc compiler plugin?", e
    )
  }

  return descriptorClass.getField("INSTANCE").get(null) as RemoteApiDescriptor<*>
}
