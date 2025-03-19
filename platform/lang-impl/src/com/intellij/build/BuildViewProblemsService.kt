// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build

import com.intellij.analysis.problemsView.FileProblem
import com.intellij.analysis.problemsView.HighlightingDuplicateProblem
import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartBuildEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BuildViewProblemsService(override val project: Project) : ProblemsProvider {

  override fun dispose() {
    buildIdToFileProblems.clear()
    workingDirToBuildId.clear()
  }

  private val workingDirToBuildId: MutableMap<String, Any> = mutableMapOf()
  private val buildIdToFileProblems: MutableMap<Any, MutableSet<FileBuildProblem>> = mutableMapOf()

  fun listenToBuildView(buildProgressObservable: BuildProgressObservable) {
    val collector = project.service<ProblemsCollector>()

    buildProgressObservable.addListener(BuildProgressListener { buildId, event ->
      if (event is FileMessageEvent &&
          event.kind == MessageEvent.Kind.ERROR) {
        val virtualFile = VirtualFileManager.getInstance().findFileByNioPath(event.filePosition.file.toPath()) ?: return@BuildProgressListener
        val problem = FileBuildProblem(event, virtualFile, this)

        val problems = buildIdToFileProblems.getOrPut(buildId) { HashSet() }
        if (problems.add(problem)) {
          collector.problemAppeared(problem)
        } else {
          collector.problemUpdated(problem)
        }
      }

      if (event is StartBuildEvent) {
        workingDirToBuildId
          .put(event.buildDescriptor.workingDir, buildId)
          ?.let { oldBuildId ->
            buildIdToFileProblems[oldBuildId]?.forEach { collector.problemDisappeared(it) }
            buildIdToFileProblems.remove(oldBuildId)
          }
      }

    }, this)
  }

  class BuildProblemsProvider(override val project: Project) : ProblemsProvider

  class FileBuildProblem(val event: FileMessageEvent,
                         val virtualFile: VirtualFile,
                         val problemsProvider: ProblemsProvider) : FileProblem, HighlightingDuplicateProblem {
    override val description: String?
      get() = event.description
    override val file: VirtualFile
      get() = virtualFile
    override val line: Int
      get() = event.filePosition.startLine
    override val column: Int
      get() = event.filePosition.startColumn
    override val provider: ProblemsProvider
      get() = problemsProvider
    override val text: String
      get() = event.message

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as FileBuildProblem

      if (event != other.event) return false
      if (virtualFile != other.virtualFile) return false
      if (problemsProvider != other.problemsProvider) return false

      return true
    }

    override fun hashCode(): Int {
      var result = event.hashCode()
      result = 31 * result + virtualFile.hashCode()
      result = 31 * result + problemsProvider.hashCode()
      return result
    }
  }
}