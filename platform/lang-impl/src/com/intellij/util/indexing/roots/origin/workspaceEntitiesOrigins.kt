// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots.origin

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.ModuleContentOrigin
import com.intellij.workspaceModel.storage.EntityReference

interface ModuleAwareContentEntityOrigin : ModuleContentOrigin {
  override val module: Module
  val reference: EntityReference<*>
  val roots: Collection<VirtualFile>
}

interface GenericContentEntityOrigin : ContentOrigin {
  val reference: EntityReference<*>
  val roots: Collection<VirtualFile>
}

interface ExternalEntityOrigin : IndexableSetOrigin {
  val reference: EntityReference<*>
  val roots: Collection<VirtualFile>
  val sourceRoots: Collection<VirtualFile>
}