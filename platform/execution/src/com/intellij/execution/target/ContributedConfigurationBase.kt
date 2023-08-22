// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.Nls

/**
 * Base class for all instance configurations contributed for given [extensionPoint] by some specific [extension][ContributedTypeBase].
 * This way the heterogeneous configurations for given [extensionPoint] may be managed in the common UI while individually requiring
 * the different persistence.
 *
 * E.g, all docker accounts requires different set of configuration options and persistense comparing to EC2 accounts, but still may
 * be managed by user together in the same "remote accounts" UI.
 */
abstract class ContributedConfigurationBase(val typeId: String,
                                            internal val extensionPoint: ExtensionPointName<out ContributedTypeBase<*>>) {

  @Nls
  var displayName: String = ""

  companion object {
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    internal fun <C : ContributedConfigurationBase, T : ContributedTypeBase<C>> C.getTypeImpl(): T =
      this.extensionPoint.extensionList.find { it.id == typeId } as T?
      ?: throw IllegalStateException("for type: $typeId, name: $displayName")
  }
}