// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.packaging.elements.PackagingElement
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ExternalEntityMapping
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.MutableExternalEntityMapping

const val PACKAGING_ELEMENTS = "intellij.artifacts.packaging.elements"
internal val MutableEntityStorage.mutableElements: MutableExternalEntityMapping<PackagingElement<*>>
  get() = this.getMutableExternalMapping(PACKAGING_ELEMENTS)
val EntityStorage.elements: ExternalEntityMapping<PackagingElement<*>>
  get() = this.getExternalMapping(PACKAGING_ELEMENTS)