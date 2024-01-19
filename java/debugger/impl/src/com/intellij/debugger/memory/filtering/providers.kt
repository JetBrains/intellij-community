// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.filtering

import com.intellij.debugger.memory.ui.JavaReferenceInfo
import com.intellij.xdebugger.memory.ui.ReferenceInfo
import com.intellij.xdebugger.memory.utils.InstancesProvider
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType

internal interface InstanceProviderEx : InstancesProvider {
  fun returnAllInstancesOfAClass(): Boolean
}

internal class FixedListProvider(private val references: List<ObjectReference>) : InstanceProviderEx {
  override fun getInstances(limit: Int): MutableList<ReferenceInfo> = references.mapToInfoList()
  override fun returnAllInstancesOfAClass() = false
}

internal class ClassInstancesProvider(private val referenceType: ReferenceType) : InstanceProviderEx {
  override fun getInstances(limit: Int): MutableList<ReferenceInfo> = referenceType.instances(limit.toLong()).mapToInfoList()
  override fun returnAllInstancesOfAClass() = true
}

private fun List<ObjectReference>?.mapToInfoList(): MutableList<ReferenceInfo> =
  this?.mapTo(ArrayList()) { JavaReferenceInfo(it) } ?: mutableListOf()
