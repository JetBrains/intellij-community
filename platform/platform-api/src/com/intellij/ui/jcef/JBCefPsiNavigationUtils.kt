// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.NonNls
import java.nio.file.Paths

object JBCefPsiNavigationUtils {
  @NonNls
  private val PSI_ELEMENT_COORDINATES = "${JBCefSourceSchemeHandlerFactory.SOURCE_SCHEME}://(.+):(\\d+)".toRegex()
  private const val FILE_PATH_GROUP = 1
  private const val OFFSET_GROUP = 2

  fun navigateTo(requestLink: String): Boolean {
    val (filePath, offset) = parsePsiElementCoordinates(requestLink) ?: return false
    val dataContext = DataManager.getInstance().dataContext
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    val virtualFile = ProjectRootManager.getInstance(project)
                        .contentRoots.asSequence()
                        .map { Paths.get(it.path, filePath) }
                        .mapNotNull(VirtualFileManager.getInstance()::findFileByNioPath)
                        .firstOrNull() ?: return false

    ApplicationManager.getApplication().invokeLater {
      FileEditorManager.getInstance(project)
        .openEditor(OpenFileDescriptor(project, virtualFile, offset), true)
    }
    return true
  }

  private fun parsePsiElementCoordinates(rawCoordinates: String): Coordinates? {
    val groups = PSI_ELEMENT_COORDINATES.matchEntire(rawCoordinates)?.groups ?: return null
    val filePath = groups[FILE_PATH_GROUP]?.value ?: return null
    val offset = groups[OFFSET_GROUP]?.value?.toInt() ?: return null
    return Coordinates(filePath, offset)
  }

  private data class Coordinates(val filePath: String, val offset: Int)
}