// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.download.impl.eel

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.download.DownloadableFileDescription
import com.intellij.util.download.eel.EelFileDownloader
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.delete
import com.intellij.util.progress.ConcurrentTasksProgressManager
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

@ApiStatus.Internal
internal class EelFileDownloaderImpl(
  private val myFileDescriptions: MutableList<out DownloadableFileDescription>,
) : EelFileDownloader {

  @Throws(IOException::class)
  override fun download(targetDir: Path): MutableList<Pair<Path, DownloadableFileDescription>> {
    val downloadedFiles = Collections.synchronizedList(mutableListOf<Pair<Path, DownloadableFileDescription>>())
    val existingFiles = Collections.synchronizedList(mutableListOf<Pair<Path, DownloadableFileDescription>>())

    try {
      val progressManager = ConcurrentTasksProgressManager(createIndicator(), myFileDescriptions.size)
      val maxParallelDownloads = Runtime.getRuntime().availableProcessors()
      LOG.debug("Downloading ${myFileDescriptions.size} files using ${maxParallelDownloads} threads")
      val start = System.currentTimeMillis()
      val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("EelFileDownloaderImpl Pool", maxParallelDownloads)
      val results: MutableList<Future<Void>> = mutableListOf()
      val totalSize = AtomicLong()
      myFileDescriptions.forEach {
        results.add(executor.submit(createDownloadTask(progressManager, targetDir, it, existingFiles, downloadedFiles, totalSize)))
      }

      results.forEach { it.getCatching() }
      val duration = System.currentTimeMillis() - start
      LOG.debug("Downloaded ${StringUtil.formatFileSize(totalSize.get())} in ${StringUtil.formatDuration(duration)}(${duration}ms)")

      return (moveToDir(downloadedFiles, targetDir) + existingFiles).toMutableList()
    }
    catch (e: ProcessCanceledException) {
      deleteFiles(downloadedFiles)
      throw e
    }
    catch (e: IOException) {
      deleteFiles(downloadedFiles)
      throw e
    }
  }

  private fun createDownloadTask(progressManager: ConcurrentTasksProgressManager,
                                 targetDir: Path, description: DownloadableFileDescription,
                                 existingFiles: MutableCollection<Pair<Path, DownloadableFileDescription>>,
                                 downloadedFiles: MutableCollection<Pair<Path, DownloadableFileDescription>>,
                                 totalSize: AtomicLong): Callable<Void> {
    return Callable {
      val indicator = progressManager.createSubTaskIndicator(1)
      indicator.checkCanceled()

      val existing = targetDir.resolve(description.getDefaultFileName())
      val url = description.getDownloadUrl()
      if (url.startsWith(LIB_SCHEMA)) {
        val path = Path.of(url.removePrefix(LIB_SCHEMA))
        val file = PathManager.getLibDir().resolve(path)
        existingFiles.add(file to description)
      }
      else if (url.startsWith(LocalFileSystem.PROTOCOL_PREFIX)) {
        val path = Path.of(url.removePrefix(LocalFileSystem.PROTOCOL_PREFIX))
        if (Files.exists(path)) {
          existingFiles.add(path to description)
        }
      }
      else {
        LOG.debug("Downloading `$url` into `$existing`")
        val downloaded: Path
        try {
          downloaded = downloadFile(description, existing, indicator)
        }
        catch (e: IOException) {
          throw IOException(IdeCoreBundle.message("error.file.download.failed", description.getDownloadUrl(),
                                                  e.message), e)
        }
        if (downloaded == existing) {
          existingFiles.add(existing to description)
        }
        else {
          totalSize.addAndGet(Files.size(downloaded))
          downloadedFiles.add(downloaded to description)
        }
      }
      indicator.finished()
      null
    }
  }

  private fun createIndicator(): ProgressIndicator {
    val indicator = ProgressManager.getInstance().getProgressIndicator()?.also { it.setIndeterminate(false) } ?: EmptyProgressIndicator()
    indicator.setText(IdeCoreBundle.message("progress.downloading.0.files.text", myFileDescriptions.size))

    return indicator
  }

  companion object {
    private val LOG = Logger.getInstance(EelFileDownloaderImpl::class.java)
    private const val LIB_SCHEMA = "lib://"

    private fun Future<Void>.getCatching() {
      try {
        this.get()
      }
      catch (_: InterruptedException) {
        throw ProcessCanceledException()
      }
      catch (e: ExecutionException) {
        if (e.cause is IOException) {
          throw (e.cause as IOException)
        }
        if (e.cause is ProcessCanceledException) {
          throw (e.cause as ProcessCanceledException)
        }
        LOG.error(e)
      }
    }

    @Throws(IOException::class)
    fun moveToDir(
      downloadedFiles: List<Pair<Path, DownloadableFileDescription>>,
      targetDir: Path
    ): List<Pair<Path, DownloadableFileDescription>> {
      Files.createDirectories(targetDir)

      return downloadedFiles.map { (fromPath, description) ->
        val fileName = description.generateFileName { name ->
          !Files.exists(targetDir.resolve(name))
        }

        val toPath = targetDir.resolve(fileName)
        Files.move(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING)

        toPath to description
      }
    }

    private fun deleteFiles(pairs: List<Pair<Path, DownloadableFileDescription>>) = pairs.forEach { it.first.delete() }

    @Throws(IOException::class)
    private fun downloadFile(
      description: DownloadableFileDescription,
      existingFile: Path,
      indicator: ProgressIndicator,
    ): Path {
      val presentableUrl = description.getPresentableDownloadUrl()
      indicator.setText(IdeCoreBundle.message("progress.connecting.to.download.file.text", presentableUrl))

      return HttpRequests.request(description.getDownloadUrl()).connect(object : HttpRequests.RequestProcessor<Path> {
        @Throws(IOException::class)
        override fun process(request: HttpRequests.Request): Path {
          val size = request.getConnection().getContentLength()
          if (Files.exists(existingFile) && size.toLong() == Files.size(existingFile)) {
            return existingFile
          }

          indicator.setText(IdeCoreBundle.message("progress.download.file.text", description.getPresentableFileName(), presentableUrl))
          return request.saveToFile(Files.createTempFile("download.", ".tmp"), indicator)
        }
      })
    }
  }
}