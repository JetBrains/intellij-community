// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository

import com.intellij.codeInsight.ExternalAnnotationsArtifactsResolver
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.ui.configuration.libraryEditor.ExistingLibraryEditor
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.util.concurrent.TimeUnit

/**
 * Resolve external annotations from Maven repositories.
 * Delegates actual resolution to [JarRepositoryManager]
 */
class ExternalAnnotationsRepositoryResolver : ExternalAnnotationsArtifactsResolver {
  companion object {
    val LOG = Logger.getInstance(ExternalAnnotationsRepositoryResolver::class.java)
  }

  override fun resolveSync(project: Project, library: Library, mavenId: String?) {
    resolveAsync(project, library, mavenId).blockingGet(1, TimeUnit.MINUTES)
  }

  override fun resolveAsync(project: Project, library: Library, mavenId: String?): Promise<Library> {
    var mavenLibDescriptor: JpsMavenRepositoryLibraryDescriptor? = null
    if (mavenId != null) {
      mavenLibDescriptor = JpsMavenRepositoryLibraryDescriptor(mavenId)
    } else if (library is LibraryEx) {
      val properties = library.properties
      if (properties is RepositoryLibraryProperties) {
        mavenLibDescriptor = JpsMavenRepositoryLibraryDescriptor(properties.groupId,
                                                                 properties.artifactId,
                                                                 properties.version)
      }
    }

    if (mavenLibDescriptor == null) {
      return Promise.resolve(library)
    }

    return JarRepositoryManager.loadDependenciesAsync(project,
                                               mavenLibDescriptor,
                                               setOf(ArtifactKind.ANNOTATIONS),
                                               null,
                                               null)
      .thenAsync { roots ->
        val promise = AsyncPromise<Library>()
        ApplicationManager.getApplication().invokeLater {
          if (roots == null || roots.isEmpty()) {
            LOG.info("No annotations found for [$mavenLibDescriptor]")
          } else {
            runWriteAction {
              LOG.debug("Found ${roots.size} external annotations for ${library.name}")
              val editor = ExistingLibraryEditor(library, null)
              editor.addRoots(roots)
              editor.commit()
            }
          }
          promise.setResult(library)
        }
        promise
      }
  }
}
