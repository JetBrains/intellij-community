// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.filePointer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import kotlin.reflect.KClass

/**
 * Container for all legacy file pointers to track the files and update the state of workspace model regarding to it
 * All legacy file pointers are collected in the [VirtualFileIndex] to perform project model update in a single change
 */
class LegacyModelRootsFilePointers(val project: Project) {
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  internal var isInsideFilePointersUpdate = false
    private set

  private val pointers = listOf(
    // Library roots
    LibraryRootFileWatcher(),
    // Library excluded roots
    EntityWithVfuFileWatcher(
      LibraryEntity::class, ModifiableLibraryEntity::class,
      propertyName = LibraryEntity::excludedRoots.name,
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        excludedRoots = excludedRoots - oldVirtualFileUrl
        excludedRoots = excludedRoots + newVirtualFileUrl
      }
    ),
    // Content root urls
    EntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class,
      propertyName = ContentRootEntity::url.name,
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Content root excluded urls
    EntityWithVfuFileWatcher(
      ContentRootEntity::class, ModifiableContentRootEntity::class,
      propertyName = ContentRootEntity::excludedUrls.name,
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        excludedUrls = excludedUrls - oldVirtualFileUrl
        excludedUrls = excludedUrls + newVirtualFileUrl
      }
    ),
    // Source roots
    EntityWithVfuFileWatcher(
      SourceRootEntity::class, ModifiableSourceRootEntity::class,
      propertyName = SourceRootEntity::url.name,
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output
    EntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class,
      propertyName = JavaModuleSettingsEntity::compilerOutput.name,
      modificator = { _, newVirtualFileUrl -> compilerOutput = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output for tests
    EntityWithVfuFileWatcher(
      JavaModuleSettingsEntity::class, ModifiableJavaModuleSettingsEntity::class,
      propertyName = JavaModuleSettingsEntity::compilerOutputForTests.name,
      modificator = { _, newVirtualFileUrl -> compilerOutputForTests = newVirtualFileUrl }
    ),
    EntitySourceFileWatcher(JpsFileEntitySource.ExactFile::class, { it.file.url }, { source, file -> source.copy(file = file) }),
    EntitySourceFileWatcher(JpsFileEntitySource.FileInDirectory::class, { it.directory.url },
                            { source, file -> source.copy(directory = file) })
  )

  fun onVfsChange(oldUrl: String, newUrl: String) {
    try {
      isInsideFilePointersUpdate = true
      WorkspaceModel.getInstance(project).updateProjectModel { diff ->
        val oldFileUrl = virtualFileManager.fromUrl(oldUrl)
        val entityWithVirtualFileUrl = mutableListOf<EntityWithVirtualFileUrl>()
        addEntitiesWithVFU(diff, oldFileUrl, entityWithVirtualFileUrl)
        oldFileUrl.subTreeFileUrls.map { fileUrl -> addEntitiesWithVFU(diff, fileUrl, entityWithVirtualFileUrl) }
        pointers.forEach { it.onVfsChange(oldUrl, newUrl, entityWithVirtualFileUrl, virtualFileManager, diff) }
      }
    }
    finally {
      isInsideFilePointersUpdate = false
    }
  }

  private fun addEntitiesWithVFU(storage: WorkspaceEntityStorageBuilder, virtualFileUrl: VirtualFileUrl,
                                 aggregator: MutableList<EntityWithVirtualFileUrl>) {
    storage.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).forEach {
      aggregator.add(EntityWithVirtualFileUrl(it.first, virtualFileUrl, it.second))
    }
  }
}

private data class EntityWithVirtualFileUrl(val entity: WorkspaceEntity, val virtualFileUrl: VirtualFileUrl, val propertyName: String)

private interface LegacyFileWatcher {
  fun onVfsChange(oldUrl: String, newUrl: String, entitiesWithVFU: List<EntityWithVirtualFileUrl>, virtualFileManager: VirtualFileUrlManager,
                  diff: WorkspaceEntityStorageBuilder)
}

private class EntitySourceFileWatcher<T : EntitySource>(
  val entitySource: KClass<T>,
  val containerToUrl: (T) -> String,
  val createNewSource: (T, VirtualFileUrl) -> T
) : LegacyFileWatcher {
  override fun onVfsChange(oldUrl: String, newUrl: String, entitiesWithVFU: List<EntityWithVirtualFileUrl>, virtualFileManager: VirtualFileUrlManager,
                           diff: WorkspaceEntityStorageBuilder) {
    val entities = diff.entitiesBySource { it::class == entitySource }
    for ((entitySource, mapOfEntities) in entities) {
      @Suppress("UNCHECKED_CAST")
      val urlFromContainer = containerToUrl(entitySource as T)
      if (!FileUtil.startsWith(urlFromContainer, oldUrl)) continue

      val newVfurl = virtualFileManager.fromUrl(newUrl + urlFromContainer.substring(oldUrl.length))
      val newEntitySource = createNewSource(entitySource, newVfurl)

      mapOfEntities.values.flatten().forEach { diff.changeSource(it, newEntitySource) }
    }
  }
}

/**
 * Legacy file pointer that can track and update urls stored in a [WorkspaceEntity].
 * [entityClass] - class of a [WorkspaceEntity] that contains an url being tracked
 * [modifiableEntityClass] - class of modifiable entity of [entityClass]
 * [propertyName] - name of the field which contains [VirtualFileUrl]
 * [modificator] - function for modifying an entity
 * There 2 functions are created for better convenience. You should use only one from them.
 */
private class EntityWithVfuFileWatcher<E : WorkspaceEntity, M : ModifiableWorkspaceEntity<E>>(
  val entityClass: KClass<E>,
  val modifiableEntityClass: KClass<M>,
  val propertyName: String,
  val modificator: M.(VirtualFileUrl, VirtualFileUrl) -> Unit
) : LegacyFileWatcher {
  override fun onVfsChange(oldUrl: String, newUrl: String, entitiesWithVFU: List<EntityWithVirtualFileUrl>, virtualFileManager: VirtualFileUrlManager,
                           diff: WorkspaceEntityStorageBuilder) {
    entitiesWithVFU.filter { entityClass.isInstance(it.entity) && it.propertyName == propertyName }.forEach { entityWithVFU ->
      val existingVirtualFileUrl = entityWithVFU.virtualFileUrl
      val savedUrl = existingVirtualFileUrl.url
      val newTrackedUrl = newUrl + savedUrl.substring(oldUrl.length)

      val newContainer = virtualFileManager.fromUrl(newTrackedUrl)
      @Suppress("UNCHECKED_CAST")
      entityWithVFU.entity as E
      diff.modifyEntity(modifiableEntityClass.java, entityWithVFU.entity) {
        this.modificator(existingVirtualFileUrl, newContainer)
      }
    }
  }
}

/**
 * It's responsible for updating complex case than [VirtualFileUrl] contains not in the entity itself but in internal data class.
 * This is about LibraryEntity -> roots (LibraryRoot) -> url (VirtualFileUrl).
 */
private class LibraryRootFileWatcher: LegacyFileWatcher {
  private val propertyName = LibraryEntity::roots.name

  override fun onVfsChange(oldUrl: String, newUrl: String, entitiesWithVFU: List<EntityWithVirtualFileUrl>, virtualFileManager: VirtualFileUrlManager,
                           diff: WorkspaceEntityStorageBuilder) {
    entitiesWithVFU.filter { LibraryEntity::class.isInstance(it.entity) && it.propertyName == propertyName }.forEach { entityWithVFU ->
      val oldVFU = entityWithVFU.virtualFileUrl
      val newVFU = virtualFileManager.fromUrl(newUrl + oldVFU.url.substring(oldUrl.length))

      entityWithVFU.entity as LibraryEntity
      val oldLibraryRoot = diff.resolve(entityWithVFU.entity.persistentId())?.roots?.find { it.url == oldVFU }
                           ?: error("Incorrect state of the VFU index")
      val newLibraryRoot = LibraryRoot(newVFU, oldLibraryRoot.type, oldLibraryRoot.inclusionOptions)
      diff.modifyEntity(ModifiableLibraryEntity::class.java, entityWithVFU.entity) {
        roots = roots - oldLibraryRoot
        roots = roots + newLibraryRoot
      }
    }
  }
}