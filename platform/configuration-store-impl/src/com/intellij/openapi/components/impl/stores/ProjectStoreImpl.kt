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

import com.intellij.CommonBundle
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.highlighter.WorkspaceFileType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.components.StateStorage.SaveSession
import com.intellij.openapi.components.impl.stores.*
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.project.impl.ProjectManagerImpl.UnableToSaveProjectNotification
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.util.PathUtilRt
import com.intellij.util.SmartList
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.messages.MessageBus
import org.jdom.Element
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.ArrayList

public open class ProjectStoreImpl(protected var myProject: ProjectImpl, pathMacroManager: PathMacroManager) : BaseFileConfigurableStoreImpl(pathMacroManager), IProjectStore {
  private var myScheme = StorageScheme.DEFAULT
  private var myPresentableUrl: String? = null

  SuppressWarnings("unused") //used in upsource
  protected fun setStorageScheme(scheme: StorageScheme) {
    myScheme = scheme
  }

  override fun checkVersion(): Boolean {
    if (originalVersion >= 0 && originalVersion < ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
      val projectFile = getProjectFile()
      LOG.assertTrue(projectFile != null)
      val message = ProjectBundle.message("project.convert.old.prompt", projectFile!!.getName(), ApplicationNamesInfo.getInstance().getProductName(), projectFile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + projectFile.getExtension())
      if (Messages.showYesNoDialog(message, CommonBundle.getWarningTitle(), Messages.getWarningIcon()) != Messages.YES) return false

//      val conversionProblems = BaseFileConfigurableStoreImpl.conversionProblemsStorage
//      if (!ContainerUtil.isEmpty(conversionProblems)) {
//        val buffer = StringBuilder()
//        buffer.append(ProjectBundle.message("project.convert.problems.detected"))
//        for (s in conversionProblems) {
//          buffer.append('\n')
//          buffer.append(s)
//        }
//        buffer.append(ProjectBundle.message("project.convert.problems.help"))
//        if (Messages.showOkCancelDialog(myProject, buffer.toString(), ProjectBundle.message("project.convert.problems.title"), ProjectBundle.message("project.convert.problems.help.button"), CommonBundle.getCloseButtonText(), Messages.getWarningIcon()) == Messages.OK) {
//          HelpManager.getInstance().invokeHelp("project.migrationProblems")
//        }
//      }

      ApplicationManager.getApplication().runWriteAction(object : Runnable {
        override fun run() {
          try {
            val projectDir = projectFile.getParent()
            assert(projectDir != null)

            backup(projectDir, projectFile)

            val workspaceFile = getWorkspaceFile()
            if (workspaceFile != null) {
              backup(projectDir, workspaceFile)
            }
          }
          catch (e: IOException) {
            LOG.error(e)
          }

        }

        throws(IOException::class)
        private fun backup(projectDir: VirtualFile, vile: VirtualFile) {
          val oldName = vile.getNameWithoutExtension() + OLD_PROJECT_SUFFIX + vile.getExtension()
          VfsUtil.saveText(projectDir.findOrCreateChildData(this, oldName), VfsUtilCore.loadText(vile))
        }
      })
    }

    return originalVersion <= ProjectManagerImpl.CURRENT_FORMAT_VERSION || MessageDialogBuilder.yesNo(CommonBundle.getWarningTitle(), ProjectBundle.message("project.load.new.version.warning", myProject.getName(), ApplicationNamesInfo.getInstance().getProductName())).icon(Messages.getWarningIcon()).project(myProject).`is`()
  }

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
      myScheme = StorageScheme.DEFAULT

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
      myScheme = StorageScheme.DIRECTORY_BASED

      val dirStore = if (file.isDirectory())
        File(file, Project.DIRECTORY_STORE_FOLDER)
      else
        File(file.getParentFile(), Project.DIRECTORY_STORE_FOLDER)
      stateStorageManager.addMacro(StoragePathMacros.PROJECT_FILE, File(dirStore, "misc.xml").getPath())

      val ws = File(dirStore, "workspace.xml")
      stateStorageManager.addMacro(StoragePathMacros.WORKSPACE_FILE, ws.getPath())
      if (!ws.exists() && !file.isDirectory()) {
        useOldWsContent(filePath, ws)
      }

      stateStorageManager.addMacro(StoragePathMacros.PROJECT_CONFIG_DIR, dirStore.getPath())

      ApplicationManager.getApplication().invokeAndWait(object : Runnable {
        override fun run() {
          VfsUtil.markDirtyAndRefresh(false, true, true, fs.refreshAndFindFileByIoFile(dirStore))
        }
      }, ModalityState.defaultModalityState())
    }

    myPresentableUrl = null
  }

  override fun getProjectBaseDir(): VirtualFile? {
    if (myProject.isDefault()) return null

    val path = getProjectBasePath() ?: return null

    return LocalFileSystem.getInstance().findFileByPath(path)
  }

  override fun getProjectBasePath(): String? {
    if (myProject.isDefault()) return null

    val path = getProjectFilePath()
    if (!StringUtil.isEmptyOrSpaces(path)) {
      return getBasePath(File(path))
    }

    //we are not yet initialized completely ("open directory", etc)
    val storage = getStateStorageManager().getStateStorage(StoragePathMacros.PROJECT_FILE, RoamingType.PER_USER)
    if (storage !is FileBasedStorage) {
      return null
    }

    return getBasePath(storage.getFile())
  }

  private fun getBasePath(file: File): String? {
    if (myScheme === StorageScheme.DEFAULT) {
      return file.getParent()
    }
    else {
      val parentFile = file.getParentFile()
      return parentFile?.getParent()
    }
  }

  override fun getProjectName(): String {
    if (myScheme === StorageScheme.DIRECTORY_BASED) {
      val baseDir = getProjectBaseDir()
      assert(baseDir != null) { "scheme=" + myScheme + " project file=" + getProjectFilePath() }

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

  override fun getStorageScheme(): StorageScheme {
    return myScheme
  }

  override fun getPresentableUrl(): String? {
    if (myProject.isDefault()) {
      return null
    }
    if (myPresentableUrl == null) {
      val url = if (myScheme === StorageScheme.DIRECTORY_BASED) getProjectBasePath() else getProjectFilePath()
      if (url != null) {
        myPresentableUrl = FileUtil.toSystemDependentName(url)
      }
    }
    return myPresentableUrl
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
    if (myProject.isDefault()) return null
    val storage = getStateStorageManager().getStateStorage(StoragePathMacros.WORKSPACE_FILE, RoamingType.DISABLED) as FileBasedStorage?
    assert(storage != null)
    return storage!!.getFilePath()
  }

  override fun loadProjectFromTemplate(defaultProject: ProjectImpl) {
    defaultProject.save()

    val element = (defaultProject.getStateStore() as DefaultProjectStoreImpl).getStateCopy()
    if (element != null) {
      getProjectFileStorage().setDefaultState(element)
    }
  }

  override fun createStateStorageManager(): StateStorageManager {
    return ProjectStateStorageManager(myPathMacroManager.createTrackingSubstitutor(), myProject)
  }

  open class ProjectStorageData : BaseFileConfigurableStoreImpl.BaseStorageData {
    protected val myProject: Project

    constructor(rootElementName: String, project: Project) : super(rootElementName) {
      myProject = project
    }

    protected constructor(storageData: ProjectStorageData) : super(storageData) {
      myProject = storageData.myProject
    }

    override fun clone(): StorageData {
      return ProjectStorageData(this)
    }
  }

  class WsStorageData : ProjectStorageData {
    constructor(rootElementName: String, project: Project) : super(rootElementName, project) {
    }

    private constructor(storageData: WsStorageData) : super(storageData) {
    }

    override fun clone(): StorageData {
      return WsStorageData(this)
    }
  }

  class IprStorageData : ProjectStorageData {
    constructor(rootElementName: String, project: Project) : super(rootElementName, project) {
    }

    constructor(storageData: IprStorageData) : super(storageData) {
    }

    override fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?, intern: Boolean) {
      val v = rootElement.getAttributeValue(VERSION_OPTION)
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      originalVersion = if (v != null) Integer.parseInt(v) else 0

      if (originalVersion != ProjectManagerImpl.CURRENT_FORMAT_VERSION) {
        convert(rootElement, originalVersion)
      }

      super.load(rootElement, pathMacroSubstitutor, intern)
    }

    @suppress("UNUSED_PARAMETER")
    protected fun convert(root: Element, originalVersion: Int) {
    }

    override fun clone(): StorageData {
      return IprStorageData(this)
    }
  }

  override fun doSave(saveSessions: List<SaveSession>?, readonlyFiles: MutableList<Pair<SaveSession, VirtualFile>>, prevErrors: MutableList<Throwable>?): MutableList<Throwable>? {
    var errors = prevErrors
    beforeSave(readonlyFiles)

    super<BaseFileConfigurableStoreImpl>.doSave(saveSessions, readonlyFiles, errors)

    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(javaClass<UnableToSaveProjectNotification>(), myProject)
    if (readonlyFiles.isEmpty()) {
      if (notifications.size() > 0) {
        for (notification in notifications) {
          notification.expire()
        }
      }
      return errors
    }

    if (notifications.size() > 0) {
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

  private val myStateStorageChooser = object : StateStorageChooser<PersistentStateComponent<*>> {
    override fun selectStorages(storages: Array<Storage>, component: PersistentStateComponent<*>, operation: StateStorageOperation): Array<Storage> {
      if (operation === StateStorageOperation.READ) {
        val result = SmartList<Storage>()
        for (i in storages.indices.reversed()) {
          val storage = storages[i]
          if (storage.scheme === myScheme) {
            result.add(storage)
          }
        }

        for (storage in storages) {
          if (storage.scheme === StorageScheme.DEFAULT && !result.contains(storage)) {
            result.add(storage)
          }
        }

        return result.toArray<Storage>(arrayOfNulls<Storage>(result.size()))
      }
      else if (operation === StateStorageOperation.WRITE) {
        val result = SmartList<Storage>()
        for (storage in storages) {
          if (storage.scheme === myScheme) {
            result.add(storage)
          }
        }

        if (!result.isEmpty()) {
          return result.toArray<Storage>(arrayOfNulls<Storage>(result.size()))
        }

        for (storage in storages) {
          if (storage.scheme === StorageScheme.DEFAULT) {
            result.add(storage)
          }
        }

        return result.toArray<Storage>(arrayOfNulls<Storage>(result.size()))
      }
      else {
        return arrayOf()
      }
    }
  }

  override fun getDefaultStateStorageChooser(): StateStorageChooser<PersistentStateComponent<*>>? {
    return myStateStorageChooser
  }

  override fun getMessageBus(): MessageBus {
    return myProject.getMessageBus()
  }

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

    private val OLD_PROJECT_SUFFIX = "_old."

    private var originalVersion = -1

    private fun isIprPath(file: File) = FileUtilRt.extensionEquals(file.getName(), ProjectFileType.DEFAULT_EXTENSION)

    private fun composeWsPath(filePath: String): String {
      val lastDot = filePath.lastIndexOf('.')
      val filePathWithoutExt = if (lastDot > 0) filePath.substring(0, lastDot) else filePath
      return "$filePathWithoutExt${WorkspaceFileType.DOT_DEFAULT_EXTENSION}"
    }

    private fun useOldWsContent(filePath: String, ws: File) {
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
