// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.openapi.file.converter

import com.intellij.openapi.file.VirtualFileUtil.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory

object VirtualFilePrefixTreeFactory : AbstractPrefixTreeFactory<VirtualFile, String>() {

  override fun convertToList(element: VirtualFile): List<String> {
    return CanonicalPathPrefixTreeFactory.convertToList(element.toCanonicalPath())
  }
}