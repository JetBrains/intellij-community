package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ExcludeFolder
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import org.jetbrains.jps.model.JpsElement
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

internal class ContentEntryBridge(internal val model: ModuleRootModelBridge,
                                  val sourceRootEntities: List<SourceRootEntity>,
                                  val entity: ContentRootEntity,
                                  val updater: (((WorkspaceEntityStorageDiffBuilder) -> Unit) -> Unit)?) : ContentEntry {
  private val excludeFolders by lazy {
    entity.excludedUrls.map { ExcludeFolderBridge(this, it) }
  }
  private val sourceFolders by lazy {
    sourceRootEntities.map { SourceFolderBridge(this, it) }
  }

  override fun getFile(): VirtualFile? {
    val virtualFilePointer = entity.url as VirtualFilePointer
    return virtualFilePointer.file
  }

  override fun getUrl(): String = entity.url.url

  override fun getSourceFolders(): Array<SourceFolder> = sourceFolders.toTypedArray()
  override fun getExcludeFolders(): Array<ExcludeFolder> = excludeFolders.toTypedArray()
  override fun getExcludePatterns(): List<String> = entity.excludedPatterns

  override fun getExcludeFolderFiles(): Array<VirtualFile> {
    val result = ArrayList<VirtualFile>(excludeFolders.size)
    excludeFolders.mapNotNullTo(result) { it.file }
    for (excludePolicy in DirectoryIndexExcludePolicy.EP_NAME.getExtensions(model.module.project)) {
      excludePolicy.getExcludeRootsForModule(model).mapNotNullTo(result) { it.file }
    }
    return VfsUtilCore.toVirtualFileArray(result)
  }

  override fun getExcludeFolderUrls(): MutableList<String> {
    val result = ArrayList<String>(excludeFolders.size)
    excludeFolders.mapTo(result) { it.url }
    for (excludePolicy in DirectoryIndexExcludePolicy.EP_NAME.getExtensions(model.module.project)) {
      excludePolicy.getExcludeRootsForModule(model).mapTo(result) { it.url }
    }
    return result
  }

  override fun equals(other: Any?): Boolean {
    return (other as? ContentEntry)?.url == url
  }

  override fun hashCode(): Int {
    return url.hashCode()
  }

  override fun isSynthetic() = false
  override fun getRootModel() = model
  override fun getSourceFolders(rootType: JpsModuleSourceRootType<*>): List<SourceFolder> = getSourceFolders(setOf(rootType))
  override fun getSourceFolders(rootTypes: Set<JpsModuleSourceRootType<*>>): List<SourceFolder> = sourceFolders.filter { it.rootType in rootTypes }
  override fun getSourceFolderFiles() = sourceFolders.mapNotNull { it.file }.toTypedArray()

  override fun <P : JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder = throwReadonly()
  override fun <P : JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>, properties: P): SourceFolder = throwReadonly()
  override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean) = throwReadonly()
  override fun addSourceFolder(file: VirtualFile, isTestSource: Boolean, packagePrefix: String): SourceFolder = throwReadonly()
  override fun <P : JpsElement?> addSourceFolder(file: VirtualFile, type: JpsModuleSourceRootType<P>) = throwReadonly()
  override fun addSourceFolder(url: String, isTestSource: Boolean): SourceFolder = throwReadonly()
  override fun <P : JpsElement?> addSourceFolder(url: String, type: JpsModuleSourceRootType<P>): SourceFolder = throwReadonly()
  override fun removeSourceFolder(sourceFolder: SourceFolder) = throwReadonly()
  override fun clearSourceFolders() = throwReadonly()
  override fun addExcludeFolder(file: VirtualFile): ExcludeFolder = throwReadonly()
  override fun addExcludeFolder(url: String): ExcludeFolder = throwReadonly()
  override fun removeExcludeFolder(excludeFolder: ExcludeFolder) = throwReadonly()
  override fun removeExcludeFolder(url: String): Boolean = throwReadonly()
  override fun clearExcludeFolders() = throwReadonly()
  override fun addExcludePattern(pattern: String) = throwReadonly()
  override fun removeExcludePattern(pattern: String) = throwReadonly()
  override fun setExcludePatterns(patterns: MutableList<String>) = throwReadonly()

  private fun throwReadonly(): Nothing = error("This model is read-only")
}
