// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AnalysisIgnoreEntityModifications")

package com.intellij.ide.analysisignore

import com.intellij.ide.analysisignore.impl.AnalysisIgnoreEntityImpl
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@GeneratedCodeApiVersion(3)
interface AnalysisIgnoreEntityBuilder : WorkspaceEntityBuilder<AnalysisIgnoreEntity> {
  override var entitySource: EntitySource
  var baseDir: VirtualFileUrl
  var patterns: MutableList<String>
}

internal object AnalysisIgnoreEntityType : EntityType<AnalysisIgnoreEntity, AnalysisIgnoreEntityBuilder>() {
  override val entityImplClass: Class<*> get() = AnalysisIgnoreEntityImpl::class.java
  override val entityImplBuilderClass: Class<*> get() = AnalysisIgnoreEntityImpl.Builder::class.java
  operator fun invoke(
    baseDir: VirtualFileUrl,
    patterns: List<String>,
    entitySource: EntitySource,
    init: (AnalysisIgnoreEntityBuilder.() -> Unit)? = null,
  ): AnalysisIgnoreEntityBuilder {
    val builder = builder()
    builder.baseDir = baseDir
    builder.patterns = patterns.toMutableWorkspaceList()
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

@Internal
fun MutableEntityStorage.modifyAnalysisIgnoreEntity(
  entity: AnalysisIgnoreEntity,
  modification: AnalysisIgnoreEntityBuilder.() -> Unit,
): AnalysisIgnoreEntity = modifyEntity(AnalysisIgnoreEntityBuilder::class.java, entity, modification)

@Internal
@JvmOverloads
@JvmName("createAnalysisIgnoreEntity")
fun AnalysisIgnoreEntity(
  baseDir: VirtualFileUrl,
  patterns: List<String>,
  entitySource: EntitySource,
  init: (AnalysisIgnoreEntityBuilder.() -> Unit)? = null,
): AnalysisIgnoreEntityBuilder = AnalysisIgnoreEntityType(baseDir, patterns, entitySource, init)
