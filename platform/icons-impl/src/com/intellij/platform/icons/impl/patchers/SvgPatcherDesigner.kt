// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.patchers

import com.intellij.platform.icons.design.SvgPatcherDesigner

class DefaultSvgPatcherDesigner : SvgPatcherDesigner {
    private val operations = mutableListOf<SvgPatchOperation>()
    private val filteredOperations = mutableListOf<SvgPathFilteredOperations>()

    override fun replace(name: String, newValue: String) {
        operations.add(SvgPatchOperation(name, newValue, false, false, null, SvgPatchOperation.Operation.Replace))
    }

    override fun replaceIfMatches(name: String, expectedValue: String, newValue: String) {
        operations.add(
            SvgPatchOperation(name, newValue, true, false, expectedValue, SvgPatchOperation.Operation.Replace)
        )
    }

    override fun replaceUnlessMatches(name: String, expectedValue: String, newValue: String) {
        operations.add(
            SvgPatchOperation(name, newValue, true, true, expectedValue, SvgPatchOperation.Operation.Replace)
        )
    }

    override fun removeIfMatches(name: String, expectedValue: String) {
        operations.add(SvgPatchOperation(name, null, true, false, expectedValue, SvgPatchOperation.Operation.Remove))
    }

    override fun removeUnlessMatches(name: String, expectedValue: String) {
        operations.add(SvgPatchOperation(name, null, true, true, expectedValue, SvgPatchOperation.Operation.Remove))
    }

    override fun remove(name: String) {
        operations.add(SvgPatchOperation(name, null, false, false, null, SvgPatchOperation.Operation.Remove))
    }

    override fun set(name: String, value: String) {
        operations.add(SvgPatchOperation(name, value, false, false, null, SvgPatchOperation.Operation.Add))
    }

    override fun add(name: String, value: String) {
        operations.add(SvgPatchOperation(name, value, false, false, null, SvgPatchOperation.Operation.Add))
    }

    override fun filter(path: String, svgPatcherDesigner: SvgPatcherDesigner.() -> Unit) {
        val designer = DefaultSvgPatcherDesigner()
        svgPatcherDesigner.invoke(designer)
        // TODO Support recursive filters
        filteredOperations.add(SvgPathFilteredOperations(path, designer.build().operations))
    }

    internal fun build(): DefaultSvgPatcher = DefaultSvgPatcher(operations, filteredOperations)
}
