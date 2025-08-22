// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Internal
@file:JvmName("ModuleScopeUtil")

package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleSourceOrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.search.impl.VirtualFileEnumeration
import com.intellij.util.ArrayUtil
import com.intellij.util.BitUtil.isSet
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import java.util.*

internal fun getOrderEnumeratorForOptions(module: Module, options: Int): OrderEnumerator {
  val en = ModuleRootManager.getInstance(module).orderEntries()
  en.recursively()
  if (options.hasOption(COMPILE_ONLY)) en.exportedOnly().compileOnly()
  if (!options.hasOption(LIBRARIES)) en.withoutLibraries().withoutSdk()
  if (!options.hasOption(MODULES)) en.withoutDepModules()
  if (!options.hasOption(TESTS)) en.productionOnly()
  return en
}

internal fun Int.hasOption(@ScopeConstant option: Int): Boolean {
  return isSet(this, option)
}

internal fun calcModules(module: Module, myOptions: Int): Set<Module> {
  val modules = HashSet<Module>()
  val en = getOrderEnumeratorForOptions(module, myOptions)
  en.forEach { each ->
    if (each is ModuleOrderEntry) {
      ContainerUtil.addIfNotNull(modules, each.getModule())
    }
    else if (each is ModuleSourceOrderEntry) {
      ContainerUtil.addIfNotNull(modules, each.getOwnerModule())
    }
    true
  }
  return modules
}

/**
 * Compute a set of ids of all files under `roots`
 */
fun getFileEnumerationUnderRoots(roots: Collection<VirtualFile>): VirtualFileEnumeration {
  val result: IntSet = IntOpenHashSet()
  for (file in roots) {
    if (file is VirtualFileWithId) {
      val children = VirtualFileManager.getInstance().listAllChildIds(file.getId())
      result.addAll(IntList.of(*children))
    }
  }

  return MyVirtualFileEnumeration(result)
}

private class MyVirtualFileEnumeration(private val myIds: IntSet) : VirtualFileEnumeration {
  override fun contains(fileId: Int): Boolean {
    return myIds.contains(fileId)
  }

  override fun asArray(): IntArray {
    return myIds.toArray(ArrayUtil.EMPTY_INT_ARRAY)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that: MyVirtualFileEnumeration = other as MyVirtualFileEnumeration
    return myIds == that.myIds
  }

  override fun hashCode(): Int {
    return Objects.hash(myIds)
  }

  override fun toString(): String {
    return myIds.toIntArray().contentToString()
  }
}

@MagicConstant(flags = [COMPILE_ONLY.toLong(), LIBRARIES.toLong(), MODULES.toLong(), TESTS.toLong()])
internal annotation class ScopeConstant

const val COMPILE_ONLY: Int = 0x01
const val LIBRARIES: Int = 0x02
const val MODULES: Int = 0x04
const val TESTS: Int = 0x08
