// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.platform.workspace.jps.CustomModuleEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntityBuilder
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntityBuilder
import com.intellij.platform.workspace.jps.serialization.impl.CustomModuleRootsSerializer
import com.intellij.platform.workspace.jps.serialization.impl.ErrorReporter
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentReader
import com.intellij.platform.workspace.jps.serialization.impl.JpsFileContentWriter
import com.intellij.platform.workspace.jps.serialization.impl.JpsModuleListSerializer
import com.intellij.platform.workspace.storage.DummyParentEntitySource
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.rules.ProjectModelRule
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths

internal val configurationStoreTestDataRoot: Path
  get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("java/java-tests/testData/configurationStore")

internal fun ProjectModelRule.saveProjectState() {
  runBlocking { project.stateStore.save() }
}

internal class SampleCustomModuleRootsSerializer : CustomModuleRootsSerializer {
  override val id: String
    get() = ID

  override fun createEntitySource(imlFileUrl: VirtualFileUrl,
                                  internalEntitySource: JpsFileEntitySource,
                                  customDir: String?,
                                  virtualFileManager: VirtualFileUrlManager): EntitySource {
    return SampleDummyParentCustomModuleEntitySource(internalEntitySource)
  }

  override fun loadRoots(moduleEntity: ModuleEntityBuilder,
                         reader: JpsFileContentReader,
                         customDir: String?,
                         imlFileUrl: VirtualFileUrl,
                         internalModuleListSerializer: JpsModuleListSerializer?,
                         errorReporter: ErrorReporter,
                         virtualFileManager: VirtualFileUrlManager,
                         moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntityBuilder>) {
  }

  override fun saveRoots(module: ModuleEntity,
                         entities: Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>,
                         writer: JpsFileContentWriter,
                         customDir: String?,
                         imlFileUrl: VirtualFileUrl,
                         storage: EntityStorage,
                         virtualFileManager: VirtualFileUrlManager) {
  }

  companion object {
    const val ID: String = "custom"
  }
}

internal class SampleDummyParentCustomModuleEntitySource(override val internalSource: JpsFileEntitySource) : CustomModuleEntitySource,
                                                                                                             DummyParentEntitySource