// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.repo

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.FileHolder
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder

abstract class VcsManagedFilesHolderBase : VcsManagedFilesHolder {
  override fun cleanAll() = Unit
  override fun cleanUnderScope(scope: VcsDirtyScope) = Unit

  override fun addFile(file: FilePath) {
    LOG.warn("Attempt to populate vcs-managed files holder $this with $file", Throwable())
  }

  override fun copy(): FileHolder = this // holder is immutable

  companion object {
    private val LOG = logger<VcsManagedFilesHolderBase>()
  }
}