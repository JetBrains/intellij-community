// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ScopeRootDescriptors")

package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.impl.DummyRootDescriptor
import com.intellij.openapi.roots.impl.LibraryRootDescriptor
import com.intellij.openapi.roots.impl.ModuleRootDescriptor
import com.intellij.openapi.roots.impl.RootDescriptor
import com.intellij.openapi.roots.impl.SdkRootDescriptor

internal fun ScopeRootDescriptor(
  orderEntry: OrderEntry,
): ScopeRootDescriptor = when (orderEntry) {
  is LibraryOrderEntry -> LibScopeDescriptor(orderEntry)
  is ModuleOrderEntry -> ModuleScopeDescriptor(orderEntry)
  is ModuleSourceOrderEntry -> ModuleSourceScopeDescriptor(orderEntry)
  is JdkOrderEntry -> SdkScopeDescriptor(orderEntry)
  else -> {
    log.error("Unexpected order entry: $orderEntry")
    InvalidScopeDescriptor(orderEntry)
  }
}

internal sealed interface ScopeRootDescriptor {
  val orderEntry: OrderEntry

  fun correspondTo(rootDescriptor: RootDescriptor): Boolean
}

private class LibScopeDescriptor(
  orderEntry: LibraryOrderEntry,
) : ScopeRootDescriptorBase<LibraryOrderEntry>(orderEntry) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is LibraryRootDescriptor) {
      val library = orderEntry.library ?: return false
      return library == rootDescriptor.library
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class ModuleScopeDescriptor(
  orderEntry: ModuleOrderEntry,
) : ScopeRootDescriptorBase<ModuleOrderEntry>(orderEntry) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is ModuleRootDescriptor) {
      val module = orderEntry.module
      return module == rootDescriptor.module
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class ModuleSourceScopeDescriptor(
  orderEntry: ModuleSourceOrderEntry,
) : ScopeRootDescriptorBase<ModuleSourceOrderEntry>(orderEntry) {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is ModuleRootDescriptor) {
      val module = orderEntry.rootModel.module
      return module == rootDescriptor.module
    }
    return super.correspondTo(rootDescriptor)
  }
}

private class SdkScopeDescriptor(
  orderEntry: JdkOrderEntry,
) : ScopeRootDescriptorBase<JdkOrderEntry>(orderEntry) {
  @Volatile
  private var cachedSdk: Any? = null

  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor is SdkRootDescriptor) {
      val orderEntrySdk = inferSdk() ?: return false
      val rootDescriptorSdk = rootDescriptor.sdk
      return orderEntrySdk == rootDescriptorSdk ||
             isEqualBackup(orderEntrySdk, rootDescriptorSdk)
    }
    return super.correspondTo(rootDescriptor)
  }

  private fun inferSdk(): Sdk? {
    if (cachedSdk == null) {
      cachedSdk = orderEntry.jdk ?: NullSdkMarker
    }
    return cachedSdk as? Sdk
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
  orderEntry: OrderEntry,
) : ScopeRootDescriptorBase<OrderEntry>(orderEntry)

private abstract class ScopeRootDescriptorBase<Entry : OrderEntry>(
  override val orderEntry: Entry,
) : ScopeRootDescriptor {
  override fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    if (rootDescriptor !is DummyRootDescriptor) return false

    // root equality is guaranteed by the map lookup in MultiverseRootContainer.getRootDescriptor
    log.debug { "DummyRootDescriptor corresponds to scopeRootDescriptor $rootDescriptor, $this" }

    return true
  }
}

private val log = logger<SdkRootDescriptor>()

private object NullSdkMarker