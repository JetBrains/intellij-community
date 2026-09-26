// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.design

interface SvgPatcherDesigner {
    fun replace(name: String, newValue: String)

    fun replaceIfMatches(name: String, expectedValue: String, newValue: String)

    fun replaceUnlessMatches(name: String, expectedValue: String, newValue: String)

    fun removeIfMatches(name: String, expectedValue: String)

    fun removeUnlessMatches(name: String, expectedValue: String)

    fun remove(name: String)

    fun set(name: String, value: String)

    fun add(name: String, value: String)

    fun filter(path: String, svgPatcherDesigner: SvgPatcherDesigner.() -> Unit)
}
