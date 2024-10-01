// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.auth

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class GenericAuthProviderEP : BaseKeyedLazyInstance<GenericAuthProvider>(), KeyedLazyInstance<GenericAuthProvider> {
  @RequiredElement
  @Attribute("id")
  var id: String? = null

  @RequiredElement
  @Attribute("implementationClass")
  var implementationClass: String? = null

  override fun getImplementationClassName(): String? = implementationClass
  override fun getKey(): String = id!!
}

@ApiStatus.Internal
@ApiStatus.Experimental
interface GenericAuthProvider {
  companion object {
    val EP_NAME : ExtensionPointName<GenericAuthProviderEP> = ExtensionPointName<GenericAuthProviderEP>("com.intellij.genericAuthProvider")
  }
  suspend fun getAuthData(request: String): String
}