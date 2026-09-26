// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.Collections.unmodifiableList
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class BookmarksListProviderService(project: Project) {
  private val reference = AtomicReference<List<BookmarksListProvider>>()

  init {
    BookmarksListProvider.EP.addChangeListener(project, { reference.set(null) }, project)
  }

  companion object {
    fun findProvider(project: Project, predicate: (BookmarksListProvider) -> Boolean): BookmarksListProvider? = getProviders(project).find(predicate)
    fun getProviders(project: Project): List<BookmarksListProvider> {
      if (project.isDisposed) return emptyList()
      val reference = project.getService(BookmarksListProviderService::class.java)?.reference ?: return emptyList()
      return reference.get() ?: createProviders(project).also { reference.set(it) }
    }

    private fun createProviders(project: Project): List<BookmarksListProvider> {
      val providers = BookmarksListProvider.EP.getExtensions(project).toMutableList()
      providers.sortByDescending { it.weight }
      return unmodifiableList(providers)
    }
  }
}
