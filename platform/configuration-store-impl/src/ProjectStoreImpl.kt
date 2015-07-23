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
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl.UnableToSaveProjectNotification
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

open class ProjectStoreImpl(protected var myProject: ProjectImpl, pathMacroManager: PathMacroManager) : BaseFileConfigurableStoreImpl(pathMacroManager), IProjectStore {
  // protected setter used in upsource
  private var scheme = StorageScheme.DEFAULT
    protected set

  private var presentableUrl: String? = null

  override fun getSubstitutors(): Array<TrackingPathMacroSubstitutor> {
    val substitutor = getStateStorageManager().getMacroSubstitutor()
    return if (substitutor == null) emptyArray() else arrayOf(substitutor)
  }

  override fun optimizeTestLoading() = myProject.isOptimiseTestLoadSpeed()

  override fun getProject() = myProject

  override fun setProjectFilePath(filePath: String) {
    val stateStorageManager = getStateStorageManager()
    val fs = LocalFileSystem.getInstance()

    val file = File(filePath)
    if (isIprPath(file)) {
      scheme = StorageScheme.DEFAULT

      stateStorageManager.addMacro(StoragePathMacros.PROJECT_FILE, filePath)

      val workspacePath = composeWsPath(filePath)
      stateStorageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspacePath)

      ApplicationManager.getApplication().invokeAndWait(object : Runnable {
        override fun run() {
          VfsUtil.markDirtyAndRefresh(false, true, false, fs.refreshAndFindFileByPath(filePath), fs.refreshAndFindFileByPath(workspacePath))
        }
      }, ModalityState.defaultModalityState())
    }
    else {
      scheme = StorageScheme.DIRECTORY_BASED

      val dirStore = File(if (file.isDirectory()) file else file.getParentFile(), Project.DIRECTORY_STORE_FOLDER)
      stateStorageManager.addMacro(StoragePathMacros.PROJECT_FILE, File(dirStore, "misc.xml").getPath())
      stateStorageManager.addMacro(StoragePathMacros.PROJECT_CONFIG_DIR, dirStore.getPath())

      val workspace = File(dirStore, "workspace.xml")
      stateStorageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, workspace.getPath())
      if (!workspace.exists() && !file.isDirectory()) {
        useOldWorkspaceContent(filePath, workspace)
      }

      ApplicationManager.getApplication().invokeAndWait(object : Runnable {
        override fun run() {
          VfsUtil.markDirtyAndRefresh(false, true, true, fs.refreshAndFindFileByIoFile(dirStore))
        }
      }, ModalityState.defaultModalityState())
    }

    presentableUrl = null
  }

  override fun getProjectBaseDir(): VirtualFile? {
    if (myProject.isDefault()) {
      return null
    }
    val path = getProjectBasePath() ?: return null
    return LocalFileSystem.getInstance().findFileByPath(path)
  }

  override fun getProjectBasePath(): String? {
    if (myProject.isDefault()) {
      return null
    }

    val path = getProjectFilePath()
    if (!StringUtil.isEmptyOrSpaces(path)) {
      return getBasePath(File(path))
    }

    //we are not yet initialized completely ("open directory", etc)
    val storage = getStateStorageManager().getStateStorage(StoragePathMacros.PROJECT_FILE, RoamingType.PER_USER)
    return if (storage is FileBasedStorage) getBasePath(storage.getFile()) else null
  }

  private fun getBasePath(file: File) = if (scheme == StorageScheme.DEFAULT) file.getParent() else file.getParentFile()?.getParent()

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
      var temp = PathUtilRt.getFileName((getProjectFileStorage() as FileBasedStorage).getFilePath())
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
    if (myProject.isDefault()) {
      return null
    }
    if (presentableUrl == null) {
      val url = if (scheme == StorageScheme.DIRECTORY_BASED) getProjectBasePath() else getProjectFilePath()
      if (url != null) {
        presentableUrl = FileUtil.toSystemDependentName(url)
      }
    }
    return presentableUrl
  }

  override fun getProjectFile() = if (myProject.isDefault()) null else (getProjectFileStorage() as FileBasedStorage).getVirtualFile()

  override fun getProjectFilePath() = if (myProject.isDefault()) "" else (getProjectFileStorage() as FileBasedStorage).getFilePath()

  // XmlElementStorage if default project, otherwise FileBasedStorage
  private fun getProjectFileStorage() = getStateStorageManager().getStateStorage(StoragePathMacros.PROJECT_FILE, RoamingType.PER_USER) as XmlElementStorage

  override fun getWorkspaceFile(): VirtualFile? {
    if (myProject.isDefault()) return null
    val storage = getStateStorageManager().getStateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED) as FileBasedStorage?
    assert(storage != null)
    return storage!!.getVirtualFile()
  }

  override fun getWorkspaceFilePath(): String? {
    if (myProject.isDefault()) {
      return null
    }
    return (getStateStorageManager().getStateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED) as FileBasedStorage?)!!.getFilePath()
  }

  override fun loadProjectFromTemplate(defaultProject: ProjectImpl) {
    defaultProject.save()

    val element = (defaultProject.getStateStore() as DefaultProjectStoreImpl).getStateCopy()
    if (element != null) {
      getProjectFileStorage().setDefaultState(element)
    }
  }

  override fun createStorageManager(): StateStorageManager = ProjectStateStorageManager(pathMacroManager.createTrackingSubstitutor(), myProject)

  override fun doSave(saveSessions: List<SaveSession>?, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, prevErrors: MutableList<Throwable>?): MutableList<Throwable>? {
    var errors = prevErrors
    beforeSave(readonlyFiles)

    super<BaseFileConfigurableStoreImpl>.doSave(saveSessions, readonlyFiles, errors)

    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(javaClass<UnableToSaveProjectNotification>(), myProject)
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
      status = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(*getFilesList(readonlyFiles))
    }
    finally {
      token.finish()
    }

    if (status.hasReadonlyFiles()) {
      dropUnableToSaveProjectNotification(myProject, status.getReadonlyFiles())
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
      dropUnableToSaveProjectNotification(myProject, getFilesList(readonlyFiles))
      throw IComponentStore.SaveCancelledException()
    }

    return errors
  }

  protected open fun beforeSave(readonlyFiles: List<Pair<SaveSession, VirtualFile>>) {
  }

  private var _defaultStorageChooser: StateStorageChooser<PersistentStateComponent<*>>? = null

  override val defaultStorageChooser: StateStorageChooser<PersistentStateComponent<*>>?
    get() {
      if (_defaultStorageChooser == null) {
        _defaultStorageChooser = DefaultStorageChooser(scheme)
      }
      return _defaultStorageChooser
    }

  override fun getMessageBus() = myProject.getMessageBus()

  override fun <T> getComponentStorageSpecs(component: PersistentStateComponent<T>, stateSpec: State, operation: StateStorageOperation): Array<Storage> {
    // if we create project from default, component state written not to own storage file, but to project file,
    // we don't have time to fix it properly, so, ancient hack restored.
    val result = super<BaseFileConfigurableStoreImpl>.getComponentStorageSpecs(component, stateSpec, operation)
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

    private fun isIprPath(file: File) = FileUtilRt.extensionEquals(file.getName(), ProjectFileType.DEFAULT_EXTENSION)

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
}

class DefaultStorageChooser(private val scheme: StorageScheme) : StateStorageChooser<PersistentStateComponent<*>> {
  override fun selectStorages(storages: Array<Storage>, component: PersistentStateComponent<*>, operation: StateStorageOperation): Array<Storage> {
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
}