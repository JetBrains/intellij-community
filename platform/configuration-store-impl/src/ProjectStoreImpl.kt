/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.configurationStore

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl.UnableToSaveProjectNotification
import com.intellij.openapi.project.impl.ProjectStoreClassProvider
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import java.io.File
import java.io.IOException
import java.util.*

abstract class ProjectStoreBase(override final val project: ProjectImpl) : ComponentStoreImpl(), IProjectStore {
  // protected setter used in upsource
  // Zelix KlassMaster - ERROR: Could not find method 'getScheme()'
  var scheme = StorageScheme.DEFAULT

  override final var loadPolicy = StateLoadPolicy.LOAD

  override final fun isOptimiseTestLoadSpeed() = loadPolicy != StateLoadPolicy.LOAD

  override final fun getStorageScheme() = scheme

  override abstract val storageManager: StateStorageManagerImpl

  override final fun setOptimiseTestLoadSpeed(value: Boolean) {
    // we don't load default state in tests as app store does because
    // 1) we should not do it
    // 2) it was so before, so, we preserve old behavior (otherwise RunManager will load template run configurations)
    loadPolicy = if (value) StateLoadPolicy.NOT_LOAD else StateLoadPolicy.LOAD
  }

  override fun getProjectFilePath() = storageManager.expandMacro(StoragePathMacros.PROJECT_FILE)

  override final fun getWorkspaceFilePath() = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  override final fun clearStorages() {
    storageManager.clearStorages()
  }

  override final fun loadProjectFromTemplate(defaultProject: Project) {
    defaultProject.save()

    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy()
    if (element != null) {
      (storageManager.getOrCreateStorage(StoragePathMacros.PROJECT_FILE) as XmlElementStorage).setDefaultState(element)
    }
  }

  override final fun getProjectBasePath(): String {
    val path = PathUtilRt.getParentPath(projectFilePath)
    return if (scheme == StorageScheme.DEFAULT) path else PathUtilRt.getParentPath(path)
  }

  // used in upsource
  protected fun setPath(filePath: String, refreshVfs: Boolean, useOldWorkspaceContentIfExists: Boolean) {
    val storageManager = storageManager
    val fs = LocalFileSystem.getInstance()
    if (FileUtilRt.extensionEquals(filePath, ProjectFileType.DEFAULT_EXTENSION)) {
      scheme = StorageScheme.DEFAULT

      storageManager.addMacro(StoragePathMacros.PROJECT_FILE, filePath)

      val workspacePath = composeWsPath(filePath)
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspacePath)

      if (refreshVfs) {
        invokeAndWaitIfNeed {
          VfsUtil.markDirtyAndRefresh(false, true, false, fs.refreshAndFindFileByPath(filePath), fs.refreshAndFindFileByPath(workspacePath))
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = !File(filePath).exists()
      }
    }
    else {
      scheme = StorageScheme.DIRECTORY_BASED

      val file = File(filePath)
      // if useOldWorkspaceContentIfExists false, so, file path is expected to be correct (we must avoid file io operations)
      val isDir = !useOldWorkspaceContentIfExists || file.isDirectory
      val configDir = "${(if (isDir) filePath else PathUtilRt.getParentPath(filePath))}/${Project.DIRECTORY_STORE_FOLDER}"
      storageManager.addMacro(StoragePathMacros.PROJECT_CONFIG_DIR, configDir)
      storageManager.addMacro(StoragePathMacros.PROJECT_FILE, "$configDir/misc.xml")
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, "$configDir/workspace.xml")


      if (!isDir) {
        val workspace = File(workspaceFilePath)
        if (!workspace.exists()) {
          useOldWorkspaceContent(filePath, workspace)
        }
      }

      if (ApplicationManager.getApplication().isUnitTestMode) {
        // load state only if there are existing files
        isOptimiseTestLoadSpeed = !file.exists()
      }

      if (refreshVfs) {
        invokeAndWaitIfNeed { VfsUtil.markDirtyAndRefresh(false, true, true, fs.refreshAndFindFileByPath(configDir)) }
      }
    }
  }
}

private open class ProjectStoreImpl(project: ProjectImpl, private val pathMacroManager: PathMacroManager) : ProjectStoreBase(project) {
  init {
    assert(!project.isDefault)
  }

  override final fun getPathMacroManagerForDefaults() = pathMacroManager

  override val storageManager = ProjectStateStorageManager(pathMacroManager.createTrackingSubstitutor(), project)

  override fun setPath(filePath: String) {
    setPath(filePath, true, true)
  }

  override fun getProjectName(): String {
    if (scheme == StorageScheme.DIRECTORY_BASED) {
      val baseDir = projectBasePath
      val nameFile = File(File(projectBasePath, Project.DIRECTORY_STORE_FOLDER), ProjectImpl.NAME_FILE)
      if (nameFile.exists()) {
        try {
          val name = nameFile.inputStream().reader().useLines() {
            it.firstOrNull { !it.isEmpty() }?.trim()
          }

          if (name != null) {
            return name
          }
        }
        catch (ignored: IOException) {
        }
      }

      return PathUtilRt.getFileName(baseDir).replace(":", "")
    }
    else {
      var temp = PathUtilRt.getFileName(projectFilePath)
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName(temp)
      if (fileType is ProjectFileType) {
        temp = temp.substring(0, temp.length() - fileType.defaultExtension.length() - 1)
      }
      val i = temp.lastIndexOf(File.separatorChar)
      if (i >= 0) {
        temp = temp.substring(i + 1, temp.length() - i + 1)
      }
      return temp
    }
  }

  override fun doSave(saveSessions: List<SaveSession>, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, prevErrors: MutableList<Throwable>?): MutableList<Throwable>? {
    var errors = prevErrors
    beforeSave(readonlyFiles)

    super.doSave(saveSessions, readonlyFiles, errors)

    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(UnableToSaveProjectNotification::class.java, project)
    if (readonlyFiles.isEmpty()) {
      for (notification in notifications) {
        notification.expire()
      }
      return errors
    }

    if (!notifications.isEmpty()) {
      throw IComponentStore.SaveCancelledException()
    }

    val status: ReadonlyStatusHandler.OperationStatus
    val token = ReadAction.start()
    try {
      status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(*getFilesList(readonlyFiles))
    }
    finally {
      token.finish()
    }

    if (status.hasReadonlyFiles()) {
      dropUnableToSaveProjectNotification(project, status.readonlyFiles)
      throw IComponentStore.SaveCancelledException()
    }
    val oldList = ArrayList(readonlyFiles)
    readonlyFiles.clear()
    for (entry in oldList) {
      errors = ComponentStoreImpl.executeSave(entry.first, readonlyFiles, errors)
    }

    if (errors != null) {
      CompoundRuntimeException.throwIfNotEmpty(errors)
    }

    if (!readonlyFiles.isEmpty()) {
      dropUnableToSaveProjectNotification(project, getFilesList(readonlyFiles))
      throw IComponentStore.SaveCancelledException()
    }

    return errors
  }

  protected open fun beforeSave(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) {
  }

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): Array<out Storage> {
    // if we create project from default, component state written not to own storage file, but to project file,
    // we don't have time to fix it properly, so, ancient hack restored.
    val result = super.getStorageSpecs(component, stateSpec, operation)
    // don't add fake storage if project file storage already listed, otherwise data will be deleted on write (because of "deprecated")
    for (storage in result) {
      if (storage.file == StoragePathMacros.PROJECT_FILE) {
        return result
      }
    }
    return Array(result.size() + 1) { if (it == result.size()) DEFAULT_STORAGE_ANNOTATION else result[it] }
  }

  companion object {
    private val DEFAULT_STORAGE_ANNOTATION = DefaultStorageAnnotation()

    private fun dropUnableToSaveProjectNotification(project: Project, readOnlyFiles: Array<VirtualFile>) {
      val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(UnableToSaveProjectNotification::class.java, project)
      if (notifications.isEmpty()) {
        Notifications.Bus.notify(UnableToSaveProjectNotification(project, readOnlyFiles), project)
      }
      else {
        notifications[0].myFiles = readOnlyFiles
      }
    }

    private fun getFilesList(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) = Array(readonlyFiles.size()) { readonlyFiles.get(it).second }
  }

  override fun selectDefaultStorages(storages: Array<Storage>, operation: StateStorageOperation) = selectDefaultStorages(storages, operation, scheme)
}

internal fun selectDefaultStorages(storages: Array<Storage>, operation: StateStorageOperation, scheme: StorageScheme): Array<Storage> {
  if (operation === StateStorageOperation.READ) {
    val result = SmartList<Storage>()
    for (i in storages.indices.reversed()) {
      val storage = storages[i]
      if (storage.scheme == scheme) {
        result.add(storage)
      }
    }

    for (storage in storages) {
      if (storage.scheme == StorageScheme.DEFAULT && !result.contains(storage)) {
        result.add(storage)
      }
    }

    return result.toTypedArray()
  }
  else if (operation == StateStorageOperation.WRITE) {
    val result = SmartList<Storage>()
    for (storage in storages) {
      if (storage.scheme == scheme) {
        result.add(storage)
      }
    }

    if (result.isEmpty()) {
      for (storage in storages) {
        if (storage.scheme == StorageScheme.DEFAULT) {
          result.add(storage)
        }
      }
    }

    return result.toTypedArray()
  }
  else {
    return emptyArray()
  }
}

private class ProjectWithModulesStoreImpl(project: ProjectImpl, pathMacroManager: PathMacroManager) : ProjectStoreImpl(project, pathMacroManager) {
  override fun beforeSave(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) {
    super.beforeSave(readonlyFiles)

    for (module in (ModuleManager.getInstance(project)?.modules ?: Module.EMPTY_ARRAY)) {
      module.stateStore.save(readonlyFiles)
    }
  }
}

private class PlatformLangProjectStoreClassProvider : ProjectStoreClassProvider {
  override fun getProjectStoreClass(isDefaultProject: Boolean): Class<out IComponentStore> {
    return if (isDefaultProject) DefaultProjectStoreImpl::class.java else ProjectWithModulesStoreImpl::class.java
  }
}

private class PlatformProjectStoreClassProvider : ProjectStoreClassProvider {
  override fun getProjectStoreClass(isDefaultProject: Boolean): Class<out IComponentStore> {
    return if (isDefaultProject) DefaultProjectStoreImpl::class.java else ProjectStoreImpl::class.java
  }
}

private fun composeWsPath(filePath: String) = "${FileUtilRt.getNameWithoutExtension(filePath)}${WorkspaceFileType.DOT_DEFAULT_EXTENSION}"

private fun useOldWorkspaceContent(filePath: String, ws: File) {
  val oldWs = File(composeWsPath(filePath))
  if (!oldWs.exists()) {
    return
  }

  try {
    FileUtil.copyContent(oldWs, ws)
  }
  catch (e: IOException) {
    LOG.error(e)
  }
}