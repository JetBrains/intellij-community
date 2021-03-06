// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.TraceableDisposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.jps.serialization.getLegacyLibraryName
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridge
import com.intellij.workspaceModel.storage.CachedValue
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryRootTypeId
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

interface LibraryBridge : LibraryEx {
  val libraryId: LibraryId

  @ApiStatus.Internal
  fun getModifiableModel(builder: WorkspaceEntityStorageBuilder): LibraryEx.ModifiableModelEx
}

@ApiStatus.Internal
internal class LibraryBridgeImpl(
  internal var libraryTable: LibraryTable,
  val project: Project,
  initialId: LibraryId,
  initialEntityStorage: VersionedEntityStorage,
  private var targetBuilder: WorkspaceEntityStorageDiffBuilder?
) : LibraryBridge, RootProvider, TraceableDisposable(true) {

  override fun getModule(): Module? = (libraryTable as? ModuleLibraryTableBridge)?.module

  var entityStorage: VersionedEntityStorage = initialEntityStorage
    internal set(value) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      field = value
    }

  var entityId: LibraryId = initialId

  private var disposed = false

  internal fun cleanCachedValue() {
    entityStorage.clearCachedValue(librarySnapshotCached)
  }

  private val dispatcher = EventDispatcher.create(RootSetChangedListener::class.java)

  private val librarySnapshotCached = CachedValue { storage ->
    LibraryStateSnapshot(
      libraryEntity = storage.findLibraryEntity(this) ?: error("Cannot find entity for library with ID $entityId"),
      storage = storage,
      libraryTable = libraryTable,
      parentDisposable = this
    )
  }

  internal val librarySnapshot: LibraryStateSnapshot
    get() {
      checkDisposed()
      return entityStorage.cachedValue(librarySnapshotCached)
    }

  override val libraryId: LibraryId
    get() = entityId
  override fun getTable(): LibraryTable? = if (libraryTable is ModuleLibraryTableBridge) null else libraryTable
  override fun getRootProvider(): RootProvider = this
  override fun getPresentableName(): String = LibraryImpl.getPresentableName(this)

  override fun toString(): String {
    return "Library '$name', roots: ${librarySnapshot.libraryEntity.roots}"
  }

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx {
    return getModifiableModel(WorkspaceEntityStorageBuilder.from(librarySnapshot.storage))
  }
  override fun getModifiableModel(builder: WorkspaceEntityStorageBuilder): LibraryEx.ModifiableModelEx {
    return LibraryModifiableModelBridgeImpl(this, librarySnapshot, builder, targetBuilder, false)
  }
  override fun getSource(): Library? = null
  override fun getExternalSource(): ProjectModelExternalSource? = librarySnapshot.externalSource
  override fun getInvalidRootUrls(type: OrderRootType): List<String> = librarySnapshot.getInvalidRootUrls(type)
  override fun getKind(): PersistentLibraryKind<*>? = librarySnapshot.kind
  override fun getName(): String? = getLegacyLibraryName(entityId)
  override fun getUrls(rootType: OrderRootType): Array<String> = librarySnapshot.getUrls(rootType)
  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = librarySnapshot.getFiles(rootType)
  override fun getProperties(): LibraryProperties<*>? = librarySnapshot.properties
  override fun getExcludedRoots(): Array<VirtualFile> = librarySnapshot.excludedRoots
  override fun getExcludedRootUrls(): Array<String> = librarySnapshot.excludedRootUrls
  override fun isJarDirectory(url: String): Boolean = librarySnapshot.isJarDirectory(url)
  override fun isJarDirectory(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isJarDirectory(url, rootType)
  override fun isValid(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isValid(url, rootType)
  override fun hasSameContent(library: Library): Boolean {
    if (this === library) return true
    if (library !is LibraryBridgeImpl) return false

    if (name != library.name) return false
    if (kind != library.kind) return false
    if (properties != library.properties) return false
    if (librarySnapshot.libraryEntity.roots != library.librarySnapshot.libraryEntity.roots) return false
    if (!excludedRoots.contentEquals(library.excludedRoots)) return false
    return true
  }

  override fun readExternal(element: Element?) = throw NotImplementedError()
  override fun writeExternal(element: Element) = throw NotImplementedError()

  override fun addRootSetChangedListener(listener: RootSetChangedListener) = dispatcher.addListener(listener)
  override fun addRootSetChangedListener(listener: RootSetChangedListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }
  override fun removeRootSetChangedListener(listener: RootSetChangedListener) = dispatcher.removeListener(listener)

  override fun isDisposed(): Boolean = disposed
  override fun dispose() {
    checkDisposed()

    disposed = true
    kill(null)
  }

  private fun checkDisposed() {
    if (isDisposed) {
      val libraryEntity = entityStorage.cachedValue(librarySnapshotCached).libraryEntity
      val isDisposedGlobally = WorkspaceModel.getInstance(project).entityStorage.current.libraryMap.getDataByEntity(libraryEntity)?.isDisposed
      val message = """
        Library $entityId already disposed:
        Library id: $libraryId
        Entity: ${libraryEntity.run { "$name, $this" }}
        Is disposed in project model: $isDisposedGlobally
        Stack trace: $stackTrace
        """.trimIndent()
      try {
        throwDisposalError(message)
      }
      catch (e: Exception) {
        thisLogger().error(message, e)
        throw e
      }
    }
  }

  internal fun fireRootSetChanged() {
    dispatcher.multicaster.rootSetChanged(this)
  }

  fun clearTargetBuilder() {
    targetBuilder = null
  }

  companion object {
    private val libraryRootTypes = ConcurrentFactoryMap.createMap<String, LibraryRootTypeId> { LibraryRootTypeId(it) }

    internal fun OrderRootType.toLibraryRootType(): LibraryRootTypeId = when (this) {
      OrderRootType.CLASSES -> LibraryRootTypeId.COMPILED
      OrderRootType.SOURCES -> LibraryRootTypeId.SOURCES
      else -> libraryRootTypes[name()]!!
    }
  }
}
