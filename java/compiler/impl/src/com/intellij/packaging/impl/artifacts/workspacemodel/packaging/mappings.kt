// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel.packaging

import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingExternalMapping
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.ExternalEntityMapping
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.MutableExternalEntityMapping

internal val MutableEntityStorage.mutableElements: MutableExternalEntityMapping<PackagingElement<*>>
  get() = this.getMutableExternalMapping(PackagingExternalMapping.key)
val EntityStorage.elements: ExternalEntityMapping<PackagingElement<*>>
  get() = this.getExternalMapping(PackagingExternalMapping.key)