// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarHttpDownloader.RelativePathToDownload
import com.intellij.jarRepository.JarHttpDownloader.RemoteRepository
import com.intellij.jarRepository.JarRepositoryAuthenticationDataProvider.AuthenticationData
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.aether.RetryProvider
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.serialization.JpsMavenSettings
import java.nio.file.Path
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Alternative repository libraries downloader, independent of maven code
 * Downloads only files listed in
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class JarHttpDownloaderJps(val project: Project, val coroutineScope: CoroutineScope) {
  companion object {
    private val LOG = Logger.getInstance(JarHttpDownloaderJps::class.java)

    @JvmStatic
    fun enabled(): Boolean = Registry.`is`("jar.http.downloader.enabled")

    @JvmStatic
    fun getInstance(project: Project): JarHttpDownloaderJps = project.service<JarHttpDownloaderJps>()

    private fun collectRelativePathsForJarHttpDownloaderOrLog(library: LibraryEx): CollectResult {
      if (library.getKind() != RepositoryLibraryType.REPOSITORY_LIBRARY_KIND) {
        return CollectResult.Failure("Library '${library.name}' is not a repository library")
      }

      val libraryProperties = library.properties as? RepositoryLibraryProperties
      if (libraryProperties == null) {
        return CollectResult.Failure("Library '${library.name}' has no repository library properties")
      }

      if (!isLibraryHasFixedVersion(libraryProperties)) {
        return CollectResult.Failure("Library '${library.name}' does not have fixed version (version=${libraryProperties.version})")
      }

      val verification = libraryProperties.artifactsVerification.associate { it.url to it }

      // All paths in .idea/libraries are typically relative to $MAVEN_REPOSITORY$
      // that's the case we want to handle

      // a collection of edge cases where the actual maven local repository root can be
      // 1. canonical path (symlinks can be resolved or not resolved)
      // 2. in tests, JarRepositoryManager.localRepositoryPath can be overridden
      val possibleMavenLocalRepositoryRoots = listOfNotNull(
        // could be overridden, like in tests
        JarRepositoryManager.getLocalRepositoryPath().path,

        // always returns a canonical path (symlinks resolved), so can be anything even if it was not overridden
        PathMacroManager.getInstance(ApplicationManager.getApplication()).expandPath(JarRepositoryManager.MAVEN_REPOSITORY_MACRO),

        // in some cases, we may receive a non-canonical path (without symlinks resolved)
        JpsMavenSettings.getMavenRepositoryPath(),
      ).map { Path.of(FileUtil.toSystemDependentName(it)).normalize() }

      val files = OrderRootType.getAllTypes().flatMap { rootType -> library.getUrls(rootType).map { rootType to it } }
        .mapNotNull { (rootType, url) ->
          val urlToPath = VfsUtil.urlToPath(url)
          if (urlToPath == url) {
            return CollectResult.Failure("Library '${library.name}': root '$url' could not be converted to path")
          }

          val independentPath = urlToPath.removeSuffix("!/")

          val path = Path.of(FileUtil.toSystemDependentName(independentPath)).normalize()

          val prefix = possibleMavenLocalRepositoryRoots.firstOrNull { path.startsWith(it) }
          val relativePath = if (prefix != null) {
            path.relativeTo(prefix)
          }
          else {
            // allow existing annotation roots on disk to be excluded from downloading
            // continue
            if (rootType == AnnotationOrderRootType.getInstance() && path.exists()) {
              return@mapNotNull null
            }

            return CollectResult.Failure(
              "Library '${library.name}': root path '$path' does not belong to local maven repository cache " +
              "(${possibleMavenLocalRepositoryRoots.distinct().joinToString(", ") { "'$it'" }}).)")
          }

          val sha256 = if (libraryProperties.isEnableSha256Checksum) {
            val fileUrl = VfsUtil.pathToUrl(path.toString())
            val sha256 = verification[fileUrl]?.sha256sum

            if (sha256 == null && rootType == OrderRootType.CLASSES) {
              error("Library '${library.name}': SHA-256 checksum is not specified for url '$fileUrl' in library '${library.name}'.\n" +
                    "Available checksums:\n" + verification.entries.joinToString("\n") { "  ${it.key} -> ${it.value.sha256sum}" })
            }

            sha256
          }
          else null

          RelativePathToDownload(relativePath = relativePath, expectedSha256 = sha256)
        }
        .distinct()

      if (files.isEmpty()) {
        return CollectResult.Failure("Library '${library.name}': no roots (files) to download")
      }

      return CollectResult.Success(files)
    }

    @TestOnly
    fun whyLibraryCouldNotBeDownloaded(library: LibraryEx): String? {
      val result = collectRelativePathsForJarHttpDownloaderOrLog(library)
      return if (result is CollectResult.Failure) result.reason else null
    }
  }

  private val defaultRetryProvider = RetryProvider.withExponentialBackOff(
    System.getProperty("jar.http.downloader.retry.initial.delay.ms", "1000").toLong(),
    System.getProperty("jar.http.downloader.retry.backoff.limit.ms", "5000").toLong(),
    System.getProperty("jar.http.downloader.retry.max.attempts", "3").toInt(),
  )

  private val NUMBER_OF_DOWNLOAD_THREADS = System.getProperty("jar.http.downloader.threads", "10").toInt()

  @OptIn(ExperimentalCoroutinesApi::class)
  private val limitedDispatcher = Dispatchers.IO.limitedParallelism(NUMBER_OF_DOWNLOAD_THREADS)

  private val filesToRefresh = ConcurrentLinkedDeque<Path>()

  init {
    coroutineScope.launch {
      while (true) {
        // Refresh every 15 seconds in case fsmonitor events were not handled good enough
        delay(15000)

        while (filesToRefresh.isNotEmpty()) {
          val file = filesToRefresh.removeFirst()

          if (LOG.isTraceEnabled) {
            LOG.trace("Refreshing VFS for file '$file'")
          }

          // try to do it as async as possible
          // we do not need anything right now, much later is also alright
          VfsImplUtil.refreshAndFindFileByPath(
            LocalFileSystem.getInstance(),
            FileUtil.toSystemIndependentName(file.pathString)) { virtualFile ->
            if (virtualFile == null) {
              LOG.warn("File '$file' could not be found in VFS after VfsImplUtil.refreshAndFindFileByPath, " +
                       "exists=${file.exists()}")
            }
          }
        }
      }
    }
  }

  /**
   * return null if `library` could not be downloaded by JarHttpDownloader
   */
  fun downloadLibraryFilesAsync(library: LibraryEx): Promise<*>? {
    val relativePaths = when (val result = collectRelativePathsForJarHttpDownloaderOrLog(library)) {
      is CollectResult.Failure -> {
        LOG.debug(result.reason)
        return null
      }
      is CollectResult.Success -> result.files
    }

    LOG.debug("Downloading library '${library.name}'")

    val localRepository = JarRepositoryManager.getLocalRepositoryPath().toPath()
    val remoteRepositories = RemoteRepositoriesConfiguration.getInstance(project).repositories

    // TODO Needs some tests on cancellation, it's not supported well
    //  it should work both way: cancelling promise should cancel downloading
    //  and cancelling coroutineScope should cancel promise (make it fail with CancellationException)

    val promise = object : AsyncPromise<Unit>() {
      // Do not call Logger.error which may cause exception re-throwing in UNIT TESTS and
      // breaking of the following try { catch {} } code
      // Example: a race between executing launch {} code and setting onError on the promise
      // (which disables error logging too)
      override fun shouldLogErrors(): Boolean = false
    }

    coroutineScope.launch {
      val remotes = remoteRepositories
        .map { repository ->
          val authData = obtainAuthenticationData(repository)
          RemoteRepository(repository.url, authData)
        }

      try {
        val downloadedFiles = JarHttpDownloader.downloadLibraryFilesAsync(
          relativePaths = relativePaths,
          localRepository = localRepository,
          remoteRepositories = remotes,
          retry = defaultRetryProvider,
          downloadDispatcher = limitedDispatcher,
        )

        // Force downloaded files to appear in VFS regardless of fsmonitor
        filesToRefresh.addAll(downloadedFiles)

        promise.setResult(Unit)
      }
      catch (e: Throwable) {
        promise.setError(e)
      }
    }

    return promise
  }

  private fun obtainAuthenticationData(repository: RemoteRepositoryDescription): AuthenticationData? {
    for (extension in JarRepositoryAuthenticationDataProvider.KEY.extensionList) {
      val authData = extension.provideAuthenticationData(repository)
      if (authData != null) {
        return AuthenticationData(authData.userName, authData.password)
      }
    }

    return null
  }

  sealed interface CollectResult {
    data class Success(val files: List<RelativePathToDownload>) : CollectResult
    data class Failure(val reason: String) : CollectResult
  }
}
