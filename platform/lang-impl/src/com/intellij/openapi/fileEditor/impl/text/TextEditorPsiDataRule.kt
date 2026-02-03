// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.multiverse.EditorContextManager.Companion.getEditorContext
import com.intellij.codeInsight.navigation.activateFileWithPsiElement
import com.intellij.ide.IdeView
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystemMarker
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.injected.InjectedCaret
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil

internal class TextEditorPsiDataRule : UiDataRule {
  override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
    val editor = snapshot[CommonDataKeys.EDITOR]
    if (editor !is EditorEx || editor.isDisposed()) return

    val editorKind = editor.editorKind
    if (editorKind == EditorKind.PREVIEW || editorKind == EditorKind.CONSOLE) return

    val caret = snapshot[CommonDataKeys.CARET] ?: editor.caretModel.primaryCaret.also {
      sink[CommonDataKeys.CARET] = it
    }

    val file = editor.virtualFile ?: return
    val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    sink[CommonDataKeys.HOST_EDITOR] = hostEditor
    sink[LangDataKeys.IDE_VIEW] = getIdeView(editor, file)

    val project = editor.project ?: return
    // regular lazy keys
    sink.lazy(CommonDataKeys.PSI_FILE) {
      getPsiFile(editor, file)
    }
    sink.lazy(CommonDataKeys.PSI_ELEMENT) {
      getPsiElementIn(editor, caret, file)
    }
    sink.lazy(PlatformCoreDataKeys.MODULE) {
      ModuleUtilCore.findModuleForFile(file, project)
    }
    sink.lazy(CommonDataKeys.LANGUAGE) {
      val psiFile = getPsiFile(editor, file) ?: return@lazy null
      getLanguageAtCurrentPositionInEditor(caret, psiFile)
    }
    sink.lazy(LangDataKeys.CONTEXT_LANGUAGES) {
      val set = LinkedHashSet<Language?>(4)
      ContainerUtil.addIfNotNull(set, getInjectedLanguage(project, editor, caret, file))
      val hostFile = getPsiFile(editor, file)
      if (hostFile != null) {
        ContainerUtil.addIfNotNull(set, getLanguageAtCurrentPositionInEditor(caret, hostFile))
        ContainerUtil.addIfNotNull(set, hostFile.viewProvider.baseLanguage)
      }
      set.toArray(Language.EMPTY_ARRAY)
    }
    // injected lazy keys
    sink.lazy(InjectedDataKeys.EDITOR) {
      getInjectedEditor(project, editor, caret, file)
    }
    sink.lazy(InjectedDataKeys.CARET) {
      val editor = getInjectedEditor(project, editor, caret, file) ?: return@lazy null
      getInjectedCaret(editor, caret)
    }
    sink.lazy(InjectedDataKeys.VIRTUAL_FILE) {
      val editor = getInjectedEditor(project, editor, caret, file) ?: return@lazy null
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.virtualFile
    }
    sink.lazy(InjectedDataKeys.PSI_FILE) {
      val editor = getInjectedEditor(project, editor, caret, file) ?: return@lazy null
      PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    }
    sink.lazy(InjectedDataKeys.PSI_ELEMENT) {
      val editor = getInjectedEditor(project, editor, caret, file) ?: return@lazy null
      getPsiElementIn(editor, getInjectedCaret(editor, caret), file)
    }
    sink.lazy(InjectedDataKeys.LANGUAGE) {
      getInjectedLanguage(project, editor, caret, file)
    }
  }
}

private fun getIdeView(e: Editor, file: VirtualFile): IdeView? {
  val project = e.project
  if (project == null) return null
  val fs = file.fileSystem
  val nonPhysical = fs is NonPhysicalFileSystem || fs is TempFileSystemMarker
  if (nonPhysical && !ApplicationManager.getApplication().isUnitTestMode()) return null
  return object : IdeView {
    override fun selectElement(element: PsiElement) {
      activateFileWithPsiElement(element)
    }

    override fun getDirectories(): Array<PsiDirectory> {
      val psiDirectory = getOrChooseDirectory()
      return if (psiDirectory == null) PsiDirectory.EMPTY_ARRAY else arrayOf(psiDirectory)
    }

    override fun getOrChooseDirectory(): PsiDirectory? {
      val parent = if (!file.isValid()) null else file.parent
      return if (parent == null) null else PsiManager.getInstance(project).findDirectory(parent)
    }
  }
}

private fun getInjectedLanguage(project: Project, e: EditorEx, caret: Caret, file: VirtualFile): Language? {
  val editor = getInjectedEditor(project, e, caret, file) ?: return null
  val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument()) ?: return null
  val injectedCaret = getInjectedCaret(editor, caret)
  return getLanguageAtCurrentPositionInEditor(injectedCaret, psiFile)
}

private fun getInjectedEditor(project: Project, editor: EditorEx, caret: Caret, file: VirtualFile): EditorWindow? {
  if (editor.isDisposed || !file.isValid) return null
  if (editor is EditorWindow) return editor
  if (PsiDocumentManager.getInstance(project).isCommitted(editor.document) &&
      InjectedLanguageManager.getInstance(project).mightHaveInjectedFragmentAtOffset(editor.document, caret.offset)) {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(editor, caret, getPsiFile(editor, file)) as? EditorWindow
  }
  return null
}

private fun getInjectedCaret(editor: EditorWindow, hostCaret: Caret): InjectedCaret {
  if (hostCaret is InjectedCaret) {
    return hostCaret
  }
  for (caret in editor.caretModel.allCarets) {
    if ((caret as InjectedCaret).delegate === hostCaret) {
      return caret
    }
  }
  throw IllegalArgumentException("Cannot find injected caret corresponding to $hostCaret")
}

private fun getLanguageAtCurrentPositionInEditor(caret: Caret, psiFile: PsiFile): Language? {
  val caretOffset = caret.offset
  val mostProbablyCorrectLanguageOffset =
    if (caretOffset == caret.selectionStart || caretOffset == caret.selectionEnd) caret.selectionStart
    else caretOffset
  if (caret.hasSelection()) {
    return getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset, caret.selectionEnd)
  }
  return PsiUtilCore.getLanguageAtOffset(psiFile, mostProbablyCorrectLanguageOffset)
}

private tailrec fun getLanguageAtOffset(psiFile: PsiFile, mostProbablyCorrectLanguageOffset: Int, end: Int): Language? {
  val elt = psiFile.findElementAt(mostProbablyCorrectLanguageOffset) ?: return psiFile.language
  if (elt is PsiWhiteSpace) {
    val incremented = elt.textRange.endOffset + 1
    if (incremented <= end) {
      return getLanguageAtOffset(psiFile, incremented, end)
    }
  }
  return PsiUtilCore.findLanguageFromElement(elt)
}

private fun getPsiElementIn(editor: Editor, caret: Caret, file: VirtualFile): PsiElement? {
  if (getPsiFile(editor, file) == null) return null
  try {
    val util = TargetElementUtil.getInstance()
    return util.findTargetElement(editor, util.getReferenceSearchFlags(), caret.offset)
  }
  catch (_: IndexNotReadyException) {
    return null
  }
}

private fun getPsiFile(editor: Editor, file: VirtualFile): PsiFile? {
  if (editor.isDisposed || !file.isValid) return null
  val project = editor.project ?: return null
  val context = getEditorContext(editor, project)
  val psiFile = PsiManager.getInstance(project).findFile(file, context)
  return if (psiFile != null && psiFile.isValid()) psiFile else null
}