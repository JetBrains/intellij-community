// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInsight.TestFrameworks
import com.intellij.execution.TestStateStorage
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.ClassUtil
import com.intellij.util.containers.FactoryMap
import org.jetbrains.uast.*

@Service
class TestFailedLineManager(project: Project) : FileEditorManagerListener {
  private val testStorage = TestStateStorage.getInstance(project)

  private val cache = FactoryMap.create<VirtualFile, MutableMap<String, TestInfo>> { hashMapOf() }

  init {
    project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
  }

  fun getFailedLineState(call: UCallExpression): TestStateStorage.Record? {
    val containingMethod = call.getContainingUMethod() ?: return null
    val callSourcePsi = call.sourcePsi ?: return null
    val file = call.getContainingUFile()?.sourcePsi ?: return null
    val info = getTestInfo(containingMethod) ?: return null
    val document = PsiDocumentManager.getInstance(callSourcePsi.project).getDocument(file) ?: return null
    info.pointer?.element?.let { pointerElem ->
      if (callSourcePsi == pointerElem) {
        info.record.failedLine = document.getLineNumber(callSourcePsi.textOffset) + 1
        return info.record
      }
    }
    if (info.record.failedLine == -1 || StringUtil.isEmpty(info.record.failedMethod)) return null
    if (info.record.failedLine != document.getLineNumber(callSourcePsi.textOffset) + 1) return null
    if (info.record.failedMethod != call.methodName) return null
    info.pointer = SmartPointerManager.createPointer(callSourcePsi)
    return info.record
  }

  private class TestInfo(var record: TestStateStorage.Record) {
    var pointer: SmartPsiElementPointer<PsiElement>? = null
  }

  private fun getTestInfo(method: UMethod): TestInfo? {
    val containingClass = method.getContainingUClass() ?: return null
    val javaClazz = containingClass.javaPsi
    val framework = TestFrameworks.detectFramework(javaClazz) ?: return null
    if (!framework.isTestMethod(method.javaPsi, false)) return null
    val url = "java:test://" + ClassUtil.getJVMClassName(javaClazz) + "/" + method.name
    val state = testStorage.getState(url) ?: return null
    val vFile = method.getContainingUFile()?.sourcePsi?.virtualFile ?: return null
    val infoInFile = cache[vFile] ?: return null
    var info = infoInFile[url]
    if (info == null || state.date != info.record.date) {
      info = TestInfo(state)
      infoInFile[url] = info
    }
    return info
  }

  override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
    cache.remove(file)?.forEach { (s: String, info: TestInfo) -> testStorage.writeState(s, info.record) }
  }
}