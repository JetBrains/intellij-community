// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.openapi.file.converter

import com.intellij.openapi.file.toCanonicalPath
import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import java.nio.file.Path

object NioPathPrefixTreeFactory : AbstractPrefixTreeFactory<Path, String>() {

  override fun convertToList(element: Path): List<String> {
    return CanonicalPathPrefixTreeFactory.convertToList(element.toCanonicalPath())
  }
}