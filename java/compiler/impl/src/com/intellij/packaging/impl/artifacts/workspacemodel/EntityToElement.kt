// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.configurationStore.deserializeInto
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.packaging.artifacts.ArtifactPointerManager
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.UnknownPackagingElementTypeException
import com.intellij.packaging.impl.elements.*
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

private val rwLock: ReadWriteLock = if (
  Registry.`is`("ide.new.project.model.artifacts.sync.initialization")
) ReentrantReadWriteLock()
else EmptyReadWriteLock()

internal fun CompositePackagingElementEntity.toCompositeElement(
  project: Project,
  storage: VersionedEntityStorage,
  addToMapping: Boolean = true,
): CompositePackagingElement<*> {
  rwLock.readLock().lock()

  var existing = try {
    testCheck(1)
    storage.current.elements.getDataByEntity(this)
  }
  catch (e: Exception) {
    rwLock.readLock().unlock()
    throw e
  }
  if (existing == null) {
    rwLock.readLock().unlock()
    rwLock.writeLock().lock()
    try {
      testCheck(2)
      // Double check
      existing = storage.current.elements.getDataByEntity(this)
      if (existing == null) {
        val element = when (this) {
          is DirectoryPackagingElementEntity -> {
            val element = DirectoryPackagingElement(this.directoryName)
            this.children.pushTo(element, project, storage)
            element
          }
          is ArchivePackagingElementEntity -> {
            val element = ArchivePackagingElement(this.fileName)
            this.children.pushTo(element, project, storage)
            element
          }
          is ArtifactRootElementEntity -> {
            val element = ArtifactRootElementImpl()
            this.children.pushTo(element, project, storage)
            element
          }
          is CustomPackagingElementEntity -> {
            val unpacked = unpackCustomElement(storage, project)
            if (unpacked !is CompositePackagingElement<*>) {
              error("Expected composite packaging element")
            }
            unpacked
          }
          else -> unknownElement()
        }
        if (addToMapping) {
          val storageBase = storage.base
          if (storageBase is WorkspaceEntityStorageBuilder) {
            val mutableMapping = storageBase.mutableElements
            mutableMapping.addMapping(this, element)
          }
          else {
            WorkspaceModel.getInstance(project).updateProjectModelSilent {
              val mutableMapping = it.mutableElements
              mutableMapping.addMapping(this, element)
            }
          }
        }
        existing = element
      }
      // Lock downgrade
      rwLock.readLock().lock()
    }
    finally {
      rwLock.writeLock().unlock()
    }
  }

  rwLock.readLock().unlock()
  return existing as CompositePackagingElement<*>
}

fun PackagingElementEntity.toElement(project: Project, storage: VersionedEntityStorage): PackagingElement<*> {
  rwLock.readLock().lock()

  var existing = try {
    testCheck(3)
    storage.current.elements.getDataByEntity(this)
  }
  catch (e: Exception) {
    rwLock.readLock().unlock()
    throw e
  }
  if (existing == null) {
    rwLock.readLock().unlock()
    rwLock.writeLock().lock()
    try {
      testCheck(4)
      // Double check
      existing = storage.current.elements.getDataByEntity(this)
      if (existing == null) {
        val element = when (this) {
          is ModuleOutputPackagingElementEntity -> {
            val module = this.module
            if (module != null) {
              val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
              ProductionModuleOutputPackagingElement(project, modulePointer)
            }
            else {
              ProductionModuleOutputPackagingElement(project)
            }
          }
          is ModuleTestOutputPackagingElementEntity -> {
            val module = this.module
            if (module != null) {
              val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
              TestModuleOutputPackagingElement(project, modulePointer)
            }
            else {
              TestModuleOutputPackagingElement(project)
            }
          }
          is ModuleSourcePackagingElementEntity -> {
            val module = this.module
            if (module != null) {
              val modulePointer = ModulePointerManager.getInstance(project).create(module.name)
              ProductionModuleSourcePackagingElement(project, modulePointer)
            }
            else {
              ProductionModuleSourcePackagingElement(project)
            }
          }
          is ArtifactOutputPackagingElementEntity -> {
            val artifact = this.artifact
            if (artifact != null) {
              val artifactPointer = ArtifactPointerManager.getInstance(project).createPointer(artifact.name)
              ArtifactPackagingElement(project, artifactPointer)
            }
            else {
              ArtifactPackagingElement(project)
            }
          }
          is ExtractedDirectoryPackagingElementEntity -> {
            val pathInArchive = this.pathInArchive
            val archive = this.filePath
            ExtractedDirectoryPackagingElement(JpsPathUtil.urlToPath(archive.url), pathInArchive)
          }
          is FileCopyPackagingElementEntity -> {
            val file = this.filePath
            val renamedOutputFileName = this.renamedOutputFileName
            if (renamedOutputFileName != null) {
              FileCopyPackagingElement(JpsPathUtil.urlToPath(file.url), renamedOutputFileName)
            }
            else {
              FileCopyPackagingElement(JpsPathUtil.urlToPath(file.url))
            }
          }
          is DirectoryCopyPackagingElementEntity -> {
            val directory = this.filePath
            DirectoryCopyPackagingElement(JpsPathUtil.urlToPath(directory.url))
          }
          is ArchivePackagingElementEntity -> this.toCompositeElement(project, storage, false)
          is DirectoryPackagingElementEntity -> this.toCompositeElement(project, storage, false)
          is ArtifactRootElementEntity -> this.toCompositeElement(project, storage, false)
          is LibraryFilesPackagingElementEntity -> {
            val mapping = storage.current.getExternalMapping<PackagingElement<*>>("intellij.artifacts.packaging.elements")
            val data = mapping.getDataByEntity(this)
            if (data != null) {
              return data
            }

            val library = this.library
            if (library != null) {
              val tableId = library.tableId
              val moduleName = if (tableId is LibraryTableId.ModuleLibraryTableId) tableId.moduleId.name else null
              LibraryPackagingElement(tableId.level, library.name, moduleName)
            }
            else {
              LibraryPackagingElement()
            }
          }
          is CustomPackagingElementEntity -> unpackCustomElement(storage, project)
          else -> unknownElement()
        }

        val storageBase = storage.base
        if (storageBase is WorkspaceEntityStorageBuilder) {
          val mutableMapping = storageBase.mutableElements
          mutableMapping.addIfAbsent(this, element)
        }
        else {
          WorkspaceModel.getInstance(project).updateProjectModelSilent {
            val mutableMapping = it.mutableElements
            mutableMapping.addIfAbsent(this, element)
          }
        }
        existing = element
      }
      // Lock downgrade
      rwLock.readLock().lock()
    }
    finally {
      rwLock.writeLock().unlock()
    }
  }

  rwLock.readLock().unlock()
  return existing as PackagingElement<*>
}

private fun CustomPackagingElementEntity.unpackCustomElement(storage: VersionedEntityStorage,
                                                             project: Project): PackagingElement<*> {
  val mapping = storage.current.getExternalMapping<PackagingElement<*>>("intellij.artifacts.packaging.elements")
  val data = mapping.getDataByEntity(this)
  if (data != null) {
    return data
  }

  // TODO: 09.04.2021 It should be invalid artifact instead of error
  val elementType = PackagingElementFactory.getInstance().findElementType(this.typeId)
                    ?: throw UnknownPackagingElementTypeException(this.typeId)

  @Suppress("UNCHECKED_CAST")
  val packagingElement = elementType.createEmpty(project) as PackagingElement<Any>
  val state = packagingElement.state
  if (state != null) {
    val element = JDOMUtil.load(this.propertiesXmlTag)
    element.deserializeInto(state)
    packagingElement.loadState(state)
  }
  if (packagingElement is CompositePackagingElement<*>) {
    this.children.pushTo(packagingElement, project, storage)
  }
  return packagingElement
}

private fun PackagingElementEntity.unknownElement(): Nothing {
  error("Unknown packaging element entity: $this")
}

private fun Sequence<PackagingElementEntity>.pushTo(element: CompositePackagingElement<*>,
                                                    project: Project,
                                                    storage: VersionedEntityStorage) {
  val children = this.map { it.toElement(project, storage) }.toList()
  children.reversed().forEach { element.addFirstChild(it) }
}

private class EmptyReadWriteLock : ReadWriteLock {
  override fun readLock(): Lock = EmptyLock

  override fun writeLock(): Lock = EmptyLock
}

private object EmptyLock : Lock {
  override fun lock() {
    // Nothing
  }

  override fun lockInterruptibly() {
    // Nothing
  }

  override fun tryLock(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun tryLock(time: Long, unit: TimeUnit): Boolean {
    throw UnsupportedOperationException()
  }

  override fun unlock() {
    // Nothing
  }

  override fun newCondition(): Condition {
    throw UnsupportedOperationException()
  }
}

// Instruments for testing code with unexpected exceptions
// I assume that tests with bad approach is better than no tests at all
@TestOnly
object ArtifactsTestingState {
  var testLevel: Int = 0
  var exceptionsThrows: MutableList<Int> = ArrayList()

  fun reset() {
    testLevel = 0
    exceptionsThrows = ArrayList()
  }
}

@Suppress("TestOnlyProblems")
private fun testCheck(level: Int) {
  if (level == ArtifactsTestingState.testLevel) {
    ArtifactsTestingState.exceptionsThrows += level
    error("Exception on level: $level")
  }
}
