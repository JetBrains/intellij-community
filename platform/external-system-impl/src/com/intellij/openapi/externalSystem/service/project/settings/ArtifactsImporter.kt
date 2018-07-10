// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.artifacts.ArtifactPointerManager
import com.intellij.packaging.artifacts.ArtifactType
import com.intellij.packaging.artifacts.ModifiableArtifact
import com.intellij.packaging.elements.CompositePackagingElement
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.packaging.impl.elements.*
import com.intellij.util.ObjectUtils.consumeIfCast

class ArtifactsImporter: ConfigurationHandler {

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val artifacts = configuration.find("artifacts") as? List<*> ?: return

    if (artifacts.isEmpty()) {
      return
    }

    val modifiableModel = modelsProvider.modifiableArtifactModel

    artifacts.forEach { value ->
      val artifactConfig = value as? Map<*, *> ?: return@forEach
      val name = artifactConfig["name"]
      if (name !is String) return@forEach
      val type: ArtifactType = PlainArtifactType.getInstance()
      val artifact: ModifiableArtifact = modifiableModel
                                           .findArtifact(name)?.let { modifiableModel.getOrCreateModifiableArtifact(it) }
                                         ?: modifiableModel.addArtifact(name, type)
      val rootElement = type.createRootElement(name)
      populateArtifact(project, rootElement, artifactConfig)
      artifact.rootElement = rootElement
    }
  }

  private fun populateArtifact(project: Project, element: CompositePackagingElement<*>, config: Map<*, *>) {
    consumeIfCast(config["children"], List::class.java) {
      it.forEach { child ->
        consumeIfCast(child, Map::class.java) child@{ config ->
          val type = config["type"] as? String ?: return@child
          when (type) {
            "ARTIFACT" -> {  }

            "DIR" -> {
              val directory = DirectoryPackagingElement(config["name"] as? String ?: "no_name")
              populateArtifact(project, directory, config)
              element.addOrFindChild(directory)
            }

            "ARCHIVE" -> {
              val archive = ArchivePackagingElement(config["name"] as? String ?: "no_name")
              populateArtifact(project, archive, config)
              element.addOrFindChild(archive)
            }

            "LIBRARY_FILES" -> {
              consumeIfCast(config["libraries"], List::class.java) {
                it.forEach { libraryCoordinates ->
                  consumeIfCast(libraryCoordinates, Map::class.java) { library ->
                    val nameSuffix = "${library["group"]}:${library["artifact"]}:${library["version"]}"
                    project.findLibraryByNameSuffix(nameSuffix)?.let {
                      val libraryClasses = LibraryPackagingElement(LibraryTablesRegistrar.PROJECT_LEVEL, it.name, null)
                      element.addOrFindChild(libraryClasses)
                    }
                  }
                }
              }
            }

            "MODULE_OUTPUT" -> {
              val moduleName = config["moduleName"] as? String
              project.ifModuleFound(moduleName) { module ->
                val pointer: ModulePointer = ModulePointerManager.getInstance(project).create(module)
                val moduleOutput = ProductionModuleOutputPackagingElement(project, pointer)
                element.addOrFindChild(moduleOutput)
              }
            }

            "MODULE_TEST_OUTPUT" -> {
              val moduleName = config["moduleName"] as? String
              project.ifModuleFound(moduleName) { module ->
                val pointer: ModulePointer = ModulePointerManager.getInstance(project).create(module)
                val moduleOutput = TestModuleOutputPackagingElement(project, pointer)
                element.addOrFindChild(moduleOutput)
              }
            }

            "MODULE_SRC" -> {
              val moduleName = config["moduleName"] as? String
              project.ifModuleFound(moduleName) { module ->
                val pointer: ModulePointer = ModulePointerManager.getInstance(project).create(module)
                val moduleOutput = ProductionModuleSourcePackagingElement(project, pointer)
                element.addOrFindChild(moduleOutput)
              }
            }

            "FILE" -> {
              consumeIfCast(config["sourceFiles"], List::class.java) {
                it.filterIsInstance(String::class.java).forEach { path ->
                  val fileCopy = FileCopyPackagingElement(path)
                  element.addOrFindChild(fileCopy)
                }
              }
            }

            "DIR_CONTENT" ->  {
              consumeIfCast(config["sourceFiles"], List::class.java) {
                it.filterIsInstance(String::class.java).forEach { path ->
                  val dirCopy = DirectoryCopyPackagingElement(path)
                  element.addOrFindChild(dirCopy)
                }
              }
            }

            "EXTRACTED_DIR" ->  {
              consumeIfCast(config["sourceFiles"], List::class.java) {
                it.filterIsInstance(String::class.java).forEach { path ->
                  val fileCopy = ExtractedDirectoryPackagingElement(path,"/")
                  element.addOrFindChild(fileCopy)
                }
              }
            }

            "ARTIFACT_REF"  -> {
              val artifactName = config["artifactName"] as? String ?: return@child
              val artifactsManager = ArtifactManager.getInstance(project)
              artifactsManager.findArtifact(artifactName)?.let {
                val pointer = ArtifactPointerManager.getInstance(project).createPointer(it)
                val artifactRef = ArtifactPackagingElement(project, pointer)
                element.addOrFindChild(artifactRef)
              }
            }
          }
        }
      }
    }
  }

  private fun Project.findLibraryByNameSuffix(nameSuffix: String): Library? {
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(this@findLibraryByNameSuffix)
    return libraryTable.libraries.find { it.name?.endsWith(nameSuffix) ?: false }
  }

  private fun Project.ifModuleFound(moduleName: String?, processor: (Module) -> Unit) {
    if (moduleName != null) { ModuleManager.getInstance(this@ifModuleFound).findModuleByName(moduleName)?.let { processor(it) } }
  }
}