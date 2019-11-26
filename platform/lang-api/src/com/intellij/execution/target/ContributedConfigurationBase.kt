// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.extensions.ExtensionPointName

abstract class ContributedConfigurationBase(val typeId: String, internal val extensionPoint: ExtensionPointName<out ContributedTypeBase<*>>) {

  var displayName: String = ""

  companion object {
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    internal fun <C : ContributedConfigurationBase, T : ContributedTypeBase<C>> C.getTypeImpl(): T =
      this.extensionPoint.extensionList.find { it.id == typeId } as T?
      ?: throw IllegalStateException("for type: $typeId, name: $displayName")
  }
}