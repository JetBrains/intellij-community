// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ScopeRootDescriptors")

package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.*
import com.intellij.openapi.vfs.VirtualFile

internal fun ScopeRootDescriptor(
  root: VirtualFile,
  orderEntry: OrderEntry,
  orderIndex: Int, // used to check which dependency has higher priority
): ScopeRootDescriptor = when (orderEntry) {
  is LibraryOrderEntry -> LibScopeDescriptor(root, orderEntry, orderIndex)
  is ModuleOrderEntry -> ModuleScopeDescriptor(root, orderEntry, orderIndex)
  is ModuleSourceOrderEntry -> ModuleSourceScopeDescriptor(root, orderEntry, orderIndex)
  is JdkOrderEntry -> SdkScopeDescriptor(root, orderEntry, orderIndex)
  else -> {
    log.error("Unexpected order entry: $orderEntry")
    InvalidScopeDescriptor(root, orderEntry, orderIndex)
  }
}

internal sealed interface ScopeRootDescriptor {
  val root: VirtualFile
  val orderEntry: OrderEntry

  /** used to check which dependency has higher priority */
  val orderIndex: Int

  fun correspondTo(rootDescriptor: RootDescriptor): Boolean
}

private class LibScopeDescriptor(
  root: VirtualFile,
  orderEntry: LibraryOrderEntry,
  orderIndex: Int,
) : ScopeRootDescriptorBase<LibraryOrderEntry>(root, orderEntry, orderIndex) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is LibraryRootDescriptor) {
      val library = orderEntry.library ?: return false
      return library == rootDescriptor.library
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class ModuleScopeDescriptor(
  root: VirtualFile,
  orderEntry: ModuleOrderEntry,
  orderIndex: Int,
) : ScopeRootDescriptorBase<ModuleOrderEntry>(root, orderEntry, orderIndex) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is ModuleRootDescriptor) {
      val module = orderEntry.module
      return module == rootDescriptor.module
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class ModuleSourceScopeDescriptor(
  root: VirtualFile,
  orderEntry: ModuleSourceOrderEntry,
  orderIndex: Int,
) : ScopeRootDescriptorBase<ModuleSourceOrderEntry>(root, orderEntry, orderIndex) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is ModuleRootDescriptor) {
      val module = orderEntry.rootModel.module
      return module == rootDescriptor.module
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class SdkScopeDescriptor(
  root: VirtualFile,
  orderEntry: JdkOrderEntry,
  orderIndex: Int,
) : ScopeRootDescriptorBase<JdkOrderEntry>(root, orderEntry, orderIndex) {
  private val sdk: Sdk? by lazy { orderEntry.jdk }

  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is SdkRootDescriptor) {
      val orderEntrySdk = sdk ?: return false
      val rootDescriptorSdk = rootDescriptor.sdk
      return orderEntrySdk == rootDescriptorSdk ||
             isEqualBackup(orderEntrySdk, rootDescriptorSdk)
    }
    return super.correspondTo(rootDescriptor)
  }

  // todo IJPL-339 get rid of this method
  //      mock sdk changes on each test run, but we don't get events about that.
  private fun isEqualBackup(
    sdk1: Sdk,
    sdk2: Sdk,
  ): Boolean {
    if (!(ApplicationManager.getApplication().isUnitTestMode)) return false

    val result =
      sdk1.homePath == sdk2.homePath &&
      sdk1.name == sdk2.name &&
      //sdk1.versionString == sdk2.versionString && todo IJPL-339 the version can be different
      sdk1.sdkType == sdk2.sdkType &&
      sdk1.unwrap().javaClass == sdk2.unwrap().javaClass

    return result
  }

  private fun Sdk.unwrap(): Sdk = if (this is ProjectJdkImpl) this.delegate else this
}

private class InvalidScopeDescriptor(
  root: VirtualFile,
  orderEntry: OrderEntry,
  orderIndex: Int,
) : ScopeRootDescriptorBase<OrderEntry>(root, orderEntry, orderIndex)

private abstract class ScopeRootDescriptorBase<Entry : OrderEntry>(
  override val root: VirtualFile,
  override val orderEntry: Entry,
  override val orderIndex: Int, // used to check which dependency has higher priority
) : ScopeRootDescriptor {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor !is DummyRootDescriptor) return false
    // todo not sure if this is correct, please investigate further
    val result = this.root == rootDescriptor.root

    if (result) {
      log.debug { "DummyRootDescriptor corresponds to scopeRootDescriptor $rootDescriptor, $this" }
    }

    return result
  }
}

private val log = logger<SdkRootDescriptor>()