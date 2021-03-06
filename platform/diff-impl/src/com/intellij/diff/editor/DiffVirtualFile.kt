// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.testFramework.LightVirtualFile

abstract class DiffVirtualFile(name: String) :
  LightVirtualFile(name, DiffFileType.INSTANCE, ""), DiffContentVirtualFile, VirtualFileWithoutContent {

  abstract fun createProcessor(project: Project): DiffRequestProcessor

  override fun isWritable(): Boolean = false

  override fun toString(): String = "${javaClass.name}@${Integer.toHexString(hashCode())}"
}
