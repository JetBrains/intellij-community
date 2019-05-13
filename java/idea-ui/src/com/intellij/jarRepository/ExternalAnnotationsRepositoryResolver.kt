// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
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
  }

  override fun resolve(project: Project, library: Library, mavenId: String?): Library {
    var mavenLibDescriptor = extractDescriptor(mavenId, library, false) ?: return library
    var roots = JarRepositoryManager
      .loadDependenciesSync(project,
                             mavenLibDescriptor,
                             setOf(ArtifactKind.ANNOTATIONS),
                             null,
                             null)
      as MutableList<OrderRoot>?

    if (roots == null || roots.isEmpty()) {
      mavenLibDescriptor = extractDescriptor(mavenId, library, true) ?: return library
      roots = JarRepositoryManager
        .loadDependenciesSync(project,
                              mavenLibDescriptor,
                              setOf(ArtifactKind.ANNOTATIONS),
                              null,
                              null)
        as MutableList<OrderRoot>?
    }

    invokeAndWaitIfNeeded {
      updateLibrary(roots, mavenLibDescriptor, library)
    }

    return library
  }

  override fun resolve(project: Project, library: Library, location: AnnotationsLocation): Library {
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
                ?: return library

    invokeAndWaitIfNeeded { updateLibrary(roots, descriptor, library) }
    return library
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
        ApplicationManager.getApplication().invokeLater {
          updateLibrary(roots, mavenLibDescriptor, library)
          promise.setResult(library)
        }
        promise
      }
  }

  private fun updateLibrary(roots: MutableList<OrderRoot>?,
                    mavenLibDescriptor: JpsMavenRepositoryLibraryDescriptor,
                    library: Library) {
    if (roots == null || roots.isEmpty()) {
      LOG.info("No annotations found for [$mavenLibDescriptor]")
    } else {
      runWriteAction {
        LOG.debug("Found ${roots.size} external annotations for ${library.name}")
        val editor = ExistingLibraryEditor(library, null)
        val type = AnnotationOrderRootType.getInstance()
        editor.getUrls(type).forEach { editor.removeRoot(it, type) }
        editor.addRoots(roots)
        editor.commit()
      }
    }
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
