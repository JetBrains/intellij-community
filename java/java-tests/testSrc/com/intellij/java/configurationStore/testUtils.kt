// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.configurationStore

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.platform.workspaceModel.jps.CustomModuleEntitySource
import com.intellij.platform.workspaceModel.jps.JpsFileEntitySource
import com.intellij.workspaceModel.ide.impl.jps.serialization.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
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

  override fun loadRoots(moduleEntity: ModuleEntity.Builder,
                         reader: JpsFileContentReader,
                         customDir: String?,
                         imlFileUrl: VirtualFileUrl,
                         internalModuleListSerializer: JpsModuleListSerializer?,
                         errorReporter: ErrorReporter,
                         virtualFileManager: VirtualFileUrlManager,
                         moduleLibrariesCollector: MutableMap<LibraryId, LibraryEntity>) {
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