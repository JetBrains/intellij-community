// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.scopeChooser

import com.intellij.openapi.ui.popup.ListSeparator

internal class ScopesSnapshotImpl(
  override val scopeDescriptors: List<ScopeDescriptor>,
  private val separators: Map<String, ListSeparator>,
) : ScopesSnapshot {

  override fun getSeparatorFor(scopeDescriptor: ScopeDescriptor): ListSeparator? =
    scopeDescriptor.displayName?.let { name -> separators[name] }

}