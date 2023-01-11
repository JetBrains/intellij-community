// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.file.converter

import com.intellij.util.containers.prefix.map.AbstractPrefixTreeFactory
import org.jetbrains.annotations.SystemIndependent

object CanonicalPathPrefixTreeFactory : AbstractPrefixTreeFactory<@SystemIndependent String, String>() {

  override fun convertToList(element: @SystemIndependent String): List<String> {
    return element.removeSuffix("/").split("/")
  }
}