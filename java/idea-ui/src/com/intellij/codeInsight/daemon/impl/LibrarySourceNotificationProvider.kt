// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ProjectTopics
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.LightColors

class LibrarySourceNotificationProvider(private val project: Project, notifications: EditorNotifications) :
    EditorNotifications.Provider<EditorNotificationPanel>() {

  private companion object {
    private val KEY = Key.create<EditorNotificationPanel>("library.source.mismatch.panel")
    private val ANDROID_SDK_PATTERN = ".*/platforms/android-\\d+/android.jar!/.*".toRegex()

    private const val FIELD = PsiFormatUtil.SHOW_NAME or PsiFormatUtil.SHOW_TYPE or PsiFormatUtil.SHOW_FQ_CLASS_NAMES
    private const val METHOD = PsiFormatUtil.SHOW_NAME or PsiFormatUtilBase.SHOW_PARAMETERS
    private const val PARAMETER = PsiFormatUtilBase.SHOW_TYPE or PsiFormatUtil.SHOW_FQ_CLASS_NAMES
    private const val CLASS = PsiFormatUtil.SHOW_NAME or PsiFormatUtil.SHOW_FQ_CLASS_NAMES or PsiFormatUtil.SHOW_EXTENDS_IMPLEMENTS
  }

  init {
    project.messageBus.connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
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
          val clsFile = offender.originalElement.containingFile?.virtualFile
          if (clsFile != null && !clsFile.path.matches(ANDROID_SDK_PATTERN)) {
            val panel = EditorNotificationPanel(LightColors.RED)
            panel.setText(ProjectBundle.message("library.source.mismatch", offender.name))
            panel.createActionLabel(ProjectBundle.message("library.source.open.class")) {
              if (!project.isDisposed && clsFile.isValid) {
                PsiNavigationSupport.getInstance().createNavigatable(project, clsFile, -1).navigate(true)
              }
            }
            panel.createActionLabel(ProjectBundle.message("library.source.show.diff")) {
              if (!project.isDisposed && clsFile.isValid) {
                val cf = DiffContentFactory.getInstance()
                val request = SimpleDiffRequest(null, cf.create(project, clsFile), cf.create(project, file), clsFile.path, file.path)
                DiffManager.getInstance().showDiff(project, request)
              }
            }
            return panel
          }
        }
      }
    }

    return null
  }

  private fun differs(src: PsiClass): Boolean {
    val cls = src.originalElement
    return cls !== src && cls is PsiClass &&
           (differs(fields(src), fields(cls), ::format) ||
            differs(methods(src), methods(cls), ::format) ||
            differs(inners(src), inners(cls), ::format))
  }

  private fun <T : PsiMember> differs(srcMembers: List<T>, clsMembers: List<T>, format: (T) -> String) =
    srcMembers.size != clsMembers.size || srcMembers.map(format).sorted() != clsMembers.map(format).sorted()

  private fun fields(c: PsiClass) = if (c is PsiExtensibleClass) c.ownFields else c.fields.asList()
  private fun methods(c: PsiClass) = (if (c is PsiExtensibleClass) c.ownMethods else c.methods.asList()).filter { !defaultInit(it) }
  private fun defaultInit(it: PsiMethod) = it.isConstructor && it.parameterList.parametersCount == 0
  private fun inners(c: PsiClass) = if (c is PsiExtensibleClass) c.ownInnerClasses else c.innerClasses.asList()

  private fun format(f: PsiField) = PsiFormatUtil.formatVariable(f, FIELD, PsiSubstitutor.EMPTY)
  private fun format(m: PsiMethod) = PsiFormatUtil.formatMethod(m, PsiSubstitutor.EMPTY, METHOD, PARAMETER)
  private fun format(c: PsiClass) = PsiFormatUtil.formatClass(c, CLASS)
}