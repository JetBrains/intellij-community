// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.ide.favoritesTreeView.FavoritesListProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.CommonActionsPanel.Buttons
import java.util.Collections.unmodifiableList
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent

class BookmarksListProviderService(project: Project) {
  private val reference = AtomicReference<List<BookmarksListProvider>>()

  init {
    BookmarksListProvider.EP.addChangeListener(project, { reference.set(null) }, project)
    FavoritesListProvider.EP_NAME.getPoint(project).addChangeListener({ reference.set(null) }, project)
  }

  companion object {
    fun findProvider(project: Project, predicate: (BookmarksListProvider) -> Boolean) = getProviders(project).find(predicate)
    fun getProviders(project: Project): List<BookmarksListProvider> {
      if (project.isDisposed) return emptyList()
      val reference = project.getService(BookmarksListProviderService::class.java)?.reference ?: return emptyList()
      return reference.get() ?: createProviders(project).also { reference.set(it) }
    }

    private fun createProviders(project: Project): List<BookmarksListProvider> {
      val providers = BookmarksListProvider.EP.getExtensions(project).toMutableList()
      FavoritesListProvider.EP_NAME.getExtensionList(project).mapTo(providers) { Adapter(project, it) }
      providers.sortByDescending { it.weight }
      return unmodifiableList(providers)
    }
  }

  private class Adapter(private val project: Project, private val provider: FavoritesListProvider) : BookmarksListProvider {
    override fun getProject() = project
    override fun getWeight() = provider.weight
    override fun createNode() = provider.createFavoriteListNode(project)

    override fun getEditActionText() = provider.getCustomName(Buttons.EDIT)
    override fun canEdit(selection: Any) = provider.willHandle(Buttons.EDIT, project, setOf(selection))
    override fun performEdit(selection: Any, parent: JComponent) = provider.handle(Buttons.EDIT, project, setOf(selection), parent)

    override fun getDeleteActionText() = provider.getCustomName(Buttons.REMOVE)
    override fun canDelete(selection: List<*>) = provider.willHandle(Buttons.REMOVE, project, selection.toSet())
    override fun performDelete(selection: List<*>, parent: JComponent) = provider.handle(Buttons.REMOVE, project, selection.toSet(), parent)
  }
}
