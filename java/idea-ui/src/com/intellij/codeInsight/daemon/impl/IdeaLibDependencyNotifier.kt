// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ui.configuration.libraryEditor.NewLibraryEditor
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.JpsProjectLoadingManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.toPath
import org.jetbrains.idea.maven.aether.ArtifactKind
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor
import java.nio.file.Path
import kotlin.io.path.name


/**
 * `IdeaLibDependencyNotifier` checks dependencies for links to JARs located in current IDE installation.
 * Example of those could be `junit` added by older version of IntelliJ Idea.
 *
 * If the library contains only jars from one of the known library, a fix will be suggested.
 * Fix will replace the library with its repository equivalent.
 */
class IdeaLibDependencyNotifier : ProjectActivity {

  override suspend fun execute(project: Project) {
    JpsProjectLoadingManager.getInstance(project).jpsProjectLoaded(Runnable {
      val ideaHome = Path.of(PathManager.getHomePath())

      WorkspaceModel.getInstance(project)
        .currentSnapshot
        .entities(LibraryEntity::class.java)
        .forEach { library -> notifyLibraryIfNeeded(library, ideaHome, project) }
    })
  }
}


private fun notifyLibraryIfNeeded(library: LibraryEntity, ideaHome: Path, project: Project) {
  val (ideaJars, nonIdeaJars) = sortOutJars(library, ideaHome)

  if (ideaJars.isEmpty()) {
    return
  }

  if (nonIdeaJars.isEmpty()) {
    val artifact = detectMavenArtifactByJars(ideaJars)
    if (artifact == null) {
      notifyLibrary(library, JavaUiBundle.message("library.depends.on.ide.message.replacement.not.found"), project)
    }
    else {
      notifyLibrary(library, JavaUiBundle.message("library.depends.on.ide.message.can.be.replaced", artifact.mavenId), project,
                    ConvertToRepositoryLibraryAction(project, library, artifact))
    }
  }
  else {
    notifyLibrary(library, JavaUiBundle.message("library.depends.on.ide.message.jar.mixture"), project)
  }
}

private fun sortOutJars(library: LibraryEntity, ideaHome: Path): Pair<List<Path>, List<Path>> {
  val ideaJars = ArrayList<Path>()
  val nonIdeaJars = ArrayList<Path>()

  library.roots
    .filter { it.type == LibraryRootTypeId.COMPILED }
    .map { root -> root.url.toPath() }
    .forEach { path ->
      if (path.startsWith(ideaHome)) {
        ideaJars.add(path)
      }
      else {
        nonIdeaJars.add(path)
      }
    }

  return Pair(ideaJars, nonIdeaJars)
}

private fun detectMavenArtifactByJars(ideaJars: List<Path>): JpsMavenRepositoryLibraryDescriptor? {
  val jarNames = ideaJars.map { it.name.lowercase() }

  for (lib in knownIdeaLibraries) {
    if (!jarNames.any { it.startsWith(lib.markerJarName) }) {
      continue
    }
    if (jarNames.all { lib.withinAssociates(it) }) {
      return lib.mavenSubstituteId
    }
    return null
  }

  return null
}


private fun notifyLibrary(library: LibraryEntity, details: String, project: Project, fix: NotificationAction? = null) {

  if (fix == null) {
    // Do not notify libraries we don't know to fix automatically.
    // It can be confusing for user. TODO: investigate more cases when auto fix is available
    // TODO: Or at least narrow search to exclude non-jar libraries.
    return
  }

  val notification = Notification(
    "Legacy Library",
    JavaUiBundle.message("library.depends.on.ide.title"),
    JavaUiBundle.message("library.depends.on.ide.message", library.name, details),
    NotificationType.WARNING
  )

  if (fix != null) {
    notification.addAction(fix)
  }

  Notifications.Bus.notify(notification, project)
}

private fun ConvertToRepositoryLibraryAction(
  project: Project,
  library: LibraryEntity,
  artifact: JpsMavenRepositoryLibraryDescriptor
) = NotificationAction.createSimpleExpiring(JavaUiBundle.message("library.depends.on.ide.fix.convert.to.repo", library.name)) {

  val newLibraryConfig = JarRepositoryManager.resolveAndDownload(project, artifact, setOf(ArtifactKind.ARTIFACT), null, null)

  WriteAction.run<Nothing> {
    WorkspaceModel.getInstance(project).updateProjectModel("Converting library '${library.name}' to repository type") { builder ->

      val libraryEditor = NewLibraryEditor()
      newLibraryConfig?.addRoots(libraryEditor)

      val newLibrary = LibraryEntity(library.name, library.tableId, emptyList(), library.entitySource) {
        libraryProperties = LibraryPropertiesEntity("repository", library.entitySource) {
          propertiesXmlTag = """<properties maven-id="${artifact.mavenId}" />"""
        }

        val urlManager = VirtualFileUrlManager.getInstance(project)
        libraryEditor.getUrls(OrderRootType.CLASSES)
          .map { urlString -> urlManager.fromUrl(urlString) }
          .map { url -> LibraryRoot(url, LibraryRootTypeId.COMPILED) }
          .forEach { root -> roots.add(root) }
      }

      builder.resolve(library.symbolicId)?.let(builder::removeEntity)
      builder.addEntity(newLibrary)
    }
  }

  project.scheduleSave()
}


private class IdeaInstallationLibrary(
  val markerJarName: String,
  val knownAssociates: List<String>,
  val mavenSubstituteId: JpsMavenRepositoryLibraryDescriptor
) {
  fun withinAssociates(jarName: String): Boolean {
    return knownAssociates.any { jarName.startsWith(it) }
  }
}

private val knownIdeaLibraries = listOf(
  IdeaInstallationLibrary("junit4", listOf("junit", "hamcrest"), JpsMavenRepositoryLibraryDescriptor("junit:junit:4.13.1")),
  IdeaInstallationLibrary("junit-4", listOf("junit", "hamcrest"), JpsMavenRepositoryLibraryDescriptor("junit:junit:4.13.1")),
  IdeaInstallationLibrary("junit.jar", listOf("junit"), JpsMavenRepositoryLibraryDescriptor("junit:junit:3.8.2"))
)
