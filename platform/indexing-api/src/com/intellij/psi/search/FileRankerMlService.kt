// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileRankerMlService {
  enum class CallSource {
    SHOW_USAGES,
    FIND_USAGES
  }

  companion object {
    @JvmStatic
    fun getInstance(): FileRankerMlService? {
      val service = serviceOrNull<FileRankerMlService>()
      if (service != null && service.isEnabled()) {
        return service
      }
      return null
    }
  }

  /**
   * This method is called when the show usages window is closed or the find usages call is finished.
   * It finishes the singular Search Usages session and logs the files' features.
   *
   * @param project instance of the current project
   * @param foundUsageFiles set of files which contain the semantic usage
   * @param callSource enum informing whether Find Usages or Search Usages was called
   */
  fun onSessionFinished(project: Project?, foundUsageFiles: Set<VirtualFile>, callSource: CallSource)

  /**
   * Return the files in the order they should be passed to ForkJoinPool to be processed.
   * If shouldUseOldImplementation() is true, this method will be called, but its result will be ignored.
   *
   * @param queryNames list of all words the user searched for (called Search Usages on)
   * @param queryFiles list of all files from which the user called Search Usages
   * @param candidateFiles list of all files in the scope which contain the syntactic usage
   * @return list of candidate files ordered by processing priority
   */
  fun getFileOrder(queryNames: List<String>, queryFiles: List<VirtualFile>, candidateFiles: List<VirtualFile>): List<VirtualFile>

  /**
   * Return true if the registered implementation should be used. If false, no methods from this implementation will be called.
   */
  fun isEnabled(): Boolean

  /**
   * If true, PsiSearchHelper#processGlobalRequests will process segments of usages sequentially. In this case,
   *    FileRankerMlService#getFileOrder will be called (to allow for feature logging), but the result will be ignored.
   * If false, it will pass the files for processing in one step, in the order provided by FileRankerMlService#getFileOrder.
   */
  fun shouldUseOldImplementation(): Boolean
}
