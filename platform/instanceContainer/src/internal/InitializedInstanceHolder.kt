// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

internal class InitializedInstanceHolder(val instance: Any) : InstanceHolder {

  override fun toString(): String {
    return "Initialized '${instance.javaClass.name}'"
  }

  override fun instanceClassName(): String {
    return instance.javaClass.name
  }

  override fun instanceClass(): Class<*> {
    return instance.javaClass
  }

  override fun tryGetInstance(): Any {
    return instance
  }

  override suspend fun getInstanceIfRequested(): Any {
    return tryGetInstance()
  }

  override suspend fun getInstance(keyClass: Class<*>?): Any {
    return tryGetInstance()
  }

  override suspend fun getInstanceInCallerContext(keyClass: Class<*>?): Any {
    return tryGetInstance()
  }
}
