// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packaging.impl.artifacts.workspacemodel

import com.intellij.configurationStore.deserializeInto
import com.intellij.java.workspace.entities.*
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.packaging.artifacts.ArtifactPointerManager
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.elements.PackagingElement
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.elements.PackagingExternalMapping
import com.intellij.packaging.impl.artifacts.UnknownPackagingElementTypeException
import com.intellij.packaging.impl.artifacts.workspacemodel.packaging.elements
import com.intellij.packaging.impl.artifacts.workspacemodel.packaging.mutableElements
import com.intellij.packaging.impl.elements.*
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import org.jetbrains.annotations.TestOnly
import org.jetbrains.jps.util.JpsPathUtil
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

private val rwLock: ReadWriteLock = ReentrantReadWriteLock()

/**
 * We use VersionedEntityStorage here instead of the builder/snapshot because we need an actual data of the storage for double check
 */
internal fun CompositePackagingElementEntity.toCompositeElement(
  project: Project,
  storage: VersionedEntityStorage,
  addToMapping: Boolean = true,
  mappingsCollector: MutableList<Pair<PackagingElementEntity, PackagingElement<*>>> = ArrayList(),
): CompositePackagingElement<*> {
  rwLock.readLock().lock()

  var existing = try {
    testCheck(1)
    storage.base.elements.getDataByEntity(this)
  }
  catch (e: Exception) {
    rwLock.readLock().unlock()
    throw e
  }
  if (existing == null) {
    rwLock.readLock().unlock()
    rwLock.writeLock().lock()
    try {
      ProgressManager.checkCanceled()
      testCheck(2)
      // Double check
      existing = storage.base.elements.getDataByEntity(this)
      if (existing == null) {
        val element = when (this) {
          is DirectoryPackagingElementEntity -> {
            val element = DirectoryPackagingElement(this.directoryName)
            this.children.pushTo(element, project, storage, mappingsCollector)
            element
          }
          is ArchivePackagingElementEntity -> {
            val element = ArchivePackagingElement(this.fileName)
            this.children.pushTo(element, project, storage, mappingsCollector)
            element
          }
          is ArtifactRootElementEntity -> {
            val element = ArtifactRootElementImpl()
            this.children.pushTo(element, project, storage, mappingsCollector)
            element
          }
          is CustomPackagingElementEntity -> {
            val unpacked = unpackCustomElement(storage, project, mappingsCollector)
            if (unpacked !is CompositePackagingElement<*>) {
              error("Expected composite packaging element")
            }
            unpacked
          }
          else -> unknownElement()
        }
        mappingsCollector.add(this to element)
        if (addToMapping) {
          ProgressManager.checkCanceled()
          val storageBase = storage.base
          if (storageBase is MutableEntityStorage) {
            val mutableMapping = storageBase.mutableElements
            for ((entity, mapping) in mappingsCollector) {
              mutableMapping.addMapping(entity, mapping)
            }
          }
          else {
            (WorkspaceModel.getInstance(project) as WorkspaceModelImpl).updateProjectModelSilent("Apply packaging elements mappings") {
              val mutableMapping = it.mutableElements
              for ((entity, mapping) in mappingsCollector) {
                mutableMapping.addMapping(entity, mapping)
              }
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
  ProgressManager.checkCanceled()
  return existing as CompositePackagingElement<*>
}

fun PackagingElementEntity.toElement(
  project: Project,
  storage: VersionedEntityStorage,
  addToMapping: Boolean = true,
  mappingsCollector: MutableList<Pair<PackagingElementEntity, PackagingElement<*>>> = ArrayList(),
): PackagingElement<*> {
  rwLock.readLock().lock()

  var existing = try {
    testCheck(3)
    storage.base.elements.getDataByEntity(this)
  }
  catch (e: Exception) {
    rwLock.readLock().unlock()
    throw e
  }
  if (existing == null) {
    rwLock.readLock().unlock()
    rwLock.writeLock().lock()
    try {
      ProgressManager.checkCanceled()
      testCheck(4)
      // Double check
      existing = storage.base.elements.getDataByEntity(this)
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
          is ArchivePackagingElementEntity -> {
            val element = this.toCompositeElement(project, storage, false, mappingsCollector)
            ProgressManager.checkCanceled()
            element
          }
          is DirectoryPackagingElementEntity -> {
            val element = this.toCompositeElement(project, storage, false, mappingsCollector)
            ProgressManager.checkCanceled()
            element
          }
          is ArtifactRootElementEntity -> {
            val element = this.toCompositeElement(project, storage, false, mappingsCollector)
            ProgressManager.checkCanceled()
            element
          }
          is LibraryFilesPackagingElementEntity -> {
            val mapping = storage.base.getExternalMapping<PackagingElement<*>>(PackagingExternalMapping.key)
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
          is CustomPackagingElementEntity -> unpackCustomElement(storage, project, mappingsCollector)
          else -> unknownElement()
        }

        mappingsCollector.add(this to element)
        if (addToMapping) {
          ProgressManager.checkCanceled()
          val storageBase = storage.base
          if (storageBase is MutableEntityStorage) {
            val mutableMapping = storageBase.mutableElements
            for ((entity, mapping) in mappingsCollector) {
              mutableMapping.addMapping(entity, mapping)
            }
          }
          else {
            (project.workspaceModel as WorkspaceModelImpl).updateProjectModelSilent("Apply packaging elements mappings (toElement)") {
              val mutableMapping = it.mutableElements
              for ((entity, mapping) in mappingsCollector) {
                mutableMapping.addMapping(entity, mapping)
              }
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
  return existing as PackagingElement<*>
}

private fun CustomPackagingElementEntity.unpackCustomElement(
  storage: VersionedEntityStorage,
  project: Project,
  mappingsCollector: MutableList<Pair<PackagingElementEntity, PackagingElement<*>>>,
): PackagingElement<*> {
  val mapping = storage.base.getExternalMapping<PackagingElement<*>>(PackagingExternalMapping.key)
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
    this.children.pushTo(packagingElement, project, storage, mappingsCollector)
  }
  return packagingElement
}

private fun PackagingElementEntity.unknownElement(): Nothing {
  error("Unknown packaging element entity: $this")
}

private fun List<PackagingElementEntity>.pushTo(
  element: CompositePackagingElement<*>,
  project: Project,
  storage: VersionedEntityStorage,
  mappingsCollector: MutableList<Pair<PackagingElementEntity, PackagingElement<*>>>,
) {
  val children = this.map {
    ProgressManager.checkCanceled()
    it.toElement(project, storage, addToMapping = false, mappingsCollector = mappingsCollector)
  }.toList()
  children.reversed().forEach { element.addFirstChild(it) }
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
