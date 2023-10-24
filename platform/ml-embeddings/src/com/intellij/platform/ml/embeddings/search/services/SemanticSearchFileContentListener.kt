package com.intellij.platform.ml.embeddings.search.services

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class SemanticSearchFileContentListener(val project: Project, cs: CoroutineScope) : AsyncFileListener {
  private val listeners = arrayOf<SemanticSearchFileContentChangeListener<*>>(
    ClassesSemanticSearchFileListener.getInstance(project),
    SymbolsSemanticSearchFileListener.getInstance(project)
  )

  private val reindexRequest = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val reindexQueue = ConcurrentCollectionFactory.createConcurrentSet<VirtualFile>()

  init {
    cs.launch {
      reindexRequest.debounce(1000.milliseconds).collectLatest {
        val iterator = reindexQueue.iterator()
        while (iterator.hasNext()) {
          processEvent(iterator.next())
          iterator.remove()
        }
      }
    }
  }

  @TestOnly
  fun clearEvents() = reindexQueue.clear()

  override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
    if (listeners.none { it.isEnabled }) return null
    events.forEach { it.file?.let { file -> reindexQueue.add(file) } }
    return object : AsyncFileListener.ChangeApplier {
      override fun afterVfsChange() {
        check(reindexRequest.tryEmit(Unit))
      }
    }
  }

  private suspend fun processEvent(file: VirtualFile) {
    for (listener in listeners) {
      if (listener.isEnabled && file.isValid) {
        val entities = readAction {
          when {
            file.isValid -> {
              if (!ProjectFileIndex.getInstance(project).isInSourceContent(file)) return@readAction emptyList()
              val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@readAction emptyList()
              // psiFile may contain outdated structure, so we manually create a new PSI file from a document text:
              val newPsiFile = file.findDocument()?.let { PsiFileFactory.getInstance(project).createFileFromText(it.text, psiFile) }
                               ?: psiFile
              listener.getStorage().traversePsiFile(newPsiFile)
            }
            else -> emptyList()
          }
        }
        listener.inferEntityDiff(file, entities)
      }
      else {
        listener.inferEntityDiff(file, emptyList())
      }
    }
  }

  companion object {
    fun getInstance(project: Project) = project.service<SemanticSearchFileContentListener>()
  }
}