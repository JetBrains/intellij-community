// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.*
import com.intellij.openapi.vfs.VirtualFile

internal class ScopeRootDescriptor(
  val root: VirtualFile,
  val orderEntry: OrderEntry,
  val orderIndex: Int, // used to check which dependency has higher priority
) {
  fun correspondTo(rootDescriptor: RootDescriptor): Boolean {
    when (rootDescriptor) {
      is LibraryRootDescriptor -> {
        if (orderEntry !is LibraryOrderEntry) return false
        val library = orderEntry.library ?: return false
        return library == rootDescriptor.library
      }
      is ModuleRootDescriptor -> {
        val module = when (orderEntry) {
          is ModuleSourceOrderEntry -> {
            orderEntry.rootModel.module
          }
          is ModuleOrderEntry -> {
            orderEntry.module
          }
          else -> return false
        }

        return module == rootDescriptor.module
      }
      is SdkRootDescriptor -> {
        if (orderEntry !is JdkOrderEntry) return false
        val orderEntrySdk = orderEntry.jdk ?: return false
        val rootDescriptorSdk = rootDescriptor.sdk
        return orderEntrySdk == rootDescriptorSdk ||
               isEqualBackup(orderEntrySdk, rootDescriptorSdk)
      }
      is DummyRootDescriptor -> {
        // todo not sure if this is correct, please investigate further
        val result = this.root == rootDescriptor.root

        if (result) {
          log.debug { "DummyRootDescriptor corresponds to scopeRootDescriptor $rootDescriptor, $this" }
        }

        return result
      }
    }
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

private val log = logger<SdkRootDescriptor>()