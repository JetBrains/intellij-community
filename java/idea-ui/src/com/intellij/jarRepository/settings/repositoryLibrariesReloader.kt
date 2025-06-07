// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository.settings

import com.intellij.ide.JavaUiBundle
import com.intellij.jarRepository.JarRepositoryManager
import com.intellij.jarRepository.collectLibraries
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import org.jetbrains.concurrency.collectResults
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

internal fun reloadAllRepositoryLibraries(project: Project) {
  val libraries = collectLibraries(project) { (it as? LibraryEx)?.properties is RepositoryLibraryProperties }
  libraries
    .asSequence()
    .filterIsInstance<LibraryEx>()
    .map { RepositoryUtils.reloadDependencies(project, it).then {
      // NOP, to make collectResults accept the correct type (Unit)
    } }
    .toList()
    .collectResults()
    .onSuccess {
      Notifications.Bus.notify(JarRepositoryManager.getNotificationGroup().createNotification(
        JavaUiBundle.message("notification.title.repository.library.synchronization"),
        JavaUiBundle.message("notification.content.libraries.reloaded", it.size),
        NotificationType.INFORMATION), project)
    }
}