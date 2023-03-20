// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMember
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.containers.SLRUMap

@Service
class FileStateCache : Disposable {

  private val cache: SLRUMap<SmartPsiElementPointer<PsiFile>, PrivateFileState> = SLRUMap(100, 50)

  companion object {

    @JvmStatic
    fun getInstance(project: Project): FileStateCache = project.getService(FileStateCache::class.java)
  }

  init {
    LowMemoryWatcher.register(Runnable { synchronized(cache) { cache.clear() } }, this)
  }

  internal fun getState(psiFile: PsiFile): FileState? {
    return synchronized(cache) { cache.get(SmartPointerManager.createPointer(psiFile))?.toFileState() }
  }

  internal fun setState(psiFile: PsiFile, snapshot: Snapshot, changes: Map<PsiMember, ScopedMember?>) {
    synchronized(cache) { cache.put(SmartPointerManager.createPointer(psiFile), PrivateFileState.create(snapshot, changes)) }
  }

  internal fun removeState(psiFile: PsiFile) {
    synchronized(cache) { cache.remove(SmartPointerManager.createPointer(psiFile)) }
  }

  private data class PrivateFileState(
    val snapshot: Snapshot,
    val changePointers: Map<SmartPsiElementPointer<PsiMember>, ScopedMember?>
  ) {

    fun toFileState(): FileState {
      val changes: Map<PsiMember, ScopedMember?> = changePointers.asSequence()
        .mapNotNull { (memberPointer, prevMember) -> memberPointer.element?.let { it to prevMember } }
        .toMap()
      return FileState(snapshot, changes)
    }

    companion object {
      fun create(snapshot: Snapshot, changes: Map<PsiMember, ScopedMember?>): PrivateFileState {
        val changePointers: Map<SmartPsiElementPointer<PsiMember>, ScopedMember?> = changes.entries.asSequence()
          .map { (psiMember, prevMember) -> SmartPointerManager.createPointer(psiMember) to prevMember }
          .toMap()
        return PrivateFileState(snapshot, changePointers)
      }
    }
  }

  override fun dispose() {}
}