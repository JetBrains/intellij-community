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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeed
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl.UnableToSaveProjectNotification
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.*
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

open class ProjectStoreImpl(override val project: ProjectImpl, private val pathMacroManager: PathMacroManager) : ComponentStoreImpl(), IProjectStore {
  // protected setter used in upsource
  // Zelix KlassMaster - ERROR: Could not find method 'getScheme()'
  var scheme = StorageScheme.DEFAULT

  private var presentableUrl: String? = null

  init {
    assert(!project.isDefault())
  }

  override fun getSubstitutors() = listOf(storageManager.getMacroSubstitutor())

  override fun optimizeTestLoading() = project.isOptimiseTestLoadSpeed()

  override final fun getPathMacroManagerForDefaults() = pathMacroManager

  override val storageManager = ProjectStateStorageManager(pathMacroManager.createTrackingSubstitutor(), project)

  override fun setPath(filePath: String) {
    val storageManager = storageManager
    val fs = LocalFileSystem.getInstance()
    if (FileUtilRt.extensionEquals(filePath, ProjectFileType.DEFAULT_EXTENSION)) {
      scheme = StorageScheme.DEFAULT

      storageManager.addMacro(StoragePathMacros.PROJECT_FILE, filePath)

      val workspacePath = composeWsPath(filePath)
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspacePath)

      invokeAndWaitIfNeed {
        VfsUtil.markDirtyAndRefresh(false, true, false, fs.refreshAndFindFileByPath(filePath), fs.refreshAndFindFileByPath(workspacePath))
      }
    }
    else {
      scheme = StorageScheme.DIRECTORY_BASED

      val file = File(filePath)
      val dirStore = File(if (file.isDirectory()) file else file.getParentFile(), Project.DIRECTORY_STORE_FOLDER)
      val projectConfigDir = dirStore.systemIndependentPath
      storageManager.addMacro(StoragePathMacros.PROJECT_FILE, "$projectConfigDir/misc.xml")
      storageManager.addMacro(StoragePathMacros.PROJECT_CONFIG_DIR, projectConfigDir)

      val workspace = File(dirStore, "workspace.xml")
      storageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspace.systemIndependentPath)
      if (!workspace.exists() && !file.isDirectory()) {
        useOldWorkspaceContent(filePath, workspace)
      }

      invokeAndWaitIfNeed { VfsUtil.markDirtyAndRefresh(false, true, true, fs.refreshAndFindFileByPath(projectConfigDir)) }
    }

    presentableUrl = null
  }

  override fun clearStorages() {
    storageManager.clearStorages()
  }

  override fun getProjectBaseDir(): VirtualFile? {
    val path = getProjectBasePath() ?: return null
    return LocalFileSystem.getInstance().findFileByPath(path)
  }

  override fun getProjectBasePath(): String? {
    val path = PathUtilRt.getParentPath(getProjectFilePath())
    return if (scheme == StorageScheme.DEFAULT) path else PathUtilRt.getParentPath(path)
  }

  override fun getProjectName(): String {
    if (scheme == StorageScheme.DIRECTORY_BASED) {
      val baseDir = getProjectBaseDir()
      assert(baseDir != null) { "scheme=$scheme project file=${getProjectFilePath()}" }

      val ideaDir = baseDir!!.findChild(Project.DIRECTORY_STORE_FOLDER)
      if (ideaDir != null && ideaDir.isValid()) {
        val nameFile = ideaDir.findChild(ProjectImpl.NAME_FILE)
        if (nameFile != null && nameFile.isValid()) {
          try {
            val `in` = BufferedReader(InputStreamReader(nameFile.getInputStream(), CharsetToolkit.UTF8_CHARSET))
            try {
              val name = `in`.readLine()
              if (name != null && !name.isEmpty()) {
                return name.trim()
              }
            }
            finally {
              `in`.close()
            }
          }
          catch (ignored: IOException) {
          }

        }
      }

      return baseDir.getName().replace(":", "")
    }
    else {
      var temp = PathUtilRt.getFileName(getProjectFilePath())
      val fileType = FileTypeManager.getInstance().getFileTypeByFileName(temp)
      if (fileType is ProjectFileType) {
        temp = temp.substring(0, temp.length() - fileType.getDefaultExtension().length() - 1)
      }
      val i = temp.lastIndexOf(File.separatorChar)
      if (i >= 0) {
        temp = temp.substring(i + 1, temp.length() - i + 1)
      }
      return temp
    }
  }

  override fun getStorageScheme() = scheme

  override fun getPresentableUrl(): String? {
    if (presentableUrl == null) {
      val url = if (scheme == StorageScheme.DIRECTORY_BASED) getProjectBasePath() else getProjectFilePath()
      if (url != null) {
        presentableUrl = FileUtil.toSystemDependentName(url)
      }
    }
    return presentableUrl
  }

  override fun getProjectFile() = getProjectFileStorage().getVirtualFile()

  override fun getProjectFilePath() = storageManager.expandMacro(StoragePathMacros.PROJECT_FILE)

  private fun getProjectFileStorage() = storageManager.getOrCreateStorage(StoragePathMacros.PROJECT_FILE) as FileBasedStorage

  override fun getWorkspaceFile() = (storageManager.getOrCreateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED) as FileBasedStorage?)?.getVirtualFile()

  override fun getWorkspaceFilePath() = storageManager.expandMacro(StoragePathMacros.WORKSPACE_FILE)

  override fun loadProjectFromTemplate(defaultProject: Project) {
    defaultProject.save()

    val element = (defaultProject.stateStore as DefaultProjectStoreImpl).getStateCopy()
    if (element != null) {
      getProjectFileStorage().setDefaultState(element)
    }
  }

  override fun doSave(saveSessions: List<SaveSession>?, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, prevErrors: MutableList<Throwable>?): MutableList<Throwable>? {
    var errors = prevErrors
    beforeSave(readonlyFiles)

    super<ComponentStoreImpl>.doSave(saveSessions, readonlyFiles, errors)

    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(javaClass<UnableToSaveProjectNotification>(), project)
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
      dropUnableToSaveProjectNotification(project, status.getReadonlyFiles())
      throw IComponentStore.SaveCancelledException()
    }
    val oldList = ArrayList(readonlyFiles)
    readonlyFiles.clear()
    for (entry in oldList) {
      errors = ComponentStoreImpl.executeSave(entry.first, readonlyFiles, errors)
    }

    if (errors != null) {
      CompoundRuntimeException.doThrow(errors)
    }

    if (!readonlyFiles.isEmpty()) {
      dropUnableToSaveProjectNotification(project, getFilesList(readonlyFiles))
      throw IComponentStore.SaveCancelledException()
    }

    return errors
  }

  protected open fun beforeSave(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) {
  }

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): Array<Storage> {
    // if we create project from default, component state written not to own storage file, but to project file,
    // we don't have time to fix it properly, so, ancient hack restored.
    val result = super<ComponentStoreImpl>.getStorageSpecs(component, stateSpec, operation)
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

    private fun composeWsPath(filePath: String): String {
      val lastDot = filePath.lastIndexOf('.')
      val filePathWithoutExt = if (lastDot > 0) filePath.substring(0, lastDot) else filePath
      return "$filePathWithoutExt${WorkspaceFileType.DOT_DEFAULT_EXTENSION}"
    }

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

    private fun dropUnableToSaveProjectNotification(project: Project, readOnlyFiles: Array<VirtualFile>) {
      val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(javaClass<UnableToSaveProjectNotification>(), project)
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

fun selectDefaultStorages(storages: Array<Storage>, operation: StateStorageOperation, scheme: StorageScheme): Array<Storage> {
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