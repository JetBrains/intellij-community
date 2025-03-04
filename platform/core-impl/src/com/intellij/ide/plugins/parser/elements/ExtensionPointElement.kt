// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.parser.elements

import com.intellij.openapi.extensions.ExtensionPointDescriptor

class ExtensionPointElement(
  @JvmField val name: String?,
  @JvmField val qualifiedName: String?,
  @JvmField val `interface`: String?,
  @JvmField val beanClass: String?,
  @JvmField val hasAttributes: Boolean,
  @JvmField val isDynamic: Boolean,
) {
  init {
    require(name != null || qualifiedName != null) { "neither `name` nor `qualifiedName` specified" }
    require((`interface` != null) != (beanClass != null)) { "only one of `interface` or `beanClass` must be specified" }
  }

  companion object {
    // todo move out
    fun ExtensionPointElement.convert(): ExtensionPointDescriptor = ExtensionPointDescriptor(
      name = qualifiedName ?: name!!,
      isNameQualified = qualifiedName != null,
      className = `interface` ?: beanClass!!,
      isBean = `interface` == null,
      hasAttributes = hasAttributes,
      isDynamic = isDynamic,
    )
  }
}