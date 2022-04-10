// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.psi.util.PsiFormatUtil.*
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.LightColors
import com.intellij.util.diff.Diff
import java.util.function.Function
import javax.swing.JComponent

class LibrarySourceNotificationProvider : EditorNotificationProvider {

  private companion object {

    private val LOG = logger<LibrarySourceNotificationProvider>()
    private val ANDROID_SDK_PATTERN = ".*/platforms/android-\\d+/android.jar!/.*".toRegex()

    private const val FIELD = SHOW_NAME or SHOW_TYPE or SHOW_FQ_CLASS_NAMES or SHOW_RAW_TYPE
    private const val METHOD = SHOW_NAME or SHOW_PARAMETERS or SHOW_RAW_TYPE
    private const val PARAMETER = SHOW_TYPE or SHOW_FQ_CLASS_NAMES or SHOW_RAW_TYPE
    private const val CLASS = SHOW_NAME or SHOW_FQ_CLASS_NAMES or SHOW_EXTENDS_IMPLEMENTS or SHOW_RAW_TYPE
  }

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?> {
    if (file.fileType is LanguageFileType && ProjectRootManager.getInstance(project).fileIndex.isInLibrarySource(file)) {
      val psiFile = PsiManager.getInstance(project).findFile(file)
      if (psiFile is PsiJavaFile) {
        val offender = psiFile.classes.find { differs(it) }
        if (offender != null) {
          val clsFile = offender.originalElement.containingFile?.virtualFile
          if (clsFile != null && !clsFile.path.matches(ANDROID_SDK_PATTERN)) {
            return Function {
              val panel = EditorNotificationPanel(LightColors.RED)
              panel.text = JavaUiBundle.message("library.source.mismatch", offender.name)
              panel.createActionLabel(JavaUiBundle.message("library.source.open.class")) {
                if (!project.isDisposed && clsFile.isValid) {
                  PsiNavigationSupport.getInstance().createNavigatable(project, clsFile, -1).navigate(true)
                }
              }
              panel.createActionLabel(JavaUiBundle.message("library.source.show.diff")) {
                if (!project.isDisposed && clsFile.isValid) {
                  val cf = DiffContentFactory.getInstance()
                  val request = SimpleDiffRequest(null, cf.create(project, clsFile), cf.create(project, file), clsFile.path, file.path)
                  DiffManager.getInstance().showDiff(project, request)
                }
              }
              logMembers(offender)
              return@Function panel
            }
          }
        }
      }
    }

    return EditorNotificationProvider.CONST_NULL
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

  private fun methods(c: PsiClass) = (if (c is PsiExtensibleClass) c.ownMethods else c.methods.asList()).filterNot(::ignoreMethod)

  private fun ignoreMethod(m: PsiMethod): Boolean {
    if (m.isConstructor) {
      return m.parameterList.parametersCount == 0 // default constructor
    }
    else {
      return m.name.contains("$\$bridge") // org.jboss.bridger.Bridger adds ACC_BRIDGE | ACC_SYNTHETIC to such methods
    }
  }

  private fun inners(c: PsiClass) = if (c is PsiExtensibleClass) c.ownInnerClasses else c.innerClasses.asList()

  private fun format(f: PsiField) = formatVariable(f, FIELD, PsiSubstitutor.EMPTY)

  private fun format(m: PsiMethod) = formatMethod(m, PsiSubstitutor.EMPTY, METHOD, PARAMETER)

  private fun format(c: PsiClass) = formatClass(c, CLASS).removeSuffix(" extends java.lang.Object")

  private fun logMembers(offender: PsiClass) {
    if (!LOG.isTraceEnabled) {
      return
    }
    val cls = offender.originalElement as? PsiClass ?: return
    val sourceMembers = formatMembers(offender)
    val clsMembers = formatMembers(cls)
    val diff = Diff.linesDiff(sourceMembers.toTypedArray(), clsMembers.toTypedArray()) ?: return
    LOG.trace("Class: ${cls.qualifiedName}\n$diff")
  }

  private fun formatMembers(c: PsiClass): List<String> {
    return fields(c).map(::format).sorted() +
           methods(c).map(::format).sorted() +
           inners(c).map(::format).sorted()
  }
}
