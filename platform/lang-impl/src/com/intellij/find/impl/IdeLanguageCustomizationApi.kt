// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.runtime.product.ProductMode
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface IdeLanguageCustomizationApi : RemoteApi<Unit> {

  suspend fun getPrimaryIdeLanguagesExtensions(): Set<String>

  companion object {
    private val localInstance by lazy { IdeLanguageCustomizationApiImpl() }

    @JvmStatic
    suspend fun getInstance(): IdeLanguageCustomizationApi {
      LiteRemoteApiProviderService.tryResolve(remoteApiDescriptor<IdeLanguageCustomizationApi>())?.let { return it }
      // Null is not "no backend": a connected frontend also resolves null until its protocol client
      // is up - and this API is called from application init, inside that window. Only a
      // strictly-Light session uses the local implementation; everything else awaits (IJPL-252054).
      return if (IdeProductMode.getInstance().currentMode == ProductMode.LIGHT) localInstance
             else LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<IdeLanguageCustomizationApi>())
    }
  }
}