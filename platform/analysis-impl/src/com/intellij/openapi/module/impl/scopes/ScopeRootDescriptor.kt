// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.impl.LibraryRootDescriptor
import com.intellij.openapi.roots.impl.ModuleRootDescriptor
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.roots.impl.SdkRootDescriptor
import com.intellij.openapi.vfs.VirtualFile

internal class ScopeRootDescriptor(
  val root: VirtualFile,
  val orderEntry: OrderEntry,
  val orderIndex: Int, // used to check which dependency has higher priority
) {
  fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    val orderEntry = orderEntry
    when (rootDescriptor) {
      is LibraryRootDescriptor -> {
        if (orderEntry !is LibraryOrderEntry) return false
        val library = orderEntry.library ?: return false
        return library == rootDescriptor.library
      }
      is ModuleRootDescriptor -> {
        if (orderEntry !is ModuleSourceOrderEntry) return false
        val module = orderEntry.rootModel.module
        return module == rootDescriptor.module
      }
      is SdkRootDescriptor -> {
        if (orderEntry !is JdkOrderEntry) return false
        val orderEntrySdk = orderEntry.jdk ?: return false
        val rootDescriptorSdk = rootDescriptor.sdk
        return orderEntrySdk == rootDescriptorSdk ||
               isEqualBackup(orderEntrySdk, rootDescriptorSdk)
      }
    }
  }

  // todo ijpl-339 get rid of this method
  //      mock sdk changes on each test run, but we don't get events about that.
  private fun isEqualBackup(
    sdk1: Sdk,
    sdk2: Sdk,
  ): Boolean {
    if (!(ApplicationManager.getApplication().isUnitTestMode)) return false

    val result =
      sdk1.homePath == sdk2.homePath &&
      sdk1.name == sdk2.name &&
      //sdk1.versionString == sdk2.versionString && todo ijpl-339 the version can be different
      sdk1.sdkType == sdk2.sdkType &&
      sdk1.unwrap().javaClass == sdk2.unwrap().javaClass

    return result
  }

  private fun Sdk.unwrap(): Sdk = if (this is ProjectJdkImpl) this.delegate else this
}
