// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.util.Disposer
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor

/**
 * Resolve external annotations from Maven repositories.
 * Delegates actual resolution to [JarRepositoryManager]
 */
class ExternalAnnotationsRepositoryResolver : ExternalAnnotationsArtifactsResolver {
  companion object {
    val LOG = Logger.getInstance(ExternalAnnotationsRepositoryResolver::class.java)
    const val COMMIT_BATCH_SIZE = 100
  }

  override fun resolve(project: Project, library: Library, mavenId: String?): Boolean {
    var mavenLibDescriptor = extractDescriptor(mavenId, library, false) ?: return false
    var roots = JarRepositoryManager
      .loadDependenciesSync(project,
                             mavenLibDescriptor,
                             setOf(ArtifactKind.ANNOTATIONS),
                             null,
                             null)
      as MutableList<OrderRoot>?

    if (roots.isNullOrEmpty()) {
      mavenLibDescriptor = extractDescriptor(mavenId, library, true) ?: return false
      roots = JarRepositoryManager
        .loadDependenciesSync(project,
                              mavenLibDescriptor,
                              setOf(ArtifactKind.ANNOTATIONS),
                              null,
                              null)
        as MutableList<OrderRoot>?
    }

    val commitRunnable = getUpdateLibraryRunnable(roots, mavenLibDescriptor, library)
    if (commitRunnable != null) {
      runCommitAction(commitRunnable)
    }

    return !roots.isNullOrEmpty()
  }

  override fun resolve(project: Project, library: Library, location: AnnotationsLocation): Boolean {
    return resolve(project, library, location, ::runCommitAction)
  }


  fun resolve(project: Project, library: Library, location: AnnotationsLocation, consumer: (Runnable) -> Unit): Boolean {
    val descriptor = JpsMavenRepositoryLibraryDescriptor(location.groupId, location.artifactId, location.version, false)
    val repos = if (location.repositoryUrls.isNotEmpty()) {
      location.repositoryUrls.mapIndexed { index, url ->
        val someUniqueId = "id_${url.hashCode()}_$index"
        RemoteRepositoryDescription(someUniqueId, "name", url)
      }
    }
    else {
      null
    }

    val roots = JarRepositoryManager
      .loadDependenciesSync(project, descriptor, setOf(ArtifactKind.ANNOTATIONS), repos, null)

    if (roots.isNullOrEmpty()) {
      return false
    }

    val commitRunnable = getUpdateLibraryRunnable(roots, descriptor, library)
    if (commitRunnable != null) {
      consumer(commitRunnable)
    }

    return true
  }



  override fun resolveBatch(project: Project,
                            librariesWithLocations: MutableMap<Library, MutableCollection<AnnotationsLocation>>) {
    val locationsToSkip: MutableList<AnnotationsLocation> = ArrayList()
    val toCommit: MutableList<Runnable> = ArrayList(COMMIT_BATCH_SIZE)
    librariesWithLocations.forEach { (lib, annotationsLocation) ->
      annotationsLocation.forEach locations@ { location ->
        if (locationsToSkip.contains(location)) return@locations
        if (!resolve(project, lib, location) { toCommit.add(it) }) {
          locationsToSkip.add(location)
        }
        if (toCommit.size == COMMIT_BATCH_SIZE) {
          runCommitActions(toCommit)
          toCommit.clear()
        }
      }
    }
    if (toCommit.isNotEmpty()) {
      runCommitActions(toCommit)
    }
  }

  private fun runCommitActions(toCommit: List<Runnable>) {
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        toCommit.forEach {
          it.run()
        }
      }
    }
  }

  private fun runCommitAction(toCommit: Runnable) {
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        toCommit.run()
      }
    }
  }

  override fun resolveAsync(project: Project, library: Library, mavenId: String?): Promise<Library> {
    val mavenLibDescriptor = extractDescriptor(mavenId, library, false) ?: return resolvedPromise(library)

    return JarRepositoryManager.loadDependenciesAsync(project,
                                                      mavenLibDescriptor,
                                                      setOf(ArtifactKind.ANNOTATIONS),
                                                      null,
                                                      null)
      .thenAsync { roots ->
        val resolvedRoots = resolvedPromise(roots)
        if (roots?.isEmpty() == false) {
          resolvedRoots
        }
        else {
          val patchedDescriptor = extractDescriptor(mavenId, library, true) ?: return@thenAsync resolvedRoots
          JarRepositoryManager.loadDependenciesAsync(project,
                                                     patchedDescriptor,
                                                     setOf(ArtifactKind.ANNOTATIONS),
                                                     null,
                                                     null)
        }
      }.thenAsync { roots ->
        val promise = AsyncPromise<Library>()
        val commitAction = getUpdateLibraryRunnable(roots, mavenLibDescriptor, library)
        if (commitAction != null) {
          ApplicationManager.getApplication().invokeLater {
            runCommitAction(commitAction)
            promise.setResult(library)
          }
        } else {
          promise.setResult(library)
        }
        promise
      }
  }

  private fun getUpdateLibraryRunnable(roots: MutableList<OrderRoot>?,
                                       mavenLibDescriptor: JpsMavenRepositoryLibraryDescriptor,
                                       library: Library): Runnable? {
    if (library !is LibraryEx || library.isDisposed) return null
    if (roots.isNullOrEmpty()) {
      LOG.info("No annotations found for [$mavenLibDescriptor]")
    } else {
        LOG.debug("Found ${roots.size} external annotations for ${library.name}")
        val editor = ExistingLibraryEditor(library, null)
        val type = AnnotationOrderRootType.getInstance()
        val newUrls = roots.map { it.file.url }.toHashSet()
        editor.getUrls(type).forEach {
          if (!newUrls.contains(it)) {
            editor.removeRoot(it, type)
          } else {
            newUrls.remove(it)
          }
        }
        if (!newUrls.isEmpty()) {
          editor.addRoots(roots.filter { newUrls.contains(it.file.url) })
          return Runnable { editor.commit() }
        } else {
          Disposer.dispose(editor)
        }
    }
    return null
  }

  private fun extractDescriptor(mavenId: String?,
                                library: Library,
                                patched: Boolean): JpsMavenRepositoryLibraryDescriptor? = when {
    mavenId != null -> JpsMavenRepositoryLibraryDescriptor(
      if (patched) patchArtifactId(mavenId) else mavenId,
      false, emptyList()
    )
    library is LibraryEx  -> (library.properties as? RepositoryLibraryProperties)
      ?.run { JpsMavenRepositoryLibraryDescriptor(groupId,
                                                   if (patched) "$artifactId-annotations" else artifactId, version) }
    else -> null
  }

  private fun patchArtifactId(mavenId: String): String {
    val components = mavenId.split(':', limit = 3)
    if (components.size < 3) {
      return mavenId
    }
    return "${components[0]}:${components[1]}-annotations:${components[2]}"
  }

}
