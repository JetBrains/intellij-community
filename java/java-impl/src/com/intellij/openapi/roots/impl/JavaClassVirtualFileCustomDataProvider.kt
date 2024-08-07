// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.java.frontback.psi.impl.ClassFileInformation
import com.intellij.java.frontback.psi.impl.ClassFileInformationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileCustomDataProvider
import com.intellij.psi.ClassFileViewProvider
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class JavaClassVirtualFileCustomDataProvider : VirtualFileCustomDataProvider<ClassFileInformation> {
  companion object {
    val LOG = logger<JavaClassVirtualFileCustomDataProvider>()
  }

  override val id: String = "javaClassVirtualFile"

  override val dataType: KType
    get() = typeOf<ClassFileInformation>()

  override fun getValues(project: Project, virtualFile: VirtualFile): Flow<ClassFileInformation> {
    if (virtualFile.fileType != JavaClassFileType.INSTANCE) return emptyFlow()
    val viewProvider = PsiManagerEx.getInstanceEx(project).fileManager.findViewProvider(virtualFile)
    if(viewProvider !is ClassFileViewProvider) return emptyFlow()
    return object : Flow<ClassFileInformation> {
      override suspend fun collect(collector: FlowCollector<ClassFileInformation>) {
        collector.emit(readAction {
          createClassFileInformation(project, virtualFile)
        })
      }
    }
  }

  @RequiresReadLock
  private fun createClassFileInformation(
    project: Project,
    virtualFile: VirtualFile,
  ): ClassFileInformation {
    val fileIndex = FileIndexFacade.getInstance(project)
    val isOutOfLibrary = !fileIndex.isInLibraryClasses(virtualFile) && fileIndex.isInSource(virtualFile)
    return ClassFileInformation(if (isOutOfLibrary) ClassFileInformationType.JAVA_CLASS_FILE_OUTSIDE else ClassFileInformationType.JAVA_CLASS_FILE)
  }
}