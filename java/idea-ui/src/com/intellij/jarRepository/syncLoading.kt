// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.jarRepository.JarRepositoryManager.loadArtifactForDependenciesAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.registry.Registry
import org.eclipse.aether.artifact.Artifact
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance(RepositoryLibrarySynchronizer::class.java)

internal class LoadResult(val artifacts: Collection<Artifact>?,
                          val library: Library?) {
  companion object {
    val POISON = LoadResult(null, null)
  }
}

internal fun loadDependenciesSync(project: Project) {
  val toSync = RepositoryLibrarySynchronizer.collectLibrariesToSync(project)
  if (toSync.size == 0) return
  val queue = ArrayBlockingQueue<LoadResult>(toSync.size)
  val submitted = submitLoadJobs(project, toSync, queue)
  LOG.info("Submitted $submitted jobs for downloading maven dependencies")

  val timeout = Registry.intValue("load.maven.dependencies.timeout", 120)

  repeat(submitted) {
    val result = queue.poll(timeout.toLong(), TimeUnit.MINUTES)
    if (result == LoadResult.POISON) {
      return
    }
    if (result == null) {
      LOG.error("Cant resolve maven dependencies within $timeout minutes")
      return
    }
    val artifacts = result.artifacts
    if (artifacts != null && !artifacts.isEmpty()) {
      LOG.info("Creating roots started for - " + result.library?.name)
      JarRepositoryManager.copyAndRefreshFiles(artifacts, RepositoryUtils.getStorageRoot(result.library, project))
      LOG.info("Create roots finished for - " + result.library?.name)
    }
  }
}

internal fun submitLoadJobs(project: Project, toSync: Collection<Library>, queue: BlockingQueue<LoadResult>): Int {
  var submitted = 0

  for (library in toSync.filter(LibraryTableImplUtil::isValidLibrary)) {
    val properties = (library as LibraryEx).properties
    if (properties is RepositoryLibraryProperties) {
      val descriptor = properties.repositoryLibraryDescriptor
      val promise = loadArtifactForDependenciesAsync(project, descriptor, EnumSet.of(ArtifactKind.ARTIFACT), null)
      promise.onProcessed { artifacts: Collection<Artifact>? ->
        try {
          queue.put(LoadResult(artifacts, library))
        }
        catch (e: InterruptedException) {
          queue.clear()
          queue.offer(LoadResult.POISON)
          LOG.error(e)
        }
      }
      submitted++
    }
  }
  return submitted
}
