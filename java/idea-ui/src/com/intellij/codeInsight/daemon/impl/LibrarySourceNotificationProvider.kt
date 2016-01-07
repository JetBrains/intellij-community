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
package com.intellij.codeInsight.daemon.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ModuleRootAdapter
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors
import java.awt.Color

class LibrarySourceNotificationProvider(private val project: Project, notifications: EditorNotifications) :
    EditorNotifications.Provider<EditorNotificationPanel>() {

  private companion object {
    private val KEY = Key.create<EditorNotificationPanel>("library.source.mismatch.panel")
  }

  init {
    project.messageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootAdapter() {
      override fun rootsChanged(event: ModuleRootEvent) = notifications.updateAllNotifications()
    })
  }

  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel? {
    if (file.fileType is LanguageFileType && ProjectRootManager.getInstance(project).fileIndex.isInLibrarySource(file)) {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile is PsiJavaFile) {
        val offender = psiFile.classes.find { differs(it) }
        if (offender != null) {
          val panel = ColoredNotificationPanel(LightColors.RED)
          panel.setText(ProjectBundle.message("library.source.mismatch", offender.name))
          panel.createActionLabel(ProjectBundle.message("library.source.open.class"), {
            val classFile = offender.originalElement.containingFile?.virtualFile
            if (classFile != null) {
              OpenFileDescriptor(project, classFile, -1).navigate(true)
            }
          })
          return panel
        }
      }
    }

    return null
  }

  private fun differs(clazz: PsiClass): Boolean {
    val binary = clazz.originalElement
    return binary !== clazz &&
        binary is PsiClass &&
        (differs(fields(clazz), fields(binary)) || differs(methods(clazz), methods(binary)) || differs(inners(clazz), inners(binary)))
  }

  private fun differs(list1: List<PsiMember>, list2: List<PsiMember>): Boolean =
      list1.size != list2.size || list1.map { it.name ?: "" }.sorted() != list2.map { it.name ?: "" }.sorted()

  private fun fields(clazz: PsiClass) = if (clazz is PsiExtensibleClass) clazz.ownFields else clazz.fields.asList()
  private fun methods(clazz: PsiClass): List<PsiMethod> =
      (if (clazz is PsiExtensibleClass) clazz.ownMethods else clazz.methods.asList())
          .filter { !(it.isConstructor && it.parameterList.parametersCount == 0) }
  private fun inners(clazz: PsiClass) = if (clazz is PsiExtensibleClass) clazz.ownInnerClasses else clazz.innerClasses.asList()
}

private class ColoredNotificationPanel(private val color: Color?) : EditorNotificationPanel() {
  override fun getBackground(): Color? = color ?: super.getBackground()
}