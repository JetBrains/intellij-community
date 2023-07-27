// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.SdkMainEntity

class SdkRootProviderBridge(private val sdkEntity: SdkMainEntity): RootProvider {
  override fun getUrls(rootType: OrderRootType): Array<String> {
    return sdkEntity.roots.filter { it.type.name == rootType.customName }
      .map { it.url.url }
      .toTypedArray()
  }

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> {
    return sdkEntity.roots.filter { it.type.name == rootType.customName }
      .mapNotNull { it.url.virtualFile }
      .toTypedArray()
  }

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()
  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener,
                                         parentDisposable: Disposable) = throw UnsupportedOperationException()
  override fun removeRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()
}