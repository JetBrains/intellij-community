// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jarRepository.settings

import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.RepositoryLibrarySynchronizer
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.concurrency.collectResults
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

fun reloadAllRepositoryLibraries(project: Project) {
  val libraries = RepositoryLibrarySynchronizer.collectLibraries(project) {
    (it as? LibraryEx)?.properties is RepositoryLibraryProperties
  }.filterIsInstance<LibraryEx>()
  libraries
    .map { RepositoryUtils.reloadDependencies(project, it) }
    .collectResults()
    .onSuccess {
      Notifications.Bus.notify(JarRepositoryManager.GROUP.createNotification(
        JavaUiBundle.message("notification.title.repository.library.synchronization"),
        JavaUiBundle.message("notification.content.libraries.reloaded", it.size),
        NotificationType.INFORMATION), project)
    }

}