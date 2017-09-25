/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.io.*
import com.intellij.util.loadElement
import com.intellij.util.write
import org.jdom.Element
import java.nio.file.Path

private val LOG = logger<FileSystemExternalSystemStorage>()

internal interface ExternalSystemStorage {
  val isDirty: Boolean

  fun remove(name: String)

  fun read(name: String): Element?

  fun write(name: String, element: Element, filter: JDOMUtil.ElementOutputFilter? = null)

  fun forceSave()

  fun rename(oldName: String, newName: String)
}

internal class ModuleFileSystemExternalSystemStorage(project: Project) : FileSystemExternalSystemStorage("modules", project) {
  companion object {
    private fun nameToFilename(name: String) = "${sanitizeFileName(name)}.xml"
  }

  override fun nameToPath(name: String) = super.nameToPath(nameToFilename(name))
}

internal class ProjectFileSystemExternalSystemStorage(project: Project) : FileSystemExternalSystemStorage("project", project)

internal abstract class FileSystemExternalSystemStorage(dirName: String, project: Project) : ExternalSystemStorage {
  override val isDirty = false

  protected val dir: Path = ExternalProjectsDataStorage.getProjectConfigurationDir(project).resolve(dirName)

  private var hasSomeData: Boolean

  init {
    val fileAttributes = dir.basicAttributesIfExists()
    when {
      fileAttributes == null -> hasSomeData = false
      fileAttributes.isRegularFile -> {
        // old binary format
        dir.parent.deleteChildrenStartingWith(dir.fileName.toString())
        hasSomeData = false
      }
      else -> {
        LOG.assertTrue(fileAttributes.isDirectory)
        hasSomeData = true
      }
    }
  }

  protected open fun nameToPath(name: String): Path = dir.resolve(name)

  override fun forceSave() {
  }

  override fun remove(name: String) {
    if (!hasSomeData) {
      return
    }

    nameToPath(name).delete()
  }

  override fun read(name: String): Element? {
    if (!hasSomeData) {
      return null
    }

    return nameToPath(name).inputStreamIfExists()?.use {
      loadElement(it)
    }
  }

  override fun write(name: String, element: Element, filter: JDOMUtil.ElementOutputFilter?) {
    hasSomeData = true
    element.write(nameToPath(name), filter = filter)
  }

  override fun rename(oldName: String, newName: String) {
    if (!hasSomeData) {
      return
    }

    val oldFile = nameToPath(oldName)
    if (oldFile.exists()) {
      oldFile.move(nameToPath(newName))
    }
  }
}