/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.project

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePathWrapper
import javax.swing.JComponent

object ProjectUtil {
  @JvmOverloads
  @JvmStatic
  fun calcRelativeToProjectPath(file: VirtualFile, project: Project?, includeFilePath: Boolean, includeUniqueFilePath: Boolean = false, keepModuleAlwaysOnTheLeft: Boolean = false): String {
    if (file is VirtualFilePathWrapper && file.enforcePresentableName()) {
      return if (includeFilePath) file.presentablePath else file.name
    }
    val url: String
    if (includeFilePath) {
      url = file.presentableUrl
    }
    else if (includeUniqueFilePath) {
      url = UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file)
    }
    else {
      url = file.name
    }
    if (project == null) {
      return url
    }
    return ProjectUtilCore.displayUrlRelativeToProject(file, url, project, includeFilePath, keepModuleAlwaysOnTheLeft)
  }

  @JvmStatic
  fun calcRelativeToProjectPath(file: VirtualFile, project: Project): String {
    return calcRelativeToProjectPath(file, project, true)
  }

  @JvmStatic
  fun guessProjectForFile(file: VirtualFile): Project? {
    return ProjectLocator.getInstance().guessProjectForFile(file)
  }

  /***
   * guessProjectForFile works incorrectly - even if file is config (idea config file) first opened project will be returned
   */
  @JvmOverloads
  @JvmStatic
  fun guessProjectForContentFile(file: VirtualFile, fileType: FileType = file.fileType): Project? {
    if (ProjectCoreUtil.isProjectOrWorkspaceFile(file, fileType)) {
      return null
    }

    for (project in ProjectManager.getInstance().openProjects) {
      if (!project.isDefault && project.isInitialized && !project.isDisposed && ProjectRootManager.getInstance(project).fileIndex.isInContent(file)) {
        return project
      }
    }

    return null
  }

  @JvmStatic
  fun isProjectOrWorkspaceFile(file: VirtualFile): Boolean {
    // do not use file.getFileType() to avoid autodetection by content loading for arbitrary files
    return ProjectCoreUtil.isProjectOrWorkspaceFile(file, FileTypeManager.getInstance().getFileTypeByFileName(file.name))
  }

  @JvmStatic
  fun guessCurrentProject(component: JComponent?): Project {
    var project: Project? = null
    if (component != null) {
      project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(component))
    }
    if (project == null) {
      val openProjects = ProjectManager.getInstance().openProjects
      if (openProjects.size > 0) project = openProjects[0]
      if (project == null) {
        val dataContext = DataManager.getInstance().dataContext
        project = CommonDataKeys.PROJECT.getData(dataContext)
      }
      if (project == null) {
        project = ProjectManager.getInstance().defaultProject
      }
    }
    return project
  }
}
