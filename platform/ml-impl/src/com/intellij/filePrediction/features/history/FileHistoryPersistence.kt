// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.filePrediction.features.history

import com.intellij.internal.ml.ngram.NGramIncrementalModelRunner
import com.intellij.internal.ml.ngram.NGramModelSerializer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.PathUtil
import com.intellij.util.io.delete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

object FileHistoryPersistence {
  private val LOG: Logger = Logger.getInstance(FileHistoryPersistence::class.java)

  private const val NGRAM_FILE_NAME_SUFFIX = "-ngram"

  fun deleteLegacyFile(project: Project) {
    try {
      if (!project.isDisposed) {
        val path: Path? = getPathToStorage(project, PathManager.DEFAULT_EXT)
        if (path != null && path.exists()) {
          path.delete()
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize file sequence ngrams", e)
    }
  }

  fun saveNGramsAsync(project: Project, runner: NGramIncrementalModelRunner) {
    val path = getPathToStorage(project, NGRAM_FILE_NAME_SUFFIX) ?: return
    (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().launch {
      try {
        Files.createDirectories(path.parent)
        NGramModelSerializer.saveNGrams(path, runner)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.warn("Cannot serialize file sequence ngrams", e)
      }
    }
  }

  fun loadNGrams(project: Project, nGramLength: Int): NGramIncrementalModelRunner {
    try {
      if (!project.isDisposed) {
        val path: Path? = getPathToStorage(project, NGRAM_FILE_NAME_SUFFIX)
        return NGramModelSerializer.loadNGrams(path, nGramLength)
      }
    }
    catch (e: Exception) {
      LOG.warn("Cannot deserialize file sequence ngrams", e)
    }
    return NGramModelSerializer.loadNGrams(null, nGramLength)
  }

  private fun getPathToStorage(project: Project, suffix: String): Path? {
    val url = project.presentableUrl ?: return null
    val projectPath = Paths.get(VirtualFileManager.extractPath(url))
    val dirName = projectPath.fileName?.toString() ?: projectPath.toString().substring(0, 1)
    val storageName = PathUtil.suggestFileName(dirName + Integer.toHexString(projectPath.toString().hashCode()))
    return Path.of(PathManager.getSystemPath(), "fileHistory", "${storageName}${suffix}")
  }
}