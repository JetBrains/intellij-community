// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.codeInsight.externalAnnotation.location.AnnotationsLocation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.ui.OrderRoot
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
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

    ApplicationManager.getApplication().invokeAndWait {
      updateLibrary(roots, library)
    }

    return !roots.isNullOrEmpty()
  }

  override fun resolve(project: Project, library: Library, location: AnnotationsLocation): Boolean {
    val roots = resolveNewOrderRoots(location, project)

    if (roots.isNullOrEmpty()) {
      return false
    }

    ApplicationManager.getApplication().invokeAndWait { updateLibrary(roots, library) }
    return true
  }

  override fun resolve(project: Project, library: Library, location: AnnotationsLocation, diff: MutableEntityStorage): Boolean {
    if (library !is LibraryBridge || library.isDisposed) {
      return true
    }

    val newRoots = resolveNewOrderRoots(location, project)

    if (newRoots.isNullOrEmpty()) {
      return false
    }

    LOG.debug("Found ${newRoots.size} external annotations for ${library.name}")

    val libraryEntity: LibraryEntity = diff.findLibraryEntity(library) ?: return true
    val vfUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val newUrls = newRoots.map { it.file.toVirtualFileUrl(vfUrlManager) }.toHashSet()
    val toRemove = mutableListOf<LibraryRoot>()
    val annotationsRootType = LibraryRootTypeId(AnnotationOrderRootType.ANNOTATIONS_ID)

    libraryEntity.roots
      .filter { it.type == annotationsRootType }
      .forEach {
        if (it.url !in newUrls) {
          toRemove.add(it)
        }
        else {
          newUrls.remove(it.url)
        }
      }

    if (!toRemove.isEmpty() || !newUrls.isEmpty()) {
      diff.modifyEntity(libraryEntity) {
        roots.removeAll(toRemove)
        roots.addAll(newUrls.map { LibraryRoot(it, annotationsRootType) })
      }
    }
    return true
  }

  private fun resolveNewOrderRoots(location: AnnotationsLocation,
                         project: Project): List<OrderRoot>? {
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

    val newRoots = JarRepositoryManager
      .loadDependenciesSync(project, descriptor, setOf(ArtifactKind.ANNOTATIONS), repos, null)
    if (newRoots.isNullOrEmpty()) {
      LOG.info("No annotations found for [$descriptor]")
    }
    return newRoots
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
          updateLibrary(roots, library)
          promise.setResult(library)
        }
        promise
      }
  }

  @RequiresEdt
  private fun updateLibrary(roots: List<OrderRoot>?,
                            library: Library) {
    if (library !is LibraryEx || library.isDisposed) return
    if (!roots.isNullOrEmpty()) {
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
        WriteAction.run<Exception> {
          editor.commit()
        }
      } else {
        Disposer.dispose(editor)
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
