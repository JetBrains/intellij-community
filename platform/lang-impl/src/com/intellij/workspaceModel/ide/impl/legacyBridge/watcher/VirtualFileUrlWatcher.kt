// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.watcher

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsProjectFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

@ApiStatus.Internal
open class VirtualFileUrlWatcher(val project: Project) {
  private val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
  internal var isInsideFilePointersUpdate: Boolean = false
    private set

  private val pointers = listOf(
    // Library roots
    LibraryRootFileWatcher(),
    // Sdk roots
    SdkRootFileWatcher(),
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
    EntitySourceFileWatcher(JpsProjectFileEntitySource.ExactFile::class, { it.file.url }, { source, file -> source.copy(file = file) }),
    EntitySourceFileWatcher(JpsProjectFileEntitySource.FileInDirectory::class, { it.directory.url },
                            { source, file -> source.copy(directory = file) })
  )

  fun onVfsChange(oldUrl: String, newUrl: String) {
    try {
      isInsideFilePointersUpdate = true
      val entityWithVirtualFileUrl = mutableListOf<EntityWithVirtualFileUrl>()
      WorkspaceModel.getInstance(project).updateProjectModel("On VFS change") { diff ->
        val oldFileUrl = virtualFileManager.getOrCreateFromUrl(oldUrl)
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
      val virtualFileUrlIndex = storage.getVirtualFileUrlIndex() as VirtualFileIndex
      virtualFileUrlIndex.findEntitiesToPropertyNameByUrl(virtualFileUrl).forEach {
        aggregator.add(EntityWithVirtualFileUrl(it.first, virtualFileUrl, it.second))
        hasEntities = true
      }
      return hasEntities
    }

  }
}

@ApiStatus.Internal
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
    val entitiesMap = diff.entitiesBySource { it::class == entitySource }.groupBy { it.entitySource }
    for ((entitySource, entities) in entitiesMap) {
      @Suppress("UNCHECKED_CAST")
      val urlFromContainer = containerToUrl(entitySource as T)
      val newVfsUrl = when {
        FileUtil.startsWith(urlFromContainer, oldUrl) -> newUrl + urlFromContainer.substring(oldUrl.length)
        isImlFileOfModuleMoved(oldUrl, newUrl, urlFromContainer, entities) -> newUrl.substringBeforeLast('/') 
        else -> continue
      } 
        
      val newEntitySource = createNewSource(entitySource, virtualFileManager.getOrCreateFromUrl(newVfsUrl))

      entities.forEach { entity ->
        diff.modifyEntity(WorkspaceEntity.Builder::class.java, entity) { this.entitySource = newEntitySource }
      }
    }
  }

  /**
   * Detects whether [oldUrl] points of an iml file of a module which was moved to a different directory. 
   * We need to check this case separately because [ModuleEntity.entitySource] stores the path to the parent directory of iml file, not
   * path to iml file itself (see IJPL-158284).
   */
  private fun isImlFileOfModuleMoved(
    oldUrl: String,
    newUrl: String,
    entitySourceUrl: String,
    entities: List<WorkspaceEntity>,
  ): Boolean {
    if (!oldUrl.endsWith(ModuleFileType.DOT_DEFAULT_EXTENSION) || oldUrl.substringBeforeLast('/') != entitySourceUrl) return false
    val moduleFileName = oldUrl.substringAfterLast('/')
    if (moduleFileName != newUrl.substringAfterLast('/')) return false
    
    val moduleNameFromUrl = moduleFileName.removeSuffix(ModuleFileType.DOT_DEFAULT_EXTENSION)
    return entities.any { (it as? ModuleEntity)?.name == moduleNameFromUrl }
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

      val newContainer = virtualFileManager.getOrCreateFromUrl(newTrackedUrl)
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
      val newVFU = virtualFileManager.getOrCreateFromUrl(newUrl + oldVFU.url.substring(oldUrl.length))

      entityWithVFU.entity as LibraryEntity
      val oldLibraryRoots = diff.resolve(entityWithVFU.entity.symbolicId)?.roots?.filter { it.url == oldVFU }
                            ?: error("Incorrect state of the VFU index")
      oldLibraryRoots.forEach { oldLibraryRoot ->
        val newLibraryRoot = LibraryRoot(newVFU, oldLibraryRoot.type, oldLibraryRoot.inclusionOptions)
        diff.modifyLibraryEntity(entityWithVFU.entity) {
          roots.remove(oldLibraryRoot)
          roots.add(newLibraryRoot)
        }
      }
    }
  }
}

/**
 * It's responsible for updating complex case than [VirtualFileUrl] contains not in the entity itself but in internal data class.
 * This is about SdkMainEntity -> roots (SdkRoot) -> url (VirtualFileUrl).
 */
private class SdkRootFileWatcher : LegacyFileWatcher {
  private val propertyName = SdkEntity::roots.name

  override fun onVfsChange(oldUrl: String,
                           newUrl: String,
                           entitiesWithVFU: List<EntityWithVirtualFileUrl>,
                           virtualFileManager: VirtualFileUrlManager,
                           diff: MutableEntityStorage) {
    entitiesWithVFU.filter { SdkEntity::class.isInstance(it.entity) && it.propertyName == propertyName }.forEach { entityWithVFU ->
      val oldVFU = entityWithVFU.virtualFileUrl
      val newVFU = virtualFileManager.getOrCreateFromUrl(newUrl + oldVFU.url.substring(oldUrl.length))

      entityWithVFU.entity as SdkEntity
      val oldSdkRoots = diff.resolve(entityWithVFU.entity.symbolicId)?.roots?.filter { it.url == oldVFU }
                            ?: error("Incorrect state of the VFU index")
      oldSdkRoots.forEach { oldSdkRoot ->
        val newSdkRoot = SdkRoot(newVFU, oldSdkRoot.type)
        diff.modifySdkEntity(entityWithVFU.entity) {
          roots.remove(oldSdkRoot)
          roots.add(newSdkRoot)
        }
      }
    }
  }
}