// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.design

import org.jetbrains.icons.ExperimentalIconsApi
import org.jetbrains.icons.InternalIconsApi
import org.jetbrains.icons.patchers.SvgPatchOperation
import org.jetbrains.icons.patchers.SvgPatcher
import org.jetbrains.icons.patchers.SvgPathFilteredOperations

@ExperimentalIconsApi
public class SvgPatcherDesigner {
  private val operations = mutableListOf<SvgPatchOperation>()
  private val filteredOperations = mutableListOf<SvgPathFilteredOperations>()

  fun replace(name: String, newValue: String) {
    operations.add(SvgPatchOperation(name, newValue, false, false, null, SvgPatchOperation.Operation.Replace))
  }

  fun replaceIfMatches(name: String, expectedValue: String, newValue: String) {
    operations.add(SvgPatchOperation(name, newValue, true, false, expectedValue, SvgPatchOperation.Operation.Replace))
  }

  fun replaceUnlessMatches(name: String, expectedValue: String, newValue: String) {
    operations.add(SvgPatchOperation(name, newValue, true, true, expectedValue, SvgPatchOperation.Operation.Replace))
  }

  fun removeIfMatches(name: String, expectedValue: String) {
    operations.add(SvgPatchOperation(name, null, true, false, expectedValue, SvgPatchOperation.Operation.Remove))
  }

  fun removeUnlessMatches(name: String, expectedValue: String)  {
    operations.add(SvgPatchOperation(name, null, true, true, expectedValue, SvgPatchOperation.Operation.Remove))
  }

  fun remove(name: String) {
    operations.add(SvgPatchOperation(name, null, false, false, null, SvgPatchOperation.Operation.Remove))
  }

  fun set(name: String, value: String) {
    operations.add(SvgPatchOperation(name, value, false, false, null, SvgPatchOperation.Operation.Add))
  }

  fun add(name: String, value: String) {
    operations.add(SvgPatchOperation(name, value, false, false, null, SvgPatchOperation.Operation.Add))
  }

  fun filter(path: String, svgPatcherDesigner: SvgPatcherDesigner.() -> Unit) {
    val designer = SvgPatcherDesigner()
    svgPatcherDesigner.invoke(designer)
    // TODO Support recursive filters
    filteredOperations.add(SvgPathFilteredOperations(path, designer.build().operations))
  }

  @InternalIconsApi
  internal fun build(): SvgPatcher = SvgPatcher(operations, filteredOperations)
}