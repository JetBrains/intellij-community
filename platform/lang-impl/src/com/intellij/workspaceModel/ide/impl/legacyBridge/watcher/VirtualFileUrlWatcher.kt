// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.workspaceModel.ide.JpsFileEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import kotlin.reflect.KClass

open class VirtualFileUrlWatcher(val project: Project) {
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  internal var isInsideFilePointersUpdate = false
    private set

  private val pointers = listOf(
    // Library roots
    LibraryRootFileWatcher(),
    // Library excluded roots
    EntityVirtualFileUrlWatcher(
      LibraryEntity::class, LibraryEntity.Builder::class,
      propertyName = LibraryEntity::excludedRoots.name,
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        val newUrls = excludedRoots.toMutableList()
        newUrls.removeIf { it.url == oldVirtualFileUrl }
        newUrls.add(ExcludeUrlEntity(newVirtualFileUrl, entitySource))
        excludedRoots = newUrls
      }
    ),
    EntityVirtualFileUrlWatcher(
      ExcludeUrlEntity::class, ExcludeUrlEntity.Builder::class,
      propertyName = ExcludeUrlEntity::url.name,
      modificator = { _, newVirtualFileUrl ->
        if (this.library != null || this.contentRoot != null) {
          this.url = newVirtualFileUrl
        }
      }
    ),
    // Content root urls
    EntityVirtualFileUrlWatcher(
      ContentRootEntity::class, ContentRootEntity.Builder::class,
      propertyName = ContentRootEntity::url.name,
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Content root excluded urls
    EntityVirtualFileUrlWatcher(
      ContentRootEntity::class, ContentRootEntity.Builder::class,
      propertyName = ContentRootEntity::excludedUrls.name,
      modificator = { oldVirtualFileUrl, newVirtualFileUrl ->
        val newUrls = excludedUrls.toMutableList()
        newUrls.removeIf { it.url == oldVirtualFileUrl }
        newUrls.add(ExcludeUrlEntity(newVirtualFileUrl, entitySource))
        excludedUrls = newUrls
      }
    ),
    // Source roots
    EntityVirtualFileUrlWatcher(
      SourceRootEntity::class, SourceRootEntity.Builder::class,
      propertyName = SourceRootEntity::url.name,
      modificator = { _, newVirtualFileUrl -> url = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output
    EntityVirtualFileUrlWatcher(
      JavaModuleSettingsEntity::class, JavaModuleSettingsEntity.Builder::class,
      propertyName = JavaModuleSettingsEntity::compilerOutput.name,
      modificator = { _, newVirtualFileUrl -> compilerOutput = newVirtualFileUrl }
    ),
    // Java module settings entity compiler output for tests
    EntityVirtualFileUrlWatcher(
      JavaModuleSettingsEntity::class, JavaModuleSettingsEntity.Builder::class,
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
      val entityWithVirtualFileUrl = mutableListOf<EntityWithVirtualFileUrl>()
      WorkspaceModel.getInstance(project).updateProjectModel("On VFS change") { diff ->
        val oldFileUrl = virtualFileManager.fromUrl(oldUrl)
        calculateAffectedEntities(diff, oldFileUrl, entityWithVirtualFileUrl)
        oldFileUrl.subTreeFileUrls.map { fileUrl -> calculateAffectedEntities(diff, fileUrl, entityWithVirtualFileUrl) }
        val result = entityWithVirtualFileUrl.filter { shouldUpdateThisEntity(it.entity) }.toList()
        pointers.forEach { it.onVfsChange(oldUrl, newUrl, result, virtualFileManager, diff) }
      }
    }
    finally {
      isInsideFilePointersUpdate = false
    }
  }

  // A workaround to have an opportunity to skip some entities from being updated (for now it's only for Rider to avoid update ContentRoots)
  open fun shouldUpdateThisEntity(entity: WorkspaceEntity): Boolean {
    return true
  }

  companion object {
    fun getInstance(project: Project): VirtualFileUrlWatcher = project.service()

    internal fun calculateAffectedEntities(storage: EntityStorage, virtualFileUrl: VirtualFileUrl,
                                           aggregator: MutableList<EntityWithVirtualFileUrl>): Boolean {
      var hasEntities = false
      storage.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).forEach {
        aggregator.add(EntityWithVirtualFileUrl(it.first, virtualFileUrl, it.second))
        hasEntities = true
      }
      return hasEntities
    }

  }
}

data class EntityWithVirtualFileUrl(val entity: WorkspaceEntity, val virtualFileUrl: VirtualFileUrl, val propertyName: String)

private interface LegacyFileWatcher {
  fun onVfsChange(oldUrl: String,
                  newUrl: String,
                  entitiesWithVFU: List<EntityWithVirtualFileUrl>,
                  virtualFileManager: VirtualFileUrlManager,
                  diff: MutableEntityStorage)
}

private class EntitySourceFileWatcher<T : EntitySource>(
  val entitySource: KClass<T>,
  val containerToUrl: (T) -> String,
  val createNewSource: (T, VirtualFileUrl) -> T
) : LegacyFileWatcher {
  override fun onVfsChange(oldUrl: String,
                           newUrl: String,
                           entitiesWithVFU: List<EntityWithVirtualFileUrl>,
                           virtualFileManager: VirtualFileUrlManager,
                           diff: MutableEntityStorage) {
    val entities = diff.entitiesBySource { it::class == entitySource }
    for ((entitySource, mapOfEntities) in entities) {
      @Suppress("UNCHECKED_CAST")
      val urlFromContainer = containerToUrl(entitySource as T)
      if (!FileUtil.startsWith(urlFromContainer, oldUrl)) continue

      val newVfsUrl = virtualFileManager.fromUrl(newUrl + urlFromContainer.substring(oldUrl.length))
      val newEntitySource = createNewSource(entitySource, newVfsUrl)

      mapOfEntities.values.flatten().forEach {
        diff.modifyEntity(WorkspaceEntity.Builder::class.java, it) { this.entitySource = newEntitySource }
      }
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
private class EntityVirtualFileUrlWatcher<E : WorkspaceEntity, M : WorkspaceEntity.Builder<E>>(
  val entityClass: KClass<E>,
  val modifiableEntityClass: KClass<M>,
  val propertyName: String,
  val modificator: M.(VirtualFileUrl, VirtualFileUrl) -> Unit
) : LegacyFileWatcher {
  override fun onVfsChange(oldUrl: String,
                           newUrl: String,
                           entitiesWithVFU: List<EntityWithVirtualFileUrl>,
                           virtualFileManager: VirtualFileUrlManager,
                           diff: MutableEntityStorage) {
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
private class LibraryRootFileWatcher : LegacyFileWatcher {
  private val propertyName = LibraryEntity::roots.name

  override fun onVfsChange(oldUrl: String,
                           newUrl: String,
                           entitiesWithVFU: List<EntityWithVirtualFileUrl>,
                           virtualFileManager: VirtualFileUrlManager,
                           diff: MutableEntityStorage) {
    entitiesWithVFU.filter { LibraryEntity::class.isInstance(it.entity) && it.propertyName == propertyName }.forEach { entityWithVFU ->
      val oldVFU = entityWithVFU.virtualFileUrl
      val newVFU = virtualFileManager.fromUrl(newUrl + oldVFU.url.substring(oldUrl.length))

      entityWithVFU.entity as LibraryEntity
      val oldLibraryRoots = diff.resolve(entityWithVFU.entity.symbolicId)?.roots?.filter { it.url == oldVFU }
                            ?: error("Incorrect state of the VFU index")
      oldLibraryRoots.forEach { oldLibraryRoot ->
        val newLibraryRoot = LibraryRoot(newVFU, oldLibraryRoot.type, oldLibraryRoot.inclusionOptions)
        diff.modifyEntity(entityWithVFU.entity) {
          roots.remove(oldLibraryRoot)
          roots.add(newLibraryRoot)
        }
      }
    }
  }
}